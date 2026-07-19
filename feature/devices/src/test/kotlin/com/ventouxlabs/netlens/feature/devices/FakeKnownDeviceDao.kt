package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeKnownDeviceDao : KnownDeviceDao {
    private val devices = mutableListOf<KnownDeviceEntity>()
    private val flow = MutableStateFlow<List<KnownDeviceEntity>>(emptyList())

    fun seed(device: KnownDeviceEntity) { devices.add(device); flow.value = devices.toList() }
    fun byId(id: Long): KnownDeviceEntity? = devices.find { it.id == id }

    override fun getAllDevices(): Flow<List<KnownDeviceEntity>> = flow
    override suspend fun getByMac(mac: String): KnownDeviceEntity? = devices.find { it.macAddress == mac }
    override suspend fun getByIpWithoutMac(ip: String): KnownDeviceEntity? = devices.find { it.ip == ip && it.macAddress == null }
    override fun getUnknownDevices(): Flow<List<KnownDeviceEntity>> = flowOf(devices.filter { !it.isKnown })
    override suspend fun insertIfNew(device: KnownDeviceEntity): Long { devices.add(device); flow.value = devices.toList(); return device.id }
    override suspend fun updateLastSeen(id: Long, hostname: String?, ip: String, vendor: String?, lastSeen: Long, deviceType: String?, osGuess: String?) {}
    override suspend fun setMacAddress(id: Long, mac: String) {}
    override suspend fun setKnown(id: Long, isKnown: Boolean) { update(id) { it.copy(isKnown = isKnown) } }
    override suspend fun setCustomName(id: Long, customName: String?) { update(id) { it.copy(customName = customName) } }
    override suspend fun setNetworkId(id: Long, networkId: Long?) { update(id) { it.copy(networkId = networkId) } }
    override fun search(query: String): Flow<List<KnownDeviceEntity>> = flowOf(devices)
    override suspend fun delete(id: Long) { devices.removeAll { it.id == id }; flow.value = devices.toList() }
    override suspend fun deleteAll() { devices.clear(); flow.value = emptyList() }

    private fun update(id: Long, transform: (KnownDeviceEntity) -> KnownDeviceEntity) {
        val i = devices.indexOfFirst { it.id == id }
        if (i >= 0) { devices[i] = transform(devices[i]); flow.value = devices.toList() }
    }
}
