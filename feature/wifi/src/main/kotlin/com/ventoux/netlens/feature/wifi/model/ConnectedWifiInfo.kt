package com.ventoux.netlens.feature.wifi.model

data class ConnectedWifiInfo(
    val ssid: String,
    val bssid: String,
    val linkSpeedMbps: Int,
    val frequency: Int,
    val rssi: Int,
    val ipAddress: String?,
)
