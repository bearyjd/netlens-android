package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWatchedNetworkDao : WatchedNetworkDao {
    val networks = mutableListOf<WatchedNetworkEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<WatchedNetworkEntity>>(emptyList())

    override fun observeAll(): Flow<List<WatchedNetworkEntity>> = flow

    override suspend fun getByGatewayMac(mac: String): WatchedNetworkEntity? =
        networks.find { it.gatewayMac == mac }

    override suspend fun upsert(network: WatchedNetworkEntity): Long {
        val existing = networks.indexOfFirst { it.gatewayMac == network.gatewayMac }
        return if (existing >= 0) {
            networks[existing] = network.copy(id = networks[existing].id)
            flow.value = networks.toList()
            networks[existing].id
        } else {
            val withId = network.copy(id = nextId++)
            networks.add(withId)
            flow.value = networks.toList()
            withId.id
        }
    }

    override suspend fun setWatchEnabled(id: Long, enabled: Boolean) {
        val i = networks.indexOfFirst { it.id == id }
        if (i >= 0) { networks[i] = networks[i].copy(watchEnabled = enabled); flow.value = networks.toList() }
    }

    override suspend fun delete(id: Long) { networks.removeAll { it.id == id }; flow.value = networks.toList() }
}
