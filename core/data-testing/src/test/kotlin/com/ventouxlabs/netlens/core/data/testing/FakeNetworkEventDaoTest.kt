package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.model.NetworkEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `:feature:wifiaudit` carried a copy of this fake whose every query method returned
 * `flowOf(inserted.take(limit))` — the type, from and to arguments were accepted and thrown away.
 * Its own tests never noticed because they only assert on the write path, which is precisely why
 * it survived: a double that silently ignores its arguments fails no test until someone writes the
 * one it would have caught. These pin the filtering so the weak version cannot come back.
 */
class FakeNetworkEventDaoTest {

    private fun event(type: String, timestamp: Long) = NetworkEvent(
        timestamp = timestamp,
        eventType = type,
        transportType = "WIFI",
        networkDetails = "e$timestamp",
    )

    private suspend fun seed() = FakeNetworkEventDao().apply {
        insert(event("WIFI", 100))
        insert(event("SECURITY_AUDIT", 200))
        insert(event("WIFI", 300))
    }

    @Test
    fun `getAll comes back newest first, not in insertion order`() = runTest {
        // Seeded ascending on purpose: the real query is ORDER BY timestamp DESC, so a fake that
        // returns its backing list untouched hands back the exact reverse. Both copies of this
        // fake did that, and it went unnoticed because getAll() has no callers yet.
        val dao = seed()

        assertEquals(listOf(300L, 200L, 100L), dao.getAll().first().map { it.timestamp })
    }

    @Test
    fun `a type filter actually filters`() = runTest {
        val dao = seed()

        val wifi = dao.getFiltered(setOf("WIFI"), hasTypeFilter = 1, from = null, to = null, limit = 10).first()

        assertEquals(listOf(300L, 100L), wifi.map { it.timestamp })
    }

    @Test
    fun `hasTypeFilter zero means no type filter at all, not an empty allowlist`() = runTest {
        val dao = seed()

        val all = dao.getFiltered(emptySet(), hasTypeFilter = 0, from = null, to = null, limit = 10).first()

        assertEquals(3, all.size)
    }

    @Test
    fun `the from and to bounds are inclusive and both applied`() = runTest {
        val dao = seed()

        val window = dao.getFiltered(emptySet(), hasTypeFilter = 0, from = 100, to = 200, limit = 10).first()

        assertEquals(listOf(200L, 100L), window.map { it.timestamp })
    }

    @Test
    fun `results come back newest first and honour the limit`() = runTest {
        val dao = seed()

        assertEquals(listOf(300L, 200L), dao.getRecent(limit = 2).first().map { it.timestamp })
        assertEquals(
            listOf(300L),
            dao.getFiltered(emptySet(), hasTypeFilter = 0, from = null, to = null, limit = 1)
                .first().map { it.timestamp },
        )
    }

    @Test
    fun `deleteOlderThan keeps the boundary row and inserted reflects it`() = runTest {
        val dao = seed()

        dao.deleteOlderThan(before = 200)

        assertEquals(listOf(200L, 300L), dao.inserted.map { it.timestamp })
    }
}
