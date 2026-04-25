package us.beary.netlens.feature.history

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
import us.beary.netlens.core.data.repository.CombinedHistoryResults
import us.beary.netlens.core.data.repository.HistoryRepository
import us.beary.netlens.feature.history.model.HistoryItem
import us.beary.netlens.feature.history.model.HistoryUiState
import us.beary.netlens.feature.history.model.ToolFilter
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

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
            )
        }

        return items.sortedByDescending { it.timestamp }.take(100)
    }
}
