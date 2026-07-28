package com.ventouxlabs.netlens.feature.wifi.model

/** One instantaneous reading of the association with the currently-connected AP. */
data class WifiSignalSample(
    val timestampMs: Long,
    val rssi: Int,
    val ssid: String?,
    val bssid: String?,
    val frequency: Int,
    val linkSpeedMbps: Int,
)
