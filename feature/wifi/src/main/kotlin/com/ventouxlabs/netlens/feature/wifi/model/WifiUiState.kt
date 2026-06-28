package com.ventouxlabs.netlens.feature.wifi.model

data class WifiUiState(
    val networks: List<WifiNetwork> = emptyList(),
    val connectedInfo: ConnectedWifiInfo? = null,
    val isScanning: Boolean = false,
    val error: String? = null,
    val selectedBand: WifiBand = WifiBand.ALL,
    val lastScanTimestamp: Long? = null,
    val permissionGranted: Boolean = false,
)
