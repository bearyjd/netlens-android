package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository
import com.ventouxlabs.netlens.core.scan.model.LanDevice

class RecordingDeviceInventoryRepository : DeviceInventoryRepository {
    data class Call(val devices: List<LanDevice>, val networkId: Long?)
    val calls = mutableListOf<Call>()
    override suspend fun persistScan(devices: List<LanDevice>, networkId: Long?) {
        calls.add(Call(devices, networkId))
    }
}
