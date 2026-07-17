package com.ventouxlabs.netlens.feature.devices.model

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity

data class DevicesUiState(
    val devices: List<KnownDeviceEntity> = emptyList(),
    val searchQuery: String = "",
    val watchedNetworks: List<WatchedNetworkEntity> = emptyList(),
    val cadence: WatchCadence = WatchCadence.DEFAULT,
    val masterWatchEnabled: Boolean = false,
    val selectedDeviceId: Long? = null,
)
