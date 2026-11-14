package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository
import com.ventouxlabs.netlens.core.scan.identity.MergeSuggestion
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class RecordingDeviceInventoryRepository : DeviceInventoryRepository {
    data class Call(val devices: List<LanDevice>, val networkId: Long?)
    val calls = mutableListOf<Call>()
    override suspend fun persistScan(devices: List<LanDevice>, networkId: Long?) {
        calls.add(Call(devices, networkId))
    }

    override val pendingMergeSuggestions: Flow<List<MergeSuggestion.BetweenRows>> = flowOf(emptyList())
    override suspend fun acceptSuggestion(pair: MergeSuggestion.BetweenRows, isKnown: Boolean?): Boolean = false
    override suspend fun rejectSuggestion(pair: MergeSuggestion.BetweenRows): Boolean = false
}
