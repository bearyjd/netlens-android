package us.beary.netlens.feature.ping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import us.beary.netlens.core.data.dao.PingHistoryDao
import us.beary.netlens.core.data.model.PingHistoryEntry
import us.beary.netlens.feature.ping.engine.Pinger
import us.beary.netlens.feature.ping.model.PingSummary
import us.beary.netlens.feature.ping.model.PingUiState
import javax.inject.Inject

@HiltViewModel
class PingViewModel @Inject constructor(
    private val pinger: Pinger,
    private val pingHistoryDao: PingHistoryDao,
) : ViewModel() {

    private val _state = MutableStateFlow(PingUiState())
    val state: StateFlow<PingUiState> = _state.asStateFlow()

    private var pingJob: Job? = null

    fun onHostChange(host: String) {
        _state.update { it.copy(host = host) }
    }

    fun startPing(host: String, count: Int) {
        pingJob?.cancel()
        _state.update {
            it.copy(
                isPinging = true,
                results = emptyList(),
                summary = null,
                error = null,
            )
        }

        pingJob = viewModelScope.launch {
            pinger.ping(host, count)
                .catch { e ->
                    _state.update {
                        it.copy(
                            isPinging = false,
                            error = e.message ?: "Ping failed",
                        )
                    }
                }
                .onCompletion {
                    _state.update { current ->
                        current.copy(
                            isPinging = false,
                            summary = computeSummary(current),
                        )
                    }
                    withContext(NonCancellable) {
                        saveToHistory()
                    }
                }
                .collect { result ->
                    _state.update { current ->
                        current.copy(results = current.results + result)
                    }
                }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
    }

    private suspend fun saveToHistory() {
        val state = _state.value
        val summary = state.summary ?: return
        if (summary.transmitted == 0) return
        pingHistoryDao.insert(
            PingHistoryEntry(
                host = state.host,
                sentCount = summary.transmitted,
                receivedCount = summary.received,
                minMs = summary.minMs,
                avgMs = summary.avgMs,
                maxMs = summary.maxMs,
            ),
        )
    }

    private fun computeSummary(current: PingUiState): PingSummary? {
        val results = current.results
        if (results.isEmpty()) return null

        val transmitted = results.size
        val received = results.count { !it.isTimeout }
        val latencies = results.mapNotNull { it.latencyMs }
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

    fun buildCopyText(): String {
        val current = _state.value
        val sb = StringBuilder()
        sb.appendLine("Ping results for ${current.host}:")
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
            sb.appendLine(
                "Sent: ${s.transmitted}, Received: ${s.received}, Loss: %.0f%%".format(s.lossPercent),
            )
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
}
