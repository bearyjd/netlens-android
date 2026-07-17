package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWatchedNetworkDao : WatchedNetworkDao {
    val networks = mutableListOf<WatchedNetworkEntity>()
    private val flow = MutableStateFlow<List<WatchedNetworkEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<WatchedNetworkEntity>> = flow

    override suspend fun getByGatewayMac(mac: String): WatchedNetworkEntity? =
        networks.find { it.gatewayMac == mac }

    // Mirrors the real DAO's @Insert(onConflict = REPLACE) on the unique gatewayMac
    // index: re-upserting an existing network mints a NEW id, it does not preserve
    // the old one.
    override suspend fun upsert(network: WatchedNetworkEntity): Long {
        networks.removeAll { it.gatewayMac == network.gatewayMac }
        val id = nextId++
        networks.add(network.copy(id = id))
        flow.value = networks.toList()
        return id
    }

    override suspend fun setWatchEnabled(id: Long, enabled: Boolean) {
        val i = networks.indexOfFirst { it.id == id }
        if (i >= 0) { networks[i] = networks[i].copy(watchEnabled = enabled); flow.value = networks.toList() }
    }

    override suspend fun delete(id: Long) {
        networks.removeAll { it.id == id }
        flow.value = networks.toList()
    }
}
