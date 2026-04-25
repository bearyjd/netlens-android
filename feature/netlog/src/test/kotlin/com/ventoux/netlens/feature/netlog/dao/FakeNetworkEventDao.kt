package com.ventoux.netlens.feature.netlog.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import com.ventoux.netlens.core.data.dao.NetworkEventDao
import com.ventoux.netlens.core.data.model.NetworkEvent

class FakeNetworkEventDao : NetworkEventDao {
    private val events = MutableStateFlow<List<NetworkEvent>>(emptyList())

    override fun getAll(): Flow<List<NetworkEvent>> = events

    override fun getRecent(limit: Int): Flow<List<NetworkEvent>> =
        events.map { it.sortedByDescending { e -> e.timestamp }.take(limit) }

    override suspend fun insert(event: NetworkEvent) {
        events.value = events.value + event
    }

    override suspend fun deleteOlderThan(before: Long) {
        events.value = events.value.filter { it.timestamp >= before }
    }

    override suspend fun deleteAll() {
        events.value = emptyList()
    }
}
