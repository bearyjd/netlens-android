package com.ventouxlabs.netlens.feature.lanscan.model

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.LanScanInventoryEntry
import com.ventouxlabs.netlens.core.scan.model.LanDevice

data class LanScanUiState(
    val devices: List<LanDevice> = emptyList(),
    val isScanning: Boolean = false,
    val subnetInfo: String = "",
    val progress: Float = 0f,
    val error: String? = null,
    val deviceCount: Int = 0,
    val rangeMode: ScanRangeMode = ScanRangeMode.AUTO,
    val customRange: String = "",
    val manualLatitude: String = "",
    val manualLongitude: String = "",
    val locationStatus: LocationStatus = LocationStatus.IDLE,
    val capturedLocation: ScanCoordinates? = null,
    /** Manual lat/long entry is the fallback for when no fix can be obtained, not the default. */
    val showManualLocationEntry: Boolean = false,
    val rangeError: String? = null,
    val selectedDevice: LanDevice? = null,
    val suggestedNetworks: List<SuggestedNetwork> = emptyList(),
    val selectedTab: LanScanTab = LanScanTab.SCAN,
    val historyEntries: List<LanScanHistoryUiModel> = emptyList(),
    val savedInventories: List<LanScanInventoryEntry> = emptyList(),
    val knownDevices: List<KnownDeviceEntity> = emptyList(),
    val inventorySearchQuery: String = "",
    val inventorySortField: DeviceSortField = DeviceSortField.LAST_SEEN,
    val inventorySortAscending: Boolean = false,
)

/**
 * UNAVAILABLE is a first-class outcome, not an error: `getLastKnownLocation` legitimately
 * returns nothing indoors, after a reboot, with location services off, or without permission.
 * The user needs to be told so they can fall back to manual entry — a scan still proceeds
 * either way.
 */
enum class LocationStatus { IDLE, CAPTURING, FOUND, UNAVAILABLE }

enum class LanScanTab { SCAN, HISTORY, INVENTORY, SAVED }

enum class DeviceSortField { HOSTNAME, IP, VENDOR, FIRST_SEEN, LAST_SEEN, MAC }
