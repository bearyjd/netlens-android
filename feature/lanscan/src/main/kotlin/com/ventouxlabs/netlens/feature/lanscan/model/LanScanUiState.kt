package com.ventouxlabs.netlens.feature.lanscan.model

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity

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
    val knownDevices: List<KnownDeviceEntity> = emptyList(),
    val inventorySearchQuery: String = "",
    val inventorySortField: DeviceSortField = DeviceSortField.LAST_SEEN,
    val inventorySortAscending: Boolean = false,
)

enum class LanScanTab { SCAN, HISTORY, INVENTORY }

enum class DeviceSortField { HOSTNAME, IP, VENDOR, FIRST_SEEN, LAST_SEEN, MAC }
