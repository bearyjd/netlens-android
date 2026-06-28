package com.ventouxlabs.netlens.feature.ping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ventouxlabs.netlens.core.data.dao.PingHistoryDao
import com.ventouxlabs.netlens.core.data.model.PingHistoryEntry
import com.ventouxlabs.netlens.feature.ping.engine.Pinger
import com.ventouxlabs.netlens.feature.ping.model.PingMode
import com.ventouxlabs.netlens.feature.ping.model.PingResult
import com.ventouxlabs.netlens.feature.ping.model.PingSummary
import com.ventouxlabs.netlens.feature.ping.model.PingUiState
import com.ventouxlabs.netlens.feature.ping.service.PingServiceController
import javax.inject.Inject

@HiltViewModel
class PingViewModel @Inject constructor(
    private val pinger: Pinger,
    private val pingHistoryDao: PingHistoryDao,
    private val serviceController: PingServiceController,
) : ViewModel() {

    private val _state = MutableStateFlow(PingUiState())
    val state: StateFlow<PingUiState> = _state.asStateFlow()

    private var pingJob: Job? = null
    private var elapsedJob: Job? = null
    private var startTime: Long = 0
    private var serviceActive = false
    private var pingSessionId = 0

    private var cumulativeMinMs = Float.MAX_VALUE
    private var cumulativeMaxMs = 0f
    private var cumulativeLatencySum = 0.0
    private var cumulativeLatencyCount = 0

    init {
        viewModelScope.launch {
            serviceController.stopRequested.collect { requested ->
                if (requested && _state.value.isPinging && _state.value.mode == PingMode.CONTINUOUS) {
                    stopPing()
                }
            }
        }
    }

    fun onHostChange(host: String) {
        _state.update { it.copy(host = host) }
    }

    fun onModeChanged(mode: PingMode) {
        if (_state.value.isPinging) return
        _state.update { it.copy(mode = mode) }
    }

    fun startPing(host: String, count: Int) {
        pingJob?.cancel()
        elapsedJob?.cancel()
        if (serviceActive) {
            serviceActive = false
            serviceController.stop()
        }
        val currentSessionId = ++pingSessionId
        cumulativeMinMs = Float.MAX_VALUE
        cumulativeMaxMs = 0f
        cumulativeLatencySum = 0.0
        cumulativeLatencyCount = 0
        _state.update {
            it.copy(
                isPinging = true,
                results = emptyList(),
                summary = null,
                error = null,
                totalSent = 0,
                totalReceived = 0,
                elapsedMs = 0,
            )
        }

        startTime = System.currentTimeMillis()
        val isContinuous = _state.value.mode == PingMode.CONTINUOUS

        if (isContinuous) {
            serviceActive = true
            startElapsedTimer()
            serviceController.start(host)
        }

        val flow = if (isContinuous) {
            pinger.pingContinuous(host)
        } else {
            pinger.ping(host, count)
        }

        pingJob = viewModelScope.launch {
            flow
                .catch { e ->
                    _state.update {
                        it.copy(
                            isPinging = false,
                            error = e.message ?: "Ping failed",
                        )
                    }
                }
                .onCompletion {
                    elapsedJob?.cancel()
                    _state.update { current ->
                        current.copy(
                            isPinging = false,
                            summary = current.summary ?: computeSummary(current),
                        )
                    }
                    if (currentSessionId == pingSessionId && serviceActive) {
                        serviceActive = false
                        serviceController.stop()
                    }
                    withContext(NonCancellable) {
                        saveToHistory()
                    }
                }
                .collect { result ->
                    if (result.latencyMs != null) {
                        cumulativeLatencySum += result.latencyMs
                        cumulativeLatencyCount++
                        if (result.latencyMs < cumulativeMinMs) cumulativeMinMs = result.latencyMs
                        if (result.latencyMs > cumulativeMaxMs) cumulativeMaxMs = result.latencyMs
                    }
                    _state.update { current ->
                        val newResults = if (current.mode == PingMode.CONTINUOUS) {
                            (current.results + result).takeLast(ROLLING_BUFFER_SIZE)
                        } else {
                            current.results + result
                        }
                        val newSent = current.totalSent + 1
                        val newReceived = current.totalReceived + if (!result.isTimeout) 1 else 0
                        current.copy(
                            results = newResults,
                            totalSent = newSent,
                            totalReceived = newReceived,
                            summary = if (current.mode == PingMode.CONTINUOUS) {
                                computeLiveSummary(newResults, newSent, newReceived)
                            } else {
                                null
                            },
                        )
                    }
                    if (isContinuous) {
                        val s = _state.value
                        serviceController.updateNotification(
                            host,
                            s.totalSent,
                            s.summary?.lossPercent ?: 0f,
                        )
                    }
                }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        elapsedJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        pingJob?.cancel()
        elapsedJob?.cancel()
        if (serviceActive) {
            serviceActive = false
            serviceController.stop()
        }
    }

    private fun startElapsedTimer() {
        elapsedJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                _state.update { it.copy(elapsedMs = elapsed) }
                if (elapsed >= MAX_CONTINUOUS_DURATION_MS) {
                    pingJob?.cancel()
                    break
                }
                delay(1000)
            }
        }
    }

    private suspend fun saveToHistory() {
        val state = _state.value
        val summary = state.summary ?: return
        val sentCount = if (state.mode == PingMode.CONTINUOUS) state.totalSent else summary.transmitted
        val receivedCount = if (state.mode == PingMode.CONTINUOUS) state.totalReceived else summary.received
        if (sentCount == 0) return
        pingHistoryDao.insert(
            PingHistoryEntry(
                host = state.host,
                sentCount = sentCount,
                receivedCount = receivedCount,
                minMs = summary.minMs,
                avgMs = summary.avgMs,
                maxMs = summary.maxMs,
                mode = state.mode.name,
            ),
        )
    }

    private fun computeLiveSummary(
        windowResults: List<PingResult>,
        totalSent: Int,
        totalReceived: Int,
    ): PingSummary {
        val lossPercent = if (totalSent > 0) {
            ((totalSent - totalReceived).toFloat() / totalSent) * 100f
        } else {
            0f
        }
        val avg = if (cumulativeLatencyCount > 0) {
            (cumulativeLatencySum / cumulativeLatencyCount).toFloat()
        } else {
            0f
        }

        val windowLatencies = windowResults.mapNotNull { it.latencyMs }
        val jitter = if (windowLatencies.size >= 2) {
            val windowAvg = windowLatencies.average().toFloat()
            val variance = windowLatencies.map { (it - windowAvg) * (it - windowAvg) }.average().toFloat()
            kotlin.math.sqrt(variance.toDouble()).toFloat()
        } else {
            0f
        }

        return PingSummary(
            transmitted = totalSent,
            received = totalReceived,
            lossPercent = lossPercent,
            minMs = if (cumulativeMinMs == Float.MAX_VALUE) 0f else cumulativeMinMs,
            avgMs = avg,
            maxMs = cumulativeMaxMs,
            jitterMs = jitter,
        )
    }

    private fun computeSummary(current: PingUiState): PingSummary? {
        val results = current.results
        if (results.isEmpty()) return null

        val latencies = results.mapNotNull { it.latencyMs }
        val transmitted = results.size
        val received = results.count { !it.isTimeout }
        val lossPercent = if (transmitted > 0) {
            ((transmitted - received).toFloat() / transmitted) * 100f
        } else {
            0f
        }
        val avg = if (latencies.isNotEmpty()) latencies.average().toFloat() else 0f
        val jitter = if (latencies.size >= 2) {
            val variance = latencies.map { (it - avg) * (it - avg) }.average().toFloat()
            kotlin.math.sqrt(variance.toDouble()).toFloat()
        } else {
            0f
        }

        return PingSummary(
            transmitted = transmitted,
            received = received,
            lossPercent = lossPercent,
            minMs = latencies.minOrNull() ?: 0f,
            avgMs = avg,
            maxMs = latencies.maxOrNull() ?: 0f,
            jitterMs = jitter,
        )
    }

    fun buildExportText(): String {
        val current = _state.value
        val sb = StringBuilder()
        sb.appendLine("Ping results for ${current.host}:")
        if (current.mode == PingMode.CONTINUOUS) {
            sb.appendLine("Mode: Continuous")
        }
        current.results.forEach { r ->
            if (r.isTimeout) {
                sb.appendLine("seq=${r.sequenceNumber} timeout")
            } else {
                val ttlPart = r.ttl?.let { " ttl=$it" } ?: ""
                val msPart = r.latencyMs?.let { " time=%.1fms".format(it) } ?: ""
                sb.appendLine("seq=${r.sequenceNumber}$msPart$ttlPart")
            }
        }
        current.summary?.let { s ->
            sb.appendLine("--- Statistics ---")
            if (current.mode == PingMode.CONTINUOUS) {
                sb.appendLine(
                    "Sent: ${current.totalSent}, Received: ${current.totalReceived}, Loss: %.0f%%".format(s.lossPercent),
                )
            } else {
                sb.appendLine(
                    "Sent: ${s.transmitted}, Received: ${s.received}, Loss: %.0f%%".format(s.lossPercent),
                )
            }
            sb.appendLine(
                "Min: %.1fms, Avg: %.1fms, Max: %.1fms, Jitter: %.1fms".format(
                    s.minMs,
                    s.avgMs,
                    s.maxMs,
                    s.jitterMs,
                ),
            )
        }
        return sb.toString().trimEnd()
    }

    companion object {
        private const val ROLLING_BUFFER_SIZE = 100
        private const val MAX_CONTINUOUS_DURATION_MS = 3_600_000L
    }
}
