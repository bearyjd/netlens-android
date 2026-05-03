package com.ventoux.netlens.ui.home.latency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventoux.netlens.core.data.preferences.UserPreferencesRepository
import com.ventoux.netlens.feature.ping.engine.Pinger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private var isPaused = false

    init {
        viewModelScope.launch {
            combine(
                preferences.latencyMonitorEnabled,
                preferences.latencyMonitorHost,
                preferences.latencyAlertThresholdMs,
            ) { enabled, host, threshold ->
                Triple(enabled, host, threshold)
            }.collect { (enabled, host, threshold) ->
                _state.update { it.copy(isEnabled = enabled, host = host, alertThresholdMs = threshold) }
                if (enabled && !isPaused) startPinging() else stopPinging()
            }
        }
    }

    private fun startPinging() {
        if (pingJob?.isActive == true) return
        val host = _state.value.host
        pingJob = viewModelScope.launch {
            _state.update { it.copy(isRunning = true) }
            pinger.pingContinuous(host).collect { result ->
                val point = LatencyDataPoint(
                    timestampMs = System.currentTimeMillis(),
                    latencyMs = if (result.isTimeout) null else result.latencyMs,
                )
                _state.update { current ->
                    val newPoints = (current.dataPoints + point).takeLast(60)
                    current.copy(
                        dataPoints = newPoints,
                        summary = computeSummary(newPoints),
                    )
                }
            }
        }
        pingJob?.invokeOnCompletion {
            _state.update { it.copy(isRunning = false) }
        }
    }

    private fun stopPinging() {
        pingJob?.cancel()
        pingJob = null
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
        isPaused = false
        if (_state.value.isEnabled) startPinging()
    }

    fun onPause() {
        isPaused = true
        stopPinging()
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
            stopPinging()
            if (_state.value.isEnabled && !isPaused) startPinging()
        }
    }

    override fun onCleared() {
        stopPinging()
        super.onCleared()
    }
}
