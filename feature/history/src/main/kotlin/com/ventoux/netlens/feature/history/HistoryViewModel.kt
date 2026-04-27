package com.ventoux.netlens.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ventoux.netlens.core.data.repository.CombinedHistoryResults
import com.ventoux.netlens.core.data.repository.HistoryRepository
import com.ventoux.netlens.feature.history.model.HistoryDetailState
import com.ventoux.netlens.feature.history.model.HistoryItem
import com.ventoux.netlens.feature.history.model.HistoryUiState
import com.ventoux.netlens.feature.history.model.ToolFilter
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private val _detailState = MutableStateFlow<HistoryDetailState?>(null)
    val detailState: StateFlow<HistoryDetailState?> = _detailState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadHistory()
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        loadHistory()
    }

    fun onFilterSelected(filter: ToolFilter) {
        _state.update { it.copy(selectedFilter = filter) }
        reapplyFilter()
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }

    fun selectEntry(item: HistoryItem) {
        _detailState.value = HistoryDetailState.Loading
        viewModelScope.launch {
            val data = historyRepository.getEntry(item.toolFilter.name, item.id)
            _detailState.value = if (data != null) {
                HistoryDetailState.Loaded(item, data)
            } else {
                HistoryDetailState.Error("Entry no longer exists")
            }
        }
    }

    fun dismissDetail() {
        _detailState.value = null
    }

    private var allItems: List<HistoryItem> = emptyList()

    private fun loadHistory() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        val query = _state.value.searchQuery.trim()
        val source = if (query.isBlank()) {
            historyRepository.allRecent()
        } else {
            historyRepository.searchAll(query)
        }

        loadJob = source
            .onEach { results ->
                allItems = mapToItems(results)
                val filtered = applyFilter(allItems, _state.value.selectedFilter)
                _state.update { it.copy(items = filtered, isLoading = false) }
            }
            .catch { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load history") }
            }
            .launchIn(viewModelScope)
    }

    private fun reapplyFilter() {
        val filtered = applyFilter(allItems, _state.value.selectedFilter)
        _state.update { it.copy(items = filtered) }
    }

    private fun applyFilter(items: List<HistoryItem>, filter: ToolFilter): List<HistoryItem> {
        if (filter == ToolFilter.All) return items
        return items.filter { it.toolFilter == filter }
    }

    private fun mapToItems(results: CombinedHistoryResults): List<HistoryItem> {
        val items = mutableListOf<HistoryItem>()

        results.pings.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "Ping",
                primaryLabel = entry.host,
                secondarySummary = "avg %.1fms, %d/%d recv".format(entry.avgMs, entry.receivedCount, entry.sentCount),
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.Ping,
                toolRoute = "ping",
            )
        }

        results.lanScans.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "LAN Scan",
                primaryLabel = entry.subnet ?: "Unknown",
                secondarySummary = "${entry.deviceCount} devices",
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.LanScan,
                toolRoute = "lanscan",
            )
        }

        results.portScans.mapTo(items) { entry ->
            val openCount = entry.openPorts
                .takeIf { it != "[]" }
                ?.removeSurrounding("[", "]")
                ?.split(",")
                ?.size ?: 0
            HistoryItem(
                id = entry.id,
                toolName = "Port Scan",
                primaryLabel = entry.host,
                secondarySummary = "$openCount open / ${entry.totalScanned} scanned",
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.PortScan,
                toolRoute = "portscan",
            )
        }

        results.dnsLookups.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "DNS",
                primaryLabel = entry.query,
                secondarySummary = entry.recordType,
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.Dns,
                toolRoute = "dns",
            )
        }

        results.whoisLookups.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "WHOIS",
                primaryLabel = entry.query,
                secondarySummary = "${entry.rawResponse.length} chars",
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.Whois,
                toolRoute = "whois",
            )
        }

        results.ipInfoLookups.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "IP Info",
                primaryLabel = entry.ip,
                secondarySummary = listOfNotNull(entry.city, entry.countryCode).joinToString(", "),
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.IpInfo,
                toolRoute = "ipinfo",
            )
        }

        results.traceroutes.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "Traceroute",
                primaryLabel = entry.host,
                secondarySummary = "${entry.hopCount} hops",
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.Traceroute,
                toolRoute = "traceroute",
            )
        }

        results.tlsInspections.mapTo(items) { entry ->
            val validity = if (entry.isValid) "Valid" else "Expired"
            HistoryItem(
                id = entry.id,
                toolName = "TLS",
                primaryLabel = entry.host,
                secondarySummary = "$validity · ${entry.issuer}",
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.Tls,
                toolRoute = "tls",
            )
        }

        results.httpTests.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "HTTP",
                primaryLabel = entry.url,
                secondarySummary = "${entry.method} ${entry.statusCode} · ${entry.durationMs}ms",
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.HttpTester,
                toolRoute = "httptester",
            )
        }

        results.mdnsScans.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "mDNS",
                primaryLabel = "${entry.serviceCount} services",
                secondarySummary = "mDNS discovery",
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.Mdns,
                toolRoute = "mdns",
            )
        }

        results.wolSends.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "WoL",
                primaryLabel = entry.label ?: entry.mac,
                secondarySummary = if (entry.label != null) entry.mac else entry.broadcastIp,
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.Wol,
                toolRoute = "wol",
            )
        }

        return items.sortedByDescending { it.timestamp }.take(100)
    }
}
