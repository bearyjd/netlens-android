package com.ventouxlabs.netlens.feature.history

import com.ventouxlabs.netlens.core.data.model.HistoryDetailData
import com.ventouxlabs.netlens.core.data.repository.CombinedHistoryResults
import com.ventouxlabs.netlens.core.data.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-driven [HistoryRepository].
 *
 * Kept local to this module rather than pushed into `:core:data-testing`: there is exactly one
 * consumer, and a shared fixture built for a single caller is speculative. Move it if a second
 * consumer appears — a copy is what must not happen.
 *
 * [recent] and [searched] are separate on purpose. `HistoryViewModel` chooses between
 * `allRecent()` and `searchAll()` based on whether the trimmed query is blank, and a single
 * backing flow would let a test pass without proving which one was called.
 */
class FakeHistoryRepository : HistoryRepository {

    val recent = MutableStateFlow(CombinedHistoryResults())
    val searched = MutableSharedFlow<CombinedHistoryResults>(replay = 1)

    /** Every query string handed to [searchAll], in order. */
    val searchCalls = mutableListOf<String>()

    /** How many times [allRecent] was subscribed to, for asserting a filter did not re-query. */
    var allRecentCalls = 0
        private set

    var clearAllCalls = 0
        private set

    /** Returned by [getEntry]; null models "the row is gone". */
    var entry: HistoryDetailData? = null
    val getEntryCalls = mutableListOf<Pair<String, Long>>()

    override fun allRecent(limit: Int): Flow<CombinedHistoryResults> {
        allRecentCalls++
        return recent
    }

    override fun searchAll(query: String): Flow<CombinedHistoryResults> {
        searchCalls += query
        return searched
    }

    override suspend fun clearAll() {
        clearAllCalls++
    }

    override suspend fun clearOlderThan(days: Int) = Unit

    override suspend fun getEntry(toolFilter: String, id: Long): HistoryDetailData? {
        getEntryCalls += toolFilter to id
        return entry
    }
}
