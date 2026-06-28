package com.ventouxlabs.netlens.feature.netlog.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import com.ventouxlabs.netlens.core.data.dao.NetworkEventDao
import com.ventouxlabs.netlens.core.data.model.NetworkEvent

class FakeNetworkEventDao : NetworkEventDao {
    private val events = MutableStateFlow<List<NetworkEvent>>(emptyList())

    override fun getAll(): Flow<List<NetworkEvent>> = events

    override fun getRecent(limit: Int): Flow<List<NetworkEvent>> =
        events.map { it.sortedByDescending { e -> e.timestamp }.take(limit) }

    override fun getFiltered(
        types: Set<String>,
        hasTypeFilter: Int,
        from: Long?,
        to: Long?,
        limit: Int,
    ): Flow<List<NetworkEvent>> = events.map { all ->
        all.asSequence()
            .filter { if (hasTypeFilter != 0) it.eventType in types else true }
            .filter { if (from != null) it.timestamp >= from else true }
            .filter { if (to != null) it.timestamp <= to else true }
            .sortedByDescending { it.timestamp }
            .take(limit)
            .toList()
    }

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
