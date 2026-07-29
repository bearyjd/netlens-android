package com.ventouxlabs.netlens.feature.wifi.model

import com.ventouxlabs.netlens.core.data.model.WifiSurveyPointEntity
import com.ventouxlabs.netlens.core.data.model.WifiSurveySessionSummary

data class WifiSurveyUiState(
    val isSurveying: Boolean = false,
    /** Session being recorded into, or null when idle. */
    val activeSessionId: Long? = null,
    /** Session whose points are on screen — the active one, or one picked from history. */
    val viewedSessionId: Long? = null,
    val viewedSessionName: String = "",
    /** Most recent live reading; null before the first sample or while disconnected. */
    val liveSample: WifiSignalSample? = null,
    /** Recent RSSI readings, oldest first — the "walk trail" behind the live meter. */
    val trail: List<Int> = emptyList(),
    /** Non-null while a capture burst is collecting samples for a spot. */
    val capture: CaptureProgress? = null,
    val pendingLabel: String = "",
    val points: List<WifiSurveyPointEntity> = emptyList(),
    val sessions: List<WifiSurveySessionSummary> = emptyList(),
    val sortWorstFirst: Boolean = false,
    val error: SurveyError? = null,
) {
    val liveQuality: SignalQuality? get() = liveSample?.let { SignalQuality.forRssi(it.rssi) }
}

data class CaptureProgress(
    val label: String,
    val collected: Int,
    val target: Int,
) {
    val fraction: Float get() = if (target == 0) 0f else collected.toFloat() / target
}

/** Survey failures worth telling the user about, mapped to strings in the UI layer. */
enum class SurveyError {
    NOT_CONNECTED,
    LABEL_REQUIRED,
    NO_SAMPLES,

    /** The association dropped part-way through a capture, so the burst is incomplete. */
    SIGNAL_LOST,

    /** The screen was backgrounded mid-capture, so the burst no longer means "one spot". */
    CAPTURE_INTERRUPTED,

    ;

    /**
     * Whether a live reading disproves this error, so it should clear itself the moment the radio
     * comes back.
     *
     * True only for the two that are statements about the *current* signal. Leaving "signal lost"
     * on screen while the meter shows a strong reading is worse than useless during a walk — the
     * user is standing in the spot it is lying about. The rest describe something that happened
     * and stays true: no label was typed, a burst produced nothing, a capture was interrupted.
     * Those clear when the user acts on them.
     */
    val clearedByLiveSignal: Boolean
        get() = this == NOT_CONNECTED || this == SIGNAL_LOST
}
