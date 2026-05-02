package com.ventoux.netlens.widget

data class WidgetState(
    val scoreGrade: String = "",
    val scoreColorArgb: Int = 0,
    val issueCount: Int = 0,
    val topIssue: String = "",
    val topIssueId: String = "",

    val isConnected: Boolean = false,
    val ssid: String? = null,
    val encryptionType: String = "",
    val isEncryptionSecure: Boolean = true,

    val publicIp: String = "",
    val countryFlag: String = "",
    val countryName: String = "",
    val countryCode: String = "",
    val ispName: String = "",
    val asnName: String = "",

    val speedMbps: Float = -1f,
    val speedLabel: String = "",
    val speedTimestamp: Long = 0L,

    val latencyMs: Long = -1L,
    val deviceCount: Int = 0,
    val vpnActive: Boolean = false,

    val lastScanTimestamp: Long = 0L,
    val isScanRunning: Boolean = false,
) {
    fun isStale(now: Long = System.currentTimeMillis()): Boolean =
        lastScanTimestamp > 0L && now - lastScanTimestamp > STALE_THRESHOLD_MS

    val hasScore: Boolean get() = scoreGrade.isNotEmpty()
    val hasSpeed: Boolean get() = speedMbps >= 0f
    val hasLatency: Boolean get() = latencyMs >= 0L
    val hasIssues: Boolean get() = issueCount > 0

    companion object {
        private const val STALE_THRESHOLD_MS = 5 * 60 * 1000L
    }
}
