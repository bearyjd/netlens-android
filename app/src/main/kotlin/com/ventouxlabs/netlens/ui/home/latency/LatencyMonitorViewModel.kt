package com.ventouxlabs.netlens.ui.home.latency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.feature.ping.engine.Pinger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.sqrt

@HiltViewModel
class LatencyMonitorViewModel @Inject constructor(
    private val pinger: Pinger,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LatencyMonitorState())
    val state: StateFlow<LatencyMonitorState> = _state.asStateFlow()

    private var pingJob: Job? = null
    private var currentPingHost: String? = null
    private val _isPaused = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(
                preferences.latencyMonitorEnabled,
                preferences.latencyMonitorHost,
                preferences.latencyAlertThresholdMs,
                _isPaused,
            ) { enabled, host, threshold, paused ->
                PingConfig(enabled, host, threshold, paused)
            }.collect { config ->
                _state.update {
                    it.copy(isEnabled = config.enabled, host = config.host, alertThresholdMs = config.threshold)
                }
                if (config.enabled && !config.paused) {
                    startPinging(config.host)
                } else {
                    stopPinging()
                }
            }
        }
    }

    private fun startPinging(host: String) {
        if (pingJob?.isActive == true && currentPingHost == host) return
        stopPinging()
        currentPingHost = host
        _state.update { it.copy(dataPoints = emptyList(), summary = null, error = null, isRunning = true) }
        pingJob = viewModelScope.launch {
            pinger.pingContinuous(host)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(error = e.message ?: "Ping failed", isRunning = false) }
                }
                .onCompletion { _state.update { it.copy(isRunning = false) } }
                .collect { result ->
                    val point = LatencyDataPoint(
                        timestampMs = System.currentTimeMillis(),
                        latencyMs = if (result.isTimeout) null else result.latencyMs,
                    )
                    _state.update { current ->
                        val newPoints = (current.dataPoints + point)
                            .takeLast(LatencyMonitorState.MAX_DATA_POINTS)
                        current.copy(
                            dataPoints = newPoints,
                            summary = computeSummary(newPoints),
                            error = null,
                        )
                    }
                }
        }
    }

    private fun stopPinging() {
        pingJob?.cancel()
        pingJob = null
        currentPingHost = null
    }

    private fun computeSummary(points: List<LatencyDataPoint>): LatencySummary? {
        if (points.isEmpty()) return null
        val latencies = points.mapNotNull { it.latencyMs }
        if (latencies.isEmpty()) {
            return LatencySummary(0f, 0f, 0f, 0f, 100f, points.size, 0)
        }
        val min = latencies.min()
        val avg = latencies.average().toFloat()
        val max = latencies.max()
        val variance = latencies.map { (it - avg) * (it - avg) }.average()
        val jitter = sqrt(variance).toFloat()
        val loss = ((points.size - latencies.size).toFloat() / points.size) * 100f
        return LatencySummary(min, avg, max, jitter, loss, points.size, latencies.size)
    }

    fun onResume() {
        _isPaused.value = false
    }

    fun onPause() {
        _isPaused.value = true
    }

    fun toggleEnabled() {
        viewModelScope.launch {
            preferences.setLatencyMonitorEnabled(!_state.value.isEnabled)
        }
    }

    fun toggleExpanded() {
        _state.update { it.copy(isExpanded = !it.isExpanded) }
    }

    fun showConfig() {
        _state.update { it.copy(isConfiguring = true) }
    }

    fun dismissConfig() {
        _state.update { it.copy(isConfiguring = false) }
    }

    fun saveConfig(host: String, thresholdMs: Int) {
        viewModelScope.launch {
            preferences.setLatencyMonitorHost(host)
            preferences.setLatencyAlertThresholdMs(thresholdMs)
            _state.update { it.copy(isConfiguring = false) }
        }
    }

    override fun onCleared() {
        stopPinging()
        super.onCleared()
    }
}

private data class PingConfig(
    val enabled: Boolean,
    val host: String,
    val threshold: Int,
    val paused: Boolean,
)
