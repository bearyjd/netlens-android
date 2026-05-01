package com.ventoux.netlens.feature.wifi.model

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val frequency: Int,
    val channelNumber: Int,
    val channelWidth: Int,
    val level: Int,
    val security: String,
    val capabilities: String,
)
