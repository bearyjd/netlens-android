package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.dao.NetworkEventDao
import com.ventouxlabs.netlens.core.data.model.NetworkEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [NetworkEventDao] that actually applies the filters it is asked for.
 *
 * Two versions of this used to exist. `:feature:netlog`'s honoured the type, from/to and limit
 * arguments; `:feature:wifiaudit`'s returned `flowOf(inserted.take(limit))` from *every* query
 * method, ignoring the filters entirely. Nothing was broken by that yet — wifiaudit's tests only
 * assert on [inserted] — but it meant the first read-path test written there would have passed no
 * matter what the real `@Query` did. This is the strong behaviour, kept in one place.
 *
 * Note what this still does not verify: the real DAO is generated from Room `@Query` SQL, and this
 * reimplements the intended semantics in Kotlin. A test that passes here proves the *caller* asked
 * for the right thing, not that the SQL does it — see the `known_devices` write-path note in
 * `.agent_native/agent_roadmap.md` for the same limitation stated at length.
 */
class FakeNetworkEventDao : NetworkEventDao {

    private val events = MutableStateFlow<List<NetworkEvent>>(emptyList())

    /**
     * The rows currently held, in insertion order — **not** an append-only log of everything ever
     * written. [deleteOlderThan] and [deleteAll] remove from this too, which is what the copy in
     * `:feature:wifiaudit` did and what its tests assert. The name is kept for those call sites.
     */
    val inserted: List<NetworkEvent> get() = events.value

    // `ORDER BY timestamp DESC`, like the real query. Returning the backing list unsorted — which
    // is what both copies of this fake did — is insertion order, the *reverse* of what Room gives
    // back for a test that seeds ascending timestamps. Nothing calls getAll() today; that is
    // exactly the condition under which the wifiaudit copy's ignored filters survived.
    override fun getAll(): Flow<List<NetworkEvent>> =
        events.map { it.sortedByDescending { e -> e.timestamp } }

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
