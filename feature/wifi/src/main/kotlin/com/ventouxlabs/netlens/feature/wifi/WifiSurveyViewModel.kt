package com.ventouxlabs.netlens.feature.wifi

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.data.dao.WifiSurveyDao
import com.ventouxlabs.netlens.core.data.di.ApplicationScope
import com.ventouxlabs.netlens.core.data.model.WifiSurveySessionEntity
import com.ventouxlabs.netlens.feature.wifi.engine.SurveyAggregator
import com.ventouxlabs.netlens.feature.wifi.engine.WifiSignalSampler
import com.ventouxlabs.netlens.feature.wifi.model.CaptureProgress
import com.ventouxlabs.netlens.feature.wifi.model.SurveyError
import com.ventouxlabs.netlens.feature.wifi.model.WifiSignalSample
import com.ventouxlabs.netlens.feature.wifi.model.WifiSurveyUiState
import com.ventouxlabs.netlens.feature.wifi.model.apShortName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(WifiSurveyUiState())
    val state: StateFlow<WifiSurveyUiState> = _state.asStateFlow()

    private val viewedSessionId = MutableStateFlow<Long?>(null)

    private var samplingJob: Job? = null

    /** Set between the Start tap and the first reading, while `isSurveying` is still false. */
    private var starting = false

    private var startJob: Job? = null

    /**
     * The id of a session row that has been written but not yet closed. Tracked separately from
     * `activeSessionId` because that field is only set once the start coroutine completes — if the
     * start is cancelled after the insert, this is the only remaining handle on the open row.
     */
    private var openSessionId: Long? = null

    /**
     * Wall-clock reader, overridable in tests. The capture gap below is measured in real time, not
     * on the coroutine test scheduler, so a test cannot advance it with virtual time.
     */
    internal var nowMs: () -> Long = System::currentTimeMillis

    /** When a capture was paused by a configuration change; null when not paused. */
    private var captureSuspendedAtMs: Long? = null

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
        // `isSurveying` is only set once the first reading lands, which is up to
        // START_SAMPLE_TIMEOUT_MS away — so it cannot be the guard against a second tap in the
        // meantime. This flag is read and written on the caller's thread with no suspension in
        // between, so two taps can never both get past it and open two sessions.
        if (_state.value.isSurveying || starting) return
        starting = true
        startJob = viewModelScope.launch {
            try {
                val firstSample = sampler.samples(SAMPLE_INTERVAL_MS).firstSampleOrNull()
                if (firstSample == null) {
                    _state.update { it.copy(error = SurveyError.NOT_CONNECTED) }
                    return@launch
                }
                val resolvedName = name.trim().take(MAX_SESSION_NAME_LENGTH).ifBlank {
                    firstSample.ssid ?: DEFAULT_SESSION_NAME
                }
                // NonCancellable so the row's id is always recorded in openSessionId. Cancelling
                // *at* the insert suspension point would otherwise leave the row written and its
                // id known to nobody — an endedAt=null session the UI can never finish, which is
                // the exact leak onCleared exists to prevent.
                val sessionId = withContext(NonCancellable) {
                    val id = surveyDao.insertSession(
                        WifiSurveySessionEntity(
                            name = resolvedName,
                            ssid = firstSample.ssid,
                            startedAt = firstSample.timestampMs,
                        ),
                    )
                    openSessionId = id
                    id
                }
                // Nothing after the NonCancellable block suspends, so without this an already
                // cancelled start would run to completion — publishing state on a dead ViewModel
                // and never reaching the catch that closes the row it just wrote.
                ensureActive()
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
            } catch (cancellation: CancellationException) {
                // The start was cancelled after its row was written. onCleared cannot clean this
                // up — it may already have run, reading both ids as null while the insert was
                // still in flight — so the cancelled start closes its own row, on a scope that
                // outlives the ViewModel. Rethrown so cancellation still propagates normally.
                closeOpenSessionOnAppScope()
                throw cancellation
            } finally {
                starting = false
            }
        }
    }

    /** Closes the open session row, if any, on a scope that survives ViewModel cancellation. */
    private fun closeOpenSessionOnAppScope() {
        val sessionId = openSessionId ?: return
        openSessionId = null
        endSessionOnAppScope(sessionId)
    }

    /**
     * The only path that closes a session off `viewModelScope`. Failures are logged rather than
     * swallowed: this runs where nothing is watching, so an exception here would otherwise leave a
     * permanently open row with no trace of why.
     */
    private fun endSessionOnAppScope(sessionId: Long) {
        appScope.launch {
            runCatching { surveyDao.endSession(sessionId, nowMs()) }
                .onFailure { Log.w(TAG, "failed to close survey session $sessionId", it) }
        }
    }

    /**
     * The survey screen left the foreground. `viewModelScope` is not lifecycle-aware, so without
     * this the sampler keeps waking the radio every SAMPLE_INTERVAL_MS for as long as the entry
     * stays on the back stack — a background battery drain the user cannot see or stop.
     *
     * [isConfigurationChange] separates "the activity is being recreated" from "the user left".
     * ON_STOP fires for both, but a rotation or a fold is not a departure: abandoning the capture
     * there would destroy a burst over a gap of milliseconds, and on a foldable that gesture is
     * constant. On a real departure the capture *is* abandoned — a burst split across a
     * backgrounded gap is not five seconds of standing in one spot, which is the only thing a
     * captured point is allowed to mean.
     *
     * The session stays open either way, so returning resumes the same walk.
     */
    fun onScreenStopped(isConfigurationChange: Boolean = false) {
        // Cancel the start too, not just sampling: within START_SAMPLE_TIMEOUT_MS the sampling job
        // does not exist yet, so cancelling only samplingJob would let the start coroutine resume
        // after this returns and begin polling the radio with the screen stopped — reinstating the
        // drain this method exists to stop. Cancelling runs startSurvey's finally, clearing
        // `starting` so the next tap is not swallowed.
        startJob?.cancel()
        startJob = null
        samplingJob?.cancel()
        samplingJob = null

        // A start cancelled after its row was written leaves a session nothing will ever close.
        val abortedSessionId = openSessionId?.takeIf { !_state.value.isSurveying }
        if (abortedSessionId != null) {
            openSessionId = null
            endSessionOnAppScope(abortedSessionId)
        }

        if (_state.value.capture != null) {
            if (isConfigurationChange) {
                // Held, not abandoned — but only for as long as a recreation plausibly takes.
                // onScreenStarted enforces the bound; see MAX_CAPTURE_GAP_MS.
                captureSuspendedAtMs = nowMs()
            } else {
                abandonCapture(SurveyError.CAPTURE_INTERRUPTED)
            }
        }
        _state.update { it.copy(liveSample = null) }
    }

    /** Drops the in-progress burst and reports [error]; the survey itself stays open. */
    private fun abandonCapture(error: SurveyError) {
        captureBuffer.clear()
        captureSuspendedAtMs = null
        _state.update { it.copy(capture = null, error = error) }
    }

    /** Back in the foreground: pick the walk up again if a survey is still open. */
    fun onScreenStarted() {
        // A configuration change holds the burst rather than dropping it, but nothing bounds how
        // long the activity stays stopped — fold the phone and pocket it and the "spot" would end
        // up spanning two rooms. A captured point may only ever mean one place, so past this gap
        // the burst is discarded like any other interruption.
        captureSuspendedAtMs?.let { suspendedAt ->
            captureSuspendedAtMs = null
            if (nowMs() - suspendedAt > MAX_CAPTURE_GAP_MS && _state.value.capture != null) {
                abandonCapture(SurveyError.CAPTURE_INTERRUPTED)
            }
        }
        if (_state.value.isSurveying && samplingJob == null) startSampling()
    }

    fun stopSurvey() {
        val sessionId = _state.value.activeSessionId ?: openSessionId
        startJob?.cancel()
        startJob = null
        samplingJob?.cancel()
        samplingJob = null
        openSessionId = null
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
        if (openSessionId == sessionId) openSessionId = null
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
                    // Short form only: the full BSSID is an AP MAC, and public wardriving
                    // databases turn one into a street address. An export leaves the device.
                    (point.bssid?.let { "  AP ${apShortName(it)}" } ?: ""),
            )
        }
        return sb.toString().trimEnd()
    }

    override fun onCleared() {
        super.onCleared()
        samplingJob?.cancel()
        // Navigating away abandons the survey, so close its session rather than leaving a row
        // with a null endedAt that nothing can ever finish — the UI offers no way back into a
        // session it no longer considers active. viewModelScope is already cancelled here, so
        // this has to run on a scope that outlives the ViewModel.
        // openSessionId covers the window where the row exists but the start coroutine had not
        // yet published activeSessionId — cancellation there used to orphan the row silently.
        val sessionId = _state.value.activeSessionId ?: openSessionId
        if (sessionId != null) {
            openSessionId = null
            endSessionOnAppScope(sessionId)
        }
    }

    private fun startSampling() {
        samplingJob?.cancel()
        samplingJob = viewModelScope.launch {
            sampler.samples(SAMPLE_INTERVAL_MS).collect { sample ->
                onSample(sample)
            }
        }
    }

    private suspend fun onSample(sample: WifiSignalSample?) {
        if (sample == null) {
            onSignalLost()
            return
        }
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

    /**
     * The association dropped. Abandon any capture in progress rather than leaving it waiting for
     * samples that cannot arrive, and blank the live meter — the last good reading is not what the
     * user is standing in, and this happens exactly where they most want the truth: a dead corner.
     * The survey itself stays open, so walking back into range resumes it.
     */
    private fun onSignalLost() {
        val hadCapture = _state.value.capture != null
        captureBuffer.clear()
        _state.update {
            it.copy(
                capture = null,
                liveSample = null,
                error = if (hadCapture) SurveyError.SIGNAL_LOST else it.error,
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
        private const val TAG = "WifiSurvey"

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

        /**
         * How long a capture may stay paused across a configuration change before it is discarded.
         * An activity recreation is sub-second; anything beyond this is the user having gone away
         * with the phone, and a burst either side of that is not one spot.
         */
        const val MAX_CAPTURE_GAP_MS = 3_000L
    }
}

/**
 * The first real reading, without leaving a collector running.
 *
 * The sampler emits null on every tick while unassociated, so this has to wait for a non-null
 * one; the timeout is what distinguishes "not on Wi-Fi" from "about to report".
 */
private suspend fun Flow<WifiSignalSample?>.firstSampleOrNull(): WifiSignalSample? =
    withTimeoutOrNull(WifiSurveyViewModel.START_SAMPLE_TIMEOUT_MS) { filterNotNull().firstOrNull() }
