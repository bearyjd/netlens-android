package com.ventoux.netlens.feature.lanscan.model

data class LanScanUiState(
    val devices: List<LanDevice> = emptyList(),
    val isScanning: Boolean = false,
    val subnetInfo: String = "",
    val progress: Float = 0f,
    val error: String? = null,
    val deviceCount: Int = 0,
    val rangeMode: ScanRangeMode = ScanRangeMode.AUTO,
    val customRange: String = "",
    val rangeError: String? = null,
    val selectedDevice: LanDevice? = null,
    val suggestedNetworks: List<SuggestedNetwork> = emptyList(),
    val selectedTab: LanScanTab = LanScanTab.SCAN,
    val historyEntries: List<LanScanHistoryUiModel> = emptyList(),
)

enum class LanScanTab { SCAN, HISTORY }
