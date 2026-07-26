package com.ventouxlabs.netlens.feature.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.data.dao.WifiSurveyDao
import com.ventouxlabs.netlens.core.data.model.WifiSurveySessionEntity
import com.ventouxlabs.netlens.feature.wifi.engine.SurveyAggregator
import com.ventouxlabs.netlens.feature.wifi.engine.WifiSignalSampler
import com.ventouxlabs.netlens.feature.wifi.model.CaptureProgress
import com.ventouxlabs.netlens.feature.wifi.model.SurveyError
import com.ventouxlabs.netlens.feature.wifi.model.WifiSignalSample
import com.ventouxlabs.netlens.feature.wifi.model.WifiSurveyUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Drives a walk-through coverage survey: sample the live link continuously, and roll a short
 * burst of those samples into a named point each time the user stops somewhere and captures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WifiSurveyViewModel @Inject constructor(
    private val sampler: WifiSignalSampler,
    private val surveyDao: WifiSurveyDao,
) : ViewModel() {

    private val _state = MutableStateFlow(WifiSurveyUiState())
    val state: StateFlow<WifiSurveyUiState> = _state.asStateFlow()

    private val viewedSessionId = MutableStateFlow<Long?>(null)

    private var samplingJob: Job? = null

    /** Samples collected since the current capture started; empty when not capturing. */
    private val captureBuffer = mutableListOf<WifiSignalSample>()

    init {
        viewModelScope.launch {
            surveyDao.observeSessionSummaries().collect { sessions ->
                _state.update { it.copy(sessions = sessions) }
            }
        }
        viewModelScope.launch {
            viewedSessionId
                .flatMapLatest { id ->
                    if (id == null) emptyFlow() else surveyDao.observePoints(id).map { id to it }
                }
                .collect { (id, points) ->
                    // Guard against a late emission from a session the user has already
                    // switched away from overwriting the newly-selected session's points.
                    if (viewedSessionId.value == id) {
                        _state.update { it.copy(points = points) }
                    }
                }
        }
    }

    fun onLabelChanged(label: String) {
        _state.update { it.copy(pendingLabel = label, error = null) }
    }

    fun toggleSortWorstFirst() {
        _state.update { it.copy(sortWorstFirst = !it.sortWorstFirst) }
    }

    /**
     * Opens a session and starts sampling. [name] falls back to the connected SSID so the common
     * case needs no typing at all.
     */
    fun startSurvey(name: String) {
        if (_state.value.isSurveying) return
        viewModelScope.launch {
            val firstSample = sampler.samples(SAMPLE_INTERVAL_MS).firstSampleOrNull()
            if (firstSample == null) {
                _state.update { it.copy(error = SurveyError.NOT_CONNECTED) }
                return@launch
            }
            val resolvedName = name.trim().take(MAX_SESSION_NAME_LENGTH).ifBlank {
                firstSample.ssid ?: DEFAULT_SESSION_NAME
            }
            val sessionId = surveyDao.insertSession(
                WifiSurveySessionEntity(
                    name = resolvedName,
                    ssid = firstSample.ssid,
                    startedAt = firstSample.timestampMs,
                ),
            )
            viewedSessionId.value = sessionId
            captureBuffer.clear()
            _state.update {
                it.copy(
                    isSurveying = true,
                    activeSessionId = sessionId,
                    viewedSessionId = sessionId,
                    viewedSessionName = resolvedName,
                    liveSample = firstSample,
                    trail = listOf(firstSample.rssi),
                    points = emptyList(),
                    capture = null,
                    error = null,
                )
            }
            startSampling()
        }
    }

    fun stopSurvey() {
        val sessionId = _state.value.activeSessionId
        samplingJob?.cancel()
        samplingJob = null
        captureBuffer.clear()
        _state.update {
            it.copy(isSurveying = false, activeSessionId = null, capture = null, liveSample = null, trail = emptyList())
        }
        if (sessionId != null) {
            viewModelScope.launch {
                surveyDao.endSession(sessionId, System.currentTimeMillis())
            }
        }
    }

    /**
     * Starts a capture burst for the spot the user is standing in. The burst runs for a fixed
     * number of samples rather than banking the whole walk: averaging over the walk would smear
     * the hallway into the bedroom, while a single instantaneous reading swings several dB.
     */
    fun capturePoint() {
        val current = _state.value
        if (!current.isSurveying || current.capture != null) return
        val label = current.pendingLabel.trim().take(MAX_LABEL_LENGTH)
        if (label.isEmpty()) {
            _state.update { it.copy(error = SurveyError.LABEL_REQUIRED) }
            return
        }
        captureBuffer.clear()
        _state.update {
            it.copy(
                capture = CaptureProgress(label = label, collected = 0, target = CAPTURE_SAMPLE_TARGET),
                error = null,
            )
        }
    }

    fun cancelCapture() {
        captureBuffer.clear()
        _state.update { it.copy(capture = null) }
    }

    fun selectSession(sessionId: Long) {
        if (_state.value.isSurveying && sessionId != _state.value.activeSessionId) return
        viewModelScope.launch {
            val session = surveyDao.getSession(sessionId) ?: return@launch
            viewedSessionId.value = sessionId
            _state.update {
                it.copy(
                    viewedSessionId = sessionId,
                    viewedSessionName = session.name,
                    points = emptyList(),
                )
            }
        }
    }

    fun deletePoint(pointId: Long) {
        viewModelScope.launch { surveyDao.deletePoint(pointId) }
    }

    fun deleteSession(sessionId: Long) {
        // A deleted session can't keep recording into itself — stop first, then delete, so no
        // capture lands on a row that is about to disappear.
        if (_state.value.activeSessionId == sessionId) stopSurvey()
        viewModelScope.launch {
            surveyDao.deleteSession(sessionId)
            if (viewedSessionId.value == sessionId) {
                viewedSessionId.value = null
                _state.update {
                    it.copy(viewedSessionId = null, viewedSessionName = "", points = emptyList())
                }
            }
        }
    }

    fun buildExportText(): String {
        val current = _state.value
        val sb = StringBuilder()
        sb.appendLine("Wi-Fi coverage survey: ${current.viewedSessionName.ifBlank { "(unnamed)" }}")
        sb.appendLine("Points: ${current.points.size}")
        val weak = SurveyAggregator.weakSpots(current.points)
        if (weak.isNotEmpty()) {
            sb.appendLine("Weak spots (${weak.size}): ${weak.joinToString(", ") { it.label }}")
        }
        current.points.forEach { point ->
            sb.appendLine(
                "${point.label}  ${point.avgRssi} dBm  " +
                    "(min ${point.minRssi} / max ${point.maxRssi}, ${point.sampleCount} samples)  " +
                    "Ch ${point.channel}  ${point.linkSpeedMbps} Mbps" +
                    (point.bssid?.let { "  AP $it" } ?: ""),
            )
        }
        return sb.toString().trimEnd()
    }

    override fun onCleared() {
        super.onCleared()
        samplingJob?.cancel()
    }

    private fun startSampling() {
        samplingJob?.cancel()
        samplingJob = viewModelScope.launch {
            sampler.samples(SAMPLE_INTERVAL_MS).collect { sample ->
                onSample(sample)
            }
        }
    }

    private suspend fun onSample(sample: WifiSignalSample) {
        val capture = _state.value.capture
        if (capture != null) {
            captureBuffer.add(sample)
            if (captureBuffer.size >= capture.target) {
                finishCapture(capture.label)
            } else {
                _state.update {
                    it.copy(capture = capture.copy(collected = captureBuffer.size))
                }
            }
        }
        _state.update {
            it.copy(
                liveSample = sample,
                trail = (it.trail + sample.rssi).takeLast(TRAIL_LENGTH),
            )
        }
    }

    private suspend fun finishCapture(label: String) {
        val sessionId = _state.value.activeSessionId
        val samples = captureBuffer.toList()
        captureBuffer.clear()
        val point = sessionId?.let {
            SurveyAggregator.aggregate(
                sessionId = it,
                label = label,
                samples = samples,
                capturedAt = samples.lastOrNull()?.timestampMs ?: System.currentTimeMillis(),
            )
        }
        if (point == null) {
            _state.update { it.copy(capture = null, error = SurveyError.NO_SAMPLES) }
            return
        }
        surveyDao.insertPoint(point)
        // Clearing the label leaves the field ready for the next room instead of re-recording
        // the last one if the user taps capture again by reflex.
        _state.update { it.copy(capture = null, pendingLabel = "") }
    }

    companion object {
        /** ~1.4 readings/second: fast enough to feel live while walking, cheap enough to hold. */
        const val SAMPLE_INTERVAL_MS = 700L

        /** Roughly 5.5 s of standing still per captured spot. */
        const val CAPTURE_SAMPLE_TARGET = 8

        const val TRAIL_LENGTH = 60
        const val MAX_LABEL_LENGTH = 40
        const val MAX_SESSION_NAME_LENGTH = 60
        const val DEFAULT_SESSION_NAME = "Survey"

        /**
         * How long to wait for the first reading before declaring the phone unassociated.
         * The sampler emits nothing at all when there is no connection, so a timeout is the
         * only way to tell "not on Wi-Fi" from "about to report".
         */
        const val START_SAMPLE_TIMEOUT_MS = 3_000L
    }
}

/** One sample if the sampler can produce one, without leaving a collector running. */
private suspend fun Flow<WifiSignalSample>.firstSampleOrNull(): WifiSignalSample? =
    withTimeoutOrNull(WifiSurveyViewModel.START_SAMPLE_TIMEOUT_MS) { firstOrNull() }
