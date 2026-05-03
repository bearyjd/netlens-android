package com.ventoux.netlens.ui.home.latency

data class LatencyMonitorState(
    val isEnabled: Boolean = false,
    val isRunning: Boolean = false,
    val host: String = "1.1.1.1",
    val dataPoints: List<LatencyDataPoint> = emptyList(),
    val summary: LatencySummary? = null,
    val alertThresholdMs: Int = 200,
    val isExpanded: Boolean = true,
    val isConfiguring: Boolean = false,
)

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
