package com.ventouxlabs.netlens.feature.wifiaudit.model

data class ConnectedNetworkInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val security: String,
    val capabilities: String,
    val linkSpeedMbps: Int,
    val ipAddress: String?,
)
