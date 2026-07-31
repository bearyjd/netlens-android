package com.ventouxlabs.netlens.feature.history

import com.ventouxlabs.netlens.core.data.model.DnsHistoryEntry
import com.ventouxlabs.netlens.core.data.model.HistoryDetailData
import com.ventouxlabs.netlens.core.data.model.PingHistoryEntry
import com.ventouxlabs.netlens.core.data.repository.CombinedHistoryResults
import com.ventouxlabs.netlens.feature.history.model.HistoryDetailState
import com.ventouxlabs.netlens.feature.history.model.ToolFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private lateinit var repo: FakeHistoryRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeHistoryRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ping(id: Long, host: String, timestamp: Long) = PingHistoryEntry(
        id = id,
        timestamp = timestamp,
        host = host,
        sentCount = 4,
        receivedCount = 4,
        minMs = 1f,
        avgMs = 2f,
        maxMs = 3f,
    )

    private fun dns(id: Long, query: String, timestamp: Long) =
        DnsHistoryEntry(id = id, timestamp = timestamp, query = query, recordType = "A", resultsJson = "[]")

    @Test
    fun `history loads on construction and stops loading`() = runTest {
        repo.recent.value = CombinedHistoryResults(pings = listOf(ping(1, "example.com", 100)))

        val vm = HistoryViewModel(repo)

        assertFalse(vm.state.value.isLoading)
        assertEquals(listOf("example.com"), vm.state.value.items.map { it.primaryLabel })
        assertNull(vm.state.value.error)
    }

    @Test
    fun `items come back newest first regardless of which tool produced them`() = runTest {
        repo.recent.value = CombinedHistoryResults(
            pings = listOf(ping(1, "old-ping", 100), ping(2, "new-ping", 400)),
            dnsLookups = listOf(dns(1, "middle-dns", 200)),
        )

        val vm = HistoryViewModel(repo)

        assertEquals(
            listOf("new-ping", "middle-dns", "old-ping"),
            vm.state.value.items.map { it.primaryLabel },
        )
    }

    @Test
    fun `the list is silently capped at 100 items`() = runTest {
        // Not a hypothetical bound: allRecent asks each of 11 DAOs for 50 rows, so 550 can arrive
        // and 450 are dropped with nothing in the UI to say so. Pinned because a change to the
        // cap should be a decision, not an accident.
        repo.recent.value = CombinedHistoryResults(
            pings = (1..150).map { ping(it.toLong(), "host$it", it.toLong()) },
        )

        val vm = HistoryViewModel(repo)

        assertEquals(100, vm.state.value.items.size)
        // Kept the newest, not the first 100 encountered.
        assertEquals("host150", vm.state.value.items.first().primaryLabel)
        assertEquals("host51", vm.state.value.items.last().primaryLabel)
    }

    @Test
    fun `a blank query reads recents and a real query searches`() = runTest {
        val vm = HistoryViewModel(repo)
        assertEquals(1, repo.allRecentCalls)
        assertTrue(repo.searchCalls.isEmpty())

        vm.onSearchQueryChanged("nas")
        assertEquals(listOf("nas"), repo.searchCalls)

        // Whitespace is trimmed before the decision, so it is still "blank".
        vm.onSearchQueryChanged("   ")
        assertEquals(listOf("nas"), repo.searchCalls)
        assertEquals(2, repo.allRecentCalls)
    }

    @Test
    fun `search results replace the list`() = runTest {
        // Asserting searchAll was *called* proves routing, not wiring. This proves what comes back
        // from it actually reaches the UI — a different failure, and the one a user would see.
        repo.recent.value = CombinedHistoryResults(pings = listOf(ping(1, "from-recents", 100)))
        val vm = HistoryViewModel(repo)

        vm.onSearchQueryChanged("nas")
        repo.searched.emit(CombinedHistoryResults(pings = listOf(ping(9, "nas.local", 500))))

        assertEquals(listOf("nas.local"), vm.state.value.items.map { it.primaryLabel })
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `a query that fails part-way surfaces an error instead of spinning forever`() = runTest {
        // Fails after its first emission, like a Room flow whose query throws once the screen is
        // already open. isLoading must come down, or the UI spins with no explanation.
        repo.recent.value = CombinedHistoryResults(pings = listOf(ping(1, "example.com", 100)))
        repo.failAfterFirstEmission = IllegalStateException("database is locked")

        val vm = HistoryViewModel(repo)

        assertFalse(vm.state.value.isLoading)
        assertEquals("database is locked", vm.state.value.error)
    }

    @Test
    fun `an error with no message still says something`() = runTest {
        repo.failAfterFirstEmission = IllegalStateException()

        val vm = HistoryViewModel(repo)

        assertEquals("Failed to load history", vm.state.value.error)
    }

    @Test
    fun `the raw query is kept in state even when it is only whitespace`() = runTest {
        // What the user typed drives the text field; the trim only decides which query runs.
        val vm = HistoryViewModel(repo)

        vm.onSearchQueryChanged("  ")

        assertEquals("  ", vm.state.value.searchQuery)
    }

    @Test
    fun `selecting a filter narrows the list without going back to the repository`() = runTest {
        repo.recent.value = CombinedHistoryResults(
            pings = listOf(ping(1, "a-ping", 200)),
            dnsLookups = listOf(dns(1, "a-dns", 100)),
        )
        val vm = HistoryViewModel(repo)
        val callsBefore = repo.allRecentCalls

        vm.onFilterSelected(ToolFilter.Dns)

        assertEquals(listOf("a-dns"), vm.state.value.items.map { it.primaryLabel })
        assertEquals(callsBefore, repo.allRecentCalls, "filtering is local; it must not re-query")
    }

    @Test
    fun `an active filter survives new data arriving`() = runTest {
        repo.recent.value = CombinedHistoryResults(pings = listOf(ping(1, "a-ping", 200)))
        val vm = HistoryViewModel(repo)
        vm.onFilterSelected(ToolFilter.Dns)
        assertTrue(vm.state.value.items.isEmpty())

        repo.recent.value = CombinedHistoryResults(
            pings = listOf(ping(2, "b-ping", 300)),
            dnsLookups = listOf(dns(1, "b-dns", 250)),
        )

        assertEquals(listOf("b-dns"), vm.state.value.items.map { it.primaryLabel })
    }

    @Test
    fun `clearing the filter brings everything back`() = runTest {
        repo.recent.value = CombinedHistoryResults(
            pings = listOf(ping(1, "a-ping", 200)),
            dnsLookups = listOf(dns(1, "a-dns", 100)),
        )
        val vm = HistoryViewModel(repo)

        vm.onFilterSelected(ToolFilter.Dns)
        vm.onFilterSelected(ToolFilter.All)

        assertEquals(listOf("a-ping", "a-dns"), vm.state.value.items.map { it.primaryLabel })
    }

    @Test
    fun `clearAll delegates to the repository`() = runTest {
        val vm = HistoryViewModel(repo)

        vm.clearAll()

        assertEquals(1, repo.clearAllCalls)
    }

    @Test
    fun `selecting an entry loads its detail`() = runTest {
        val entry = ping(7, "example.com", 100)
        repo.recent.value = CombinedHistoryResults(pings = listOf(entry))
        repo.entry = HistoryDetailData.Ping(entry)
        val vm = HistoryViewModel(repo)

        vm.selectEntry(vm.state.value.items.first())

        val detail = vm.detailState.value
        assertTrue(detail is HistoryDetailState.Loaded, "expected Loaded, got $detail")
        assertEquals(listOf("Ping" to 7L), repo.getEntryCalls.map { it.first to it.second })
    }

    @Test
    fun `an entry deleted underneath the list reports an error rather than a blank sheet`() =
        runTest {
            repo.recent.value = CombinedHistoryResults(pings = listOf(ping(7, "example.com", 100)))
            repo.entry = null
            val vm = HistoryViewModel(repo)

            vm.selectEntry(vm.state.value.items.first())

            assertTrue(vm.detailState.value is HistoryDetailState.Error)
        }

    @Test
    fun `dismissing the detail clears it`() = runTest {
        val entry = ping(7, "example.com", 100)
        repo.recent.value = CombinedHistoryResults(pings = listOf(entry))
        repo.entry = HistoryDetailData.Ping(entry)
        val vm = HistoryViewModel(repo)
        vm.selectEntry(vm.state.value.items.first())

        vm.dismissDetail()

        assertNull(vm.detailState.value)
    }

    @Test
    fun `selectEntry is keyed on the tool, so two tools sharing a row id do not collide`() =
        runTest {
            // Every history table has its own autoincrement, so id 1 exists in several of them.
            // getEntry takes the tool name precisely to disambiguate; this pins that the ViewModel
            // actually passes it. The same shape of assumption caused the #116 duplicate-key crash.
            repo.recent.value = CombinedHistoryResults(
                pings = listOf(ping(1, "a-ping", 200)),
                dnsLookups = listOf(dns(1, "a-dns", 100)),
            )
            val vm = HistoryViewModel(repo)

            vm.selectEntry(vm.state.value.items.first { it.toolFilter == ToolFilter.Dns })

            assertEquals(listOf("Dns" to 1L), repo.getEntryCalls)
        }
}
