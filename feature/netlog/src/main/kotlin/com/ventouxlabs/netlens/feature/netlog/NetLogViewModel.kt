package com.ventouxlabs.netlens.feature.netlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ventouxlabs.netlens.core.data.dao.NetworkEventDao
import com.ventouxlabs.netlens.core.data.model.NetworkEvent
import com.ventouxlabs.netlens.core.ui.UiText
import com.ventouxlabs.netlens.feature.netlog.engine.NetworkMonitor
import com.ventouxlabs.netlens.feature.netlog.model.NetLogUiState
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NetLogViewModel @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val networkEventDao: NetworkEventDao,
) : ViewModel() {

    private val _state = MutableStateFlow(NetLogUiState())
    val state: StateFlow<NetLogUiState> = _state.asStateFlow()

    private var monitorJob: Job? = null

    init {
        _state.map { FilterParams(it.selectedEventTypes, it.dateRangeStartMs, it.dateRangeEndMs) }
            .distinctUntilChanged()
            .flatMapLatest { params ->
                networkEventDao.getFiltered(
                    types = params.types.ifEmpty { setOf("__all__") },
                    hasTypeFilter = if (params.types.isEmpty()) 0 else 1,
                    from = params.fromMs,
                    to = params.toMs,
                    limit = MAX_DISPLAYED_EVENTS,
                )
            }
            .onEach { events -> _state.update { it.copy(events = events) } }
            .catch { e -> _state.update { it.copy(error = UiText.of(e.message, R.string.netlog_error_load_failed)) } }
            .launchIn(viewModelScope)
    }

    fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        _state.update { it.copy(isMonitoring = true) }
        monitorJob = networkMonitor.observeNetworkChanges()
            .onEach { event -> networkEventDao.insert(event) }
            .catch { error ->
                _state.update {
                    it.copy(
                        isMonitoring = false,
                        error = UiText.of(error.message, R.string.netlog_error_monitoring_failed),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _state.update { it.copy(isMonitoring = false) }
    }

    fun toggleEventTypeFilter(type: String) {
        _state.update { current ->
            val updated = if (type in current.selectedEventTypes) {
                current.selectedEventTypes - type
            } else {
                current.selectedEventTypes + type
            }
            current.copy(selectedEventTypes = updated)
        }
    }

    fun setDateRange(startMs: Long?, endMs: Long?) {
        _state.update { it.copy(dateRangeStartMs = startMs, dateRangeEndMs = endMs) }
    }

    fun clearFilters() {
        _state.update { it.copy(selectedEventTypes = emptySet(), dateRangeStartMs = null, dateRangeEndMs = null) }
    }

    fun showClearConfirmation() {
        _state.update { it.copy(showClearConfirmation = true) }
    }

    fun hideClearConfirmation() {
        _state.update { it.copy(showClearConfirmation = false) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching {
                networkEventDao.deleteAll()
            }.onFailure { error ->
                _state.update { it.copy(error = UiText.of(error.message, R.string.netlog_error_clear_history_failed)) }
            }
            hideClearConfirmation()
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun buildExportJson(): String {
        val events = _state.value.events
        val array = JSONArray()
        for (event in events) {
            array.put(event.toJson())
        }
        return array.toString(2)
    }

    private fun NetworkEvent.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("eventType", eventType)
        put("transportType", transportType)
        put("networkDetails", networkDetails)
        put("isVpn", isVpn)
    }

    private data class FilterParams(
        val types: Set<String>,
        val fromMs: Long?,
        val toMs: Long?,
    )

    companion object {
        private const val MAX_DISPLAYED_EVENTS = 1000
    }
}
