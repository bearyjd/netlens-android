package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

class InMemoryKnownDeviceDao : KnownDeviceDao {
    val allDevices = mutableListOf<KnownDeviceEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<KnownDeviceEntity>>(emptyList())

    override fun getAllDevices(): Flow<List<KnownDeviceEntity>> = flow

    override suspend fun getByMac(mac: String): KnownDeviceEntity? =
        allDevices.find { it.macAddress == mac }

    override suspend fun getByIpWithoutMac(ip: String): KnownDeviceEntity? =
        allDevices.find { it.ip == ip && it.macAddress == null }

    override fun getUnknownDevices(): Flow<List<KnownDeviceEntity>> =
        flowOf(allDevices.filter { !it.isKnown })

    override suspend fun insertIfNew(device: KnownDeviceEntity): Long {
        if (device.macAddress != null && allDevices.any { it.macAddress == device.macAddress }) return -1L
        val withId = device.copy(id = nextId++)
        allDevices.add(withId)
        flow.update { allDevices.toList() }
        return withId.id
    }

    override suspend fun updateLastSeen(
        id: Long, hostname: String?, ip: String, vendor: String?,
        lastSeen: Long, deviceType: String?, osGuess: String?,
    ) {
        val i = allDevices.indexOfFirst { it.id == id }
        if (i >= 0) {
            allDevices[i] = allDevices[i].copy(
                hostname = hostname, ip = ip, vendor = vendor,
                lastSeen = lastSeen, deviceType = deviceType, osGuess = osGuess,
            )
            flow.update { allDevices.toList() }
        }
    }

    override suspend fun setMacAddress(id: Long, mac: String) {
        val i = allDevices.indexOfFirst { it.id == id }
        if (i >= 0) { allDevices[i] = allDevices[i].copy(macAddress = mac); flow.update { allDevices.toList() } }
    }

    override suspend fun setKnown(id: Long, isKnown: Boolean) {
        val i = allDevices.indexOfFirst { it.id == id }
        if (i >= 0) { allDevices[i] = allDevices[i].copy(isKnown = isKnown); flow.update { allDevices.toList() } }
    }

    override suspend fun setCustomName(id: Long, customName: String?) {
        val i = allDevices.indexOfFirst { it.id == id }
        if (i >= 0) { allDevices[i] = allDevices[i].copy(customName = customName); flow.update { allDevices.toList() } }
    }

    override suspend fun setNetworkId(id: Long, networkId: Long?) {
        val i = allDevices.indexOfFirst { it.id == id }
        if (i >= 0) { allDevices[i] = allDevices[i].copy(networkId = networkId); flow.update { allDevices.toList() } }
    }

    override fun search(query: String): Flow<List<KnownDeviceEntity>> =
        flowOf(allDevices.filter { it.hostname?.contains(query) == true || it.ip.contains(query) })

    override suspend fun delete(id: Long) { allDevices.removeAll { it.id == id }; flow.update { allDevices.toList() } }

    override suspend fun deleteAll() { allDevices.clear(); flow.update { emptyList() } }
}
