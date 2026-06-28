package com.ventouxlabs.netlens.ui.home.latency

data class LatencyMonitorState(
    val isEnabled: Boolean = false,
    val isRunning: Boolean = false,
    val host: String = DEFAULT_HOST,
    val dataPoints: List<LatencyDataPoint> = emptyList(),
    val summary: LatencySummary? = null,
    val alertThresholdMs: Int = DEFAULT_THRESHOLD_MS,
    val isExpanded: Boolean = true,
    val isConfiguring: Boolean = false,
    val error: String? = null,
) {
    companion object {
        const val DEFAULT_HOST = "1.1.1.1"
        const val DEFAULT_THRESHOLD_MS = 200
        const val MAX_DATA_POINTS = 60
    }
}

data class LatencyDataPoint(
    val timestampMs: Long,
    val latencyMs: Float?, // null = timeout
)

data class LatencySummary(
    val minMs: Float,
    val avgMs: Float,
    val maxMs: Float,
    val jitterMs: Float,
    val lossPercent: Float,
    val totalSent: Int,
    val totalReceived: Int,
)
