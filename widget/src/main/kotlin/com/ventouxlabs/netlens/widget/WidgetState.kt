package com.ventouxlabs.netlens.widget

import com.ventouxlabs.netlens.core.network.VpnState

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
    val vpnState: VpnState = VpnState.None,

    val lastScanTimestamp: Long = 0L,
    val isScanRunning: Boolean = false,

    val localIp: String = "",
    val pingMs: Int = -1,
    val hasIpv6: Boolean = false,
    val vpnInterfaceName: String = "",
    val rssi: Int = -1000,
    val rssiLevel: Int = -1,
    val linkSpeedMbps: Int = -1,
    val cellGeneration: String = "",
    val isMetered: Boolean = false,
    val isCaptivePortal: Boolean = false,
    val hasPrivateDns: Boolean = false,

    val dnsServers: String = "",
    val routingMode: String = "",
    val isDnsLeaking: Boolean = false,
    val lastRefreshMs: Long = 0L,
) {
    val primaryDns: String get() = dnsServers.split(",").firstOrNull()?.takeIf { it.isNotEmpty() }.orEmpty()
    fun isStale(now: Long = System.currentTimeMillis()): Boolean =
        lastScanTimestamp > 0L && now - lastScanTimestamp > STALE_THRESHOLD_MS

    val hasScore: Boolean get() = scoreGrade.isNotEmpty()
    val hasSpeed: Boolean get() = speedMbps >= 0f
    val hasLatency: Boolean get() = latencyMs >= 0L
    val hasIssues: Boolean get() = issueCount > 0
    val hasPing: Boolean get() = pingMs >= 0
    val hasRssi: Boolean get() = rssiLevel >= 0
    val hasLinkSpeed: Boolean get() = linkSpeedMbps > 0

    companion object {
        private const val STALE_THRESHOLD_MS = 5 * 60 * 1000L
        const val STALE_ALERT_THRESHOLD_MS = 10 * 60 * 1000L
    }
}
