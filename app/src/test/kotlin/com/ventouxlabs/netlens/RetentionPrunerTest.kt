package com.ventouxlabs.netlens

import com.ventouxlabs.netlens.core.data.model.HistoryDetailData
import com.ventouxlabs.netlens.core.data.model.NetworkEvent
import com.ventouxlabs.netlens.core.data.repository.CombinedHistoryResults
import com.ventouxlabs.netlens.core.data.repository.HistoryRepository
import com.ventouxlabs.netlens.core.data.testing.FakeNetworkEventDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RetentionPrunerTest {

    private class FakeHistoryRepository(private val failure: Throwable? = null) : HistoryRepository {
        val clearOlderThanCalls = mutableListOf<Int>()
        override fun allRecent(limit: Int): Flow<CombinedHistoryResults> = emptyFlow()
        override fun searchAll(query: String): Flow<CombinedHistoryResults> = emptyFlow()
        override suspend fun clearAll() = Unit
        override suspend fun clearOlderThan(days: Int) {
            clearOlderThanCalls += days
            failure?.let { throw it }
        }
        override suspend fun getEntry(toolFilter: String, id: Long): HistoryDetailData? = null
    }

    private val now = 1_000L * 24 * 60 * 60 * 1000
    private val logged = mutableListOf<String>()
    private val log: (String, Throwable) -> Unit = { message, _ -> logged.add(message) }
    private val dayMs = 24L * 60 * 60 * 1000

    private fun event(timestamp: Long) = NetworkEvent(timestamp = timestamp, eventType = "test", transportType = "wifi", networkDetails = "")

    @Test
    fun `prunes history with the retention period`() = runBlocking {
        val repo = FakeHistoryRepository()
        RetentionPruner(repo, FakeNetworkEventDao(), { now }, log).prune()
        assertEquals(listOf(RetentionPruner.RETENTION_DAYS), repo.clearOlderThanCalls)
        assertEquals(90, RetentionPruner.RETENTION_DAYS)
    }

    @Test
    fun `deletes network events older than the cutoff and keeps the boundary`() = runBlocking {
        val dao = FakeNetworkEventDao()
        val cutoff = now - 90 * dayMs
        dao.insert(event(cutoff - 1))
        dao.insert(event(cutoff))
        dao.insert(event(now))
        RetentionPruner(FakeHistoryRepository(), dao, { now }, log).prune()
        assertEquals(listOf(cutoff, now), dao.inserted.map { it.timestamp })
    }

    @Test
    fun `a history failure does not stop network event pruning`() = runBlocking {
        val dao = FakeNetworkEventDao()
        dao.insert(event(0))
        RetentionPruner(FakeHistoryRepository(IllegalStateException("boom")), dao, { now }, log).prune()
        assertEquals(emptyList<NetworkEvent>(), dao.inserted)
        assertEquals(listOf("History retention prune failed"), logged)
    }

    @Test
    fun `a network event failure does not propagate`() = runBlocking {
        val repo = FakeHistoryRepository()
        val dao = object : com.ventouxlabs.netlens.core.data.dao.NetworkEventDao by FakeNetworkEventDao() {
            override suspend fun deleteOlderThan(before: Long) = throw IllegalStateException("boom")
        }
        RetentionPruner(repo, dao, { now }, log).prune()
        assertEquals(1, repo.clearOlderThanCalls.size)
        assertEquals(listOf("Network event retention prune failed"), logged)
    }
}
