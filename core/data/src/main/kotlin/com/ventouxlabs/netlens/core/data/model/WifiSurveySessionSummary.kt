package com.ventouxlabs.netlens.core.data.model

/** Row projection for the past-surveys list: session header plus its point roll-up. */
data class WifiSurveySessionSummary(
    val id: Long,
    val name: String,
    val ssid: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val pointCount: Int,
    // Null until the session has at least one point.
    val worstRssi: Int?,
    val bestRssi: Int?,
)
