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
import com.ventouxlabs.netlens.feature.ping.engine.PingTimeoutFiller
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
    private var watchdogJob: Job? = null
    private var startTime: Long = 0
    private var serviceActive = false
    private var pingSessionId = 0
    private var fixedCount = 0

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
        watchdogJob?.cancel()
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
        // The screen passes its count selector regardless of mode; a continuous run has no
        // packet budget, and a stale fixed count must not cap its watchdog.
        fixedCount = if (isContinuous) 0 else count

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

        restartWatchdog(currentSessionId, isContinuous, host)

        pingJob = viewModelScope.launch {
            // `.catch` swallows the failure, so the downstream `.onCompletion` sees a null
            // cause on the error path too — this flag is what actually distinguishes "ran to
            // the end" from "blew up", and the completion fill must only run for the former.
            var failed = false
            flow
                .catch { e ->
                    failed = true
                    watchdogJob?.cancel()
                    _state.update {
                        it.copy(
                            isPinging = false,
                            error = e.message ?: "Ping failed",
                        )
                    }
                }
                .onCompletion { cause ->
                    // Session guard around the WHOLE body, not just the service stop: job
                    // cancellation resumes this collector asynchronously, so a stale session's
                    // completion can run after a new startPing has installed its own watchdog
                    // and elapsed jobs — and would otherwise cancel them. The Start button's
                    // isPinging gate happens to prevent that today, but that invariant lives in
                    // the UI layer, which this block cannot rely on.
                    if (currentSessionId != pingSessionId) return@onCompletion
                    watchdogJob?.cancel()
                    elapsedJob?.cancel()
                    // A fixed run that ended on its own with fewer rows than packets got no
                    // reply for the remainder — Android's ping prints nothing for those, so
                    // the loss is derived here. Not on cancellation: the user stopping the
                    // run means the missing packets were never sent, not lost.
                    if (cause == null && !failed && !isContinuous) {
                        applyReconciling(isContinuous = false, host = host) {
                            PingTimeoutFiller.completionFill(it, fixedCount)
                        }
                    }
                    _state.update { current ->
                        current.copy(
                            isPinging = false,
                            summary = current.summary ?: computeSummary(current),
                        )
                    }
                    if (serviceActive) {
                        serviceActive = false
                        serviceController.stop()
                    }
                    withContext(NonCancellable) {
                        saveToHistory()
                    }
                }
                .collect { result ->
                    val reconciled = applyReconciling(isContinuous, host) {
                        PingTimeoutFiller.reconcile(it, result)
                    }
                    // AFTER reconciliation, gated on a reply actually being counted: ping emits
                    // `(DUP!)` lines that the unanchored reply regex parses into a same-seq
                    // result, which reconcile then drops — its latency must not pollute the
                    // min/avg/max that feed the notification and ping history. receivedDelta > 0
                    // is exactly "this reply was newly counted" (append or late upgrade).
                    // Mutating these inside the transform lambda would double-count on a CAS
                    // retry, so they update here and the summary is refreshed below — that
                    // refresh is a pure recomputation, safe to retry.
                    if (result.latencyMs != null && reconciled.receivedDelta > 0) {
                        cumulativeLatencySum += result.latencyMs
                        cumulativeLatencyCount++
                        if (result.latencyMs < cumulativeMinMs) cumulativeMinMs = result.latencyMs
                        if (result.latencyMs > cumulativeMaxMs) cumulativeMaxMs = result.latencyMs
                        if (isContinuous) {
                            _state.update {
                                it.copy(summary = computeLiveSummary(it.results, it.totalSent, it.totalReceived))
                            }
                        }
                    }
                    restartWatchdog(currentSessionId, isContinuous, host)
                }
        }
    }

    /**
     * Reconciles against the current result list and applies the outcome, atomically.
     *
     * [transform] runs *inside* the `update` lambda, against the list the compare-and-set is
     * actually about to replace — deriving the reconciled list outside and writing it in would
     * silently re-apply a stale read if `update` ever retried, and it would only be safe because
     * everything runs on `Main.immediate` with no suspension, an invariant this function has no
     * control over.
     *
     * Counters move by the reconciler's deltas, not by +1 per event: one reply can account for
     * several packets at once (gap-filled timeouts), and a late reply upgrades a timeout row
     * without any new packet having been sent.
     */
    private fun applyReconciling(
        isContinuous: Boolean,
        host: String,
        transform: (List<PingResult>) -> PingTimeoutFiller.Reconciled,
    ): PingTimeoutFiller.Reconciled {
        var applied: PingTimeoutFiller.Reconciled? = null
        _state.update { current ->
            val reconciled = transform(current.results)
            applied = reconciled
            val newResults = if (isContinuous) {
                reconciled.results.takeLast(ROLLING_BUFFER_SIZE)
            } else {
                reconciled.results
            }
            val newSent = current.totalSent + reconciled.sentDelta
            val newReceived = current.totalReceived + reconciled.receivedDelta
            current.copy(
                results = newResults,
                totalSent = newSent,
                totalReceived = newReceived,
                summary = if (isContinuous) {
                    computeLiveSummary(newResults, newSent, newReceived)
                } else {
                    current.summary
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
        return checkNotNull(applied) { "update ran at least once" }
    }

    /**
     * Paints a timeout row when the stream goes silent — the case no reply can ever reveal.
     *
     * Android's ping prints nothing for a lost packet, so a completely dead host produces no
     * output at all: without this, a continuous ping against it shows an empty list forever and
     * a fixed run shows nothing until the process finally exits. First synthetic row after
     * [WATCHDOG_SILENCE_MS] of silence, then one per [WATCHDOG_INTERVAL_MS], matching the 1s
     * send interval. Every real result restarts the clock; a fixed run stops at [fixedCount]
     * rows (the completion fill owns the remainder).
     */
    private fun restartWatchdog(sessionId: Int, isContinuous: Boolean, host: String) {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch {
            delay(WATCHDOG_SILENCE_MS)
            while (isActive && sessionId == pingSessionId && _state.value.isPinging) {
                if (!isContinuous && _state.value.results.size >= fixedCount) break
                applyReconciling(isContinuous, host) {
                    PingTimeoutFiller.reconcile(it, PingTimeoutFiller.nextWatchdogTimeout(it))
                }
                delay(WATCHDOG_INTERVAL_MS)
            }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        elapsedJob?.cancel()
        watchdogJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        pingJob?.cancel()
        elapsedJob?.cancel()
        watchdogJob?.cancel()
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

        /** Silence before the first synthetic timeout: 1s send interval + generous reply grace. */
        private const val WATCHDOG_SILENCE_MS = 3_000L

        /**
         * Cadence of further synthetic timeouts while silent. MUST match the real send interval
         * — `PingerImpl` pings with `-i 1` (continuous) and the 1s default (fixed). If that
         * interval ever changes, this drifts silently and paints phantom timeouts; there is no
         * test that can link the two, so `PingerImpl` carries the mirror comment.
         */
        private const val WATCHDOG_INTERVAL_MS = 1_000L
    }
}
