package com.ventouxlabs.netlens.feature.wifiaudit

import com.ventouxlabs.netlens.core.data.dao.NetworkEventDao
import com.ventouxlabs.netlens.core.data.model.NetworkEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeNetworkEventDao : NetworkEventDao {

    val inserted = mutableListOf<NetworkEvent>()

    override fun getAll(): Flow<List<NetworkEvent>> = flowOf(inserted.toList())

    override fun getRecent(limit: Int): Flow<List<NetworkEvent>> = flowOf(inserted.take(limit))

    override fun getFiltered(
        types: Set<String>,
        hasTypeFilter: Int,
        from: Long?,
        to: Long?,
        limit: Int,
    ): Flow<List<NetworkEvent>> = flowOf(inserted.take(limit))

    override suspend fun insert(event: NetworkEvent) {
        inserted.add(event)
    }

    override suspend fun deleteOlderThan(before: Long) {
        inserted.removeAll { it.timestamp < before }
    }

    override suspend fun deleteAll() {
        inserted.clear()
    }
}
