package com.ventouxlabs.netlens.feature.ping

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.ventouxlabs.netlens.core.data.dao.PingHistoryDao
import com.ventouxlabs.netlens.core.data.model.PingHistoryEntry
import com.ventouxlabs.netlens.feature.ping.engine.FakePinger
import com.ventouxlabs.netlens.feature.ping.model.PingMode
import com.ventouxlabs.netlens.feature.ping.model.PingResult
import com.ventouxlabs.netlens.feature.ping.model.PingUiState
import com.ventouxlabs.netlens.feature.ping.service.PingServiceController

@OptIn(ExperimentalCoroutinesApi::class)
class PingViewModelTest {

    private lateinit var fakePinger: FakePinger
    private lateinit var viewModel: PingViewModel
    private lateinit var fakePingHistoryDao: FakePingHistoryDao
    private lateinit var fakeServiceController: FakePingServiceController

    private class FakePingHistoryDao : PingHistoryDao {
        val inserted = mutableListOf<PingHistoryEntry>()
        override fun getRecent(limit: Int): Flow<List<PingHistoryEntry>> = flowOf(emptyList())
        override fun search(query: String, limit: Int): Flow<List<PingHistoryEntry>> = flowOf(emptyList())
        override suspend fun getById(id: Long): PingHistoryEntry? = null
        override suspend fun insert(entry: PingHistoryEntry) { inserted.add(entry) }
        override suspend fun deleteById(id: Long) {}
        override suspend fun deleteOlderThan(before: Long) {}
        override suspend fun deleteAll() {}
    }

    private class FakePingServiceController : PingServiceController {
        private val _stopRequested = MutableStateFlow(false)
        override val stopRequested: StateFlow<Boolean> = _stopRequested
        var startCalled = false
        var stopCalled = false
        var lastNotificationSent = 0
        var lastNotificationLoss = -1f
        override fun start(host: String) { startCalled = true }
        override fun stop() { stopCalled = true }
        override fun requestStop() { _stopRequested.value = true }
        override fun updateNotification(host: String, sent: Int, lossPercent: Float) {
            lastNotificationSent = sent
            lastNotificationLoss = lossPercent
        }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakePinger = FakePinger()
        fakePingHistoryDao = FakePingHistoryDao()
        fakeServiceController = FakePingServiceController()
        viewModel = PingViewModel(fakePinger, fakePingHistoryDao, fakeServiceController)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has defaults`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(PingUiState(), state)
            assertEquals("", state.host)
            assertEquals(emptyList<PingResult>(), state.results)
            assertNull(state.summary)
            assertFalse(state.isPinging)
            assertNull(state.error)
            assertEquals(PingMode.FIXED, state.mode)
        }
    }

    @Test
    fun `onHostChange updates host`() = runTest {
        viewModel.state.test {
            awaitItem()

            viewModel.onHostChange("8.8.8.8")
            assertEquals("8.8.8.8", awaitItem().host)

            viewModel.onHostChange("1.1.1.1")
            assertEquals("1.1.1.1", awaitItem().host)
        }
    }

    @Test
    fun `startPing collects results from flow and computes summary`() = runTest {
        val result1 = PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64, ip = "8.8.8.8")
        val result2 = PingResult(sequenceNumber = 2, latencyMs = 12.0f, ttl = 64, ip = "8.8.8.8")
        val result3 = PingResult(sequenceNumber = 3, latencyMs = 8.0f, ttl = 64, ip = "8.8.8.8")
        fakePinger.results = listOf(result1, result2, result3)

        viewModel.state.test {
            awaitItem()

            viewModel.startPing("8.8.8.8", 3)

            val finalState = expectMostRecentItem()

            assertFalse(finalState.isPinging)
            assertEquals(3, finalState.results.size)
            assertEquals(result1, finalState.results[0])
            assertEquals(result2, finalState.results[1])
            assertEquals(result3, finalState.results[2])
            assertNull(finalState.error)

            val summary = finalState.summary
            assertNotNull(summary)
            assertEquals(3, summary!!.transmitted)
            assertEquals(3, summary.received)
            assertEquals(0f, summary.lossPercent)
            assertEquals(8.0f, summary.minMs)
            assertEquals(12.0f, summary.maxMs)
        }
    }

    @Test
    fun `startPing with error shows error and clears isPinging`() = runTest {
        fakePinger.error = RuntimeException("Network unreachable")

        viewModel.state.test {
            awaitItem()

            viewModel.startPing("bad.host", 4)

            val finalState = expectMostRecentItem()
            assertFalse(finalState.isPinging)
            assertEquals("Network unreachable", finalState.error)
            assertTrue(finalState.results.isEmpty())
        }
    }

    @Test
    fun `stopPing sets isPinging to false and computes summary`() = runTest {
        val result1 = PingResult(sequenceNumber = 1, latencyMs = 15.0f, ttl = 64, ip = "8.8.8.8")
        fakePinger.results = listOf(result1)

        viewModel.startPing("8.8.8.8", 4)

        assertFalse(viewModel.state.value.isPinging)
        assertNotNull(viewModel.state.value.summary)

        viewModel.stopPing()
        assertFalse(viewModel.state.value.isPinging)
        assertNotNull(viewModel.state.value.summary)
    }

    @Test
    fun `startPing resets previous results and error`() = runTest {
        fakePinger.error = RuntimeException("First failure")

        viewModel.state.test {
            awaitItem()

            viewModel.startPing("8.8.8.8", 4)
            expectMostRecentItem()

            fakePinger.error = null
            fakePinger.results = listOf(
                PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
            )

            viewModel.startPing("8.8.8.8", 1)

            val finalState = expectMostRecentItem()
            assertNull(finalState.error)
            assertEquals(1, finalState.results.size)
        }
    }

    @Test
    fun `startPing with timeout results computes loss percentage`() = runTest {
        fakePinger.results = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
            PingResult(sequenceNumber = 2, isTimeout = true),
            PingResult(sequenceNumber = 3, latencyMs = 12.0f, ttl = 64),
        )

        viewModel.state.test {
            awaitItem()

            viewModel.startPing("8.8.8.8", 3)

            val finalState = expectMostRecentItem()
            val summary = finalState.summary
            assertNotNull(summary)
            assertEquals(3, summary!!.transmitted)
            assertEquals(2, summary.received)
            assertTrue(summary.lossPercent > 33f)
            assertTrue(summary.lossPercent < 34f)
        }
    }

    @Test
    fun `onModeChanged switches mode`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.onModeChanged(PingMode.CONTINUOUS)
            assertEquals(PingMode.CONTINUOUS, awaitItem().mode)
        }
    }

    @Test
    fun `onModeChanged ignored while pinging`() = runTest {
        fakePinger.results = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
        )
        viewModel.startPing("8.8.8.8", 4)
        assertEquals(PingMode.FIXED, viewModel.state.value.mode)
    }

    @Test
    fun `continuous mode uses rolling buffer`() = runTest {
        viewModel.onModeChanged(PingMode.CONTINUOUS)
        val manyResults = (1..150).map {
            PingResult(sequenceNumber = it, latencyMs = it.toFloat(), ttl = 64)
        }
        fakePinger.continuousResults = manyResults

        viewModel.state.test {
            awaitItem() // current state (mode=CONTINUOUS)

            viewModel.startPing("8.8.8.8", 0)
            val finalState = expectMostRecentItem()

            assertEquals(100, finalState.results.size)
            assertEquals(51, finalState.results.first().sequenceNumber)
            assertEquals(150, finalState.results.last().sequenceNumber)
            assertEquals(150, finalState.totalSent)
            assertEquals(150, finalState.totalReceived)
        }
    }

    @Test
    fun `continuous mode computes live summary each packet`() = runTest {
        viewModel.onModeChanged(PingMode.CONTINUOUS)
        fakePinger.continuousResults = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
            PingResult(sequenceNumber = 2, latencyMs = 20.0f, ttl = 64),
            PingResult(sequenceNumber = 3, isTimeout = true),
        )

        viewModel.state.test {
            awaitItem() // current state (mode=CONTINUOUS)

            viewModel.startPing("8.8.8.8", 0)
            val finalState = expectMostRecentItem()

            assertNotNull(finalState.summary)
            assertEquals(3, finalState.totalSent)
            assertEquals(2, finalState.totalReceived)
        }
    }

    @Test
    fun `continuous mode does not save with zero packets`() = runTest {
        viewModel.onModeChanged(PingMode.CONTINUOUS)
        fakePinger.continuousResults = emptyList()

        viewModel.startPing("8.8.8.8", 0)
        viewModel.stopPing()

        assertTrue(fakePingHistoryDao.inserted.isEmpty())
    }

    @Test
    fun `fixed mode saves history with FIXED mode tag`() = runTest {
        fakePinger.results = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
        )

        viewModel.startPing("8.8.8.8", 1)

        assertEquals(1, fakePingHistoryDao.inserted.size)
        assertEquals("FIXED", fakePingHistoryDao.inserted[0].mode)
    }

    @Test
    fun `continuous mode saves history with CONTINUOUS mode tag`() = runTest {
        viewModel.onModeChanged(PingMode.CONTINUOUS)
        fakePinger.continuousResults = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
            PingResult(sequenceNumber = 2, latencyMs = 12.0f, ttl = 64),
        )

        viewModel.startPing("8.8.8.8", 0)

        assertEquals(1, fakePingHistoryDao.inserted.size)
        assertEquals("CONTINUOUS", fakePingHistoryDao.inserted[0].mode)
        assertEquals(2, fakePingHistoryDao.inserted[0].sentCount)
        assertEquals(2, fakePingHistoryDao.inserted[0].receivedCount)
    }

    @Test
    fun `continuous mode starts service`() = runTest {
        viewModel.onModeChanged(PingMode.CONTINUOUS)
        fakePinger.continuousResults = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
        )

        viewModel.startPing("8.8.8.8", 0)

        assertTrue(fakeServiceController.startCalled)
    }

    @Test
    fun `continuous mode stops service on completion`() = runTest {
        viewModel.onModeChanged(PingMode.CONTINUOUS)
        fakePinger.continuousResults = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
        )

        viewModel.startPing("8.8.8.8", 0)

        assertTrue(fakeServiceController.stopCalled)
    }

    @Test
    fun `fixed mode does not start service`() = runTest {
        fakePinger.results = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
        )

        viewModel.startPing("8.8.8.8", 1)

        assertFalse(fakeServiceController.startCalled)
    }

    @Test
    fun `continuous mode updates notification`() = runTest {
        viewModel.onModeChanged(PingMode.CONTINUOUS)
        fakePinger.continuousResults = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
            PingResult(sequenceNumber = 2, latencyMs = 12.0f, ttl = 64),
        )

        viewModel.startPing("8.8.8.8", 0)

        assertEquals(2, fakeServiceController.lastNotificationSent)
    }

    @Test
    fun `continuous mode uses cumulative stats for summary`() = runTest {
        viewModel.onModeChanged(PingMode.CONTINUOUS)
        val manyResults = (1..150).map {
            PingResult(sequenceNumber = it, latencyMs = it.toFloat(), ttl = 64)
        }
        fakePinger.continuousResults = manyResults

        viewModel.state.test {
            awaitItem()

            viewModel.startPing("8.8.8.8", 0)
            val finalState = expectMostRecentItem()
            val summary = checkNotNull(finalState.summary)

            assertEquals(1.0f, summary.minMs)
            assertEquals(150.0f, summary.maxMs)
            assertEquals(150, summary.transmitted)
            assertEquals(150, summary.received)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Timeout visibility: Android's ping prints NOTHING for a lost packet, so every case below
    // is one the parser alone can never surface — the ViewModel has to derive the loss.
    // ------------------------------------------------------------------------------------------

    // The original complaint: a dead host in fixed mode used to end with an empty list and no
    // summary — the screen showed nothing at all.
    @Test
    fun `a dead host in fixed mode shows every packet as a timeout`() = runTest {
        fakePinger.results = emptyList()

        viewModel.startPing("10.255.255.1", 4)

        val state = viewModel.state.value
        assertEquals(4, state.results.size)
        assertTrue(state.results.all { it.isTimeout })
        assertEquals(listOf(1, 2, 3, 4), state.results.map { it.sequenceNumber })
        assertEquals(4, state.totalSent)
        assertEquals(0, state.totalReceived)
        assertEquals(100f, state.summary?.lossPercent)
        assertFalse(state.isPinging)
    }

    @Test
    fun `a skipped sequence number appears as a timeout row between its neighbours`() = runTest {
        fakePinger.results = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10f, ttl = 56, ip = "1.1.1.1"),
            PingResult(sequenceNumber = 3, latencyMs = 12f, ttl = 56, ip = "1.1.1.1"),
        )

        viewModel.startPing("example.com", 3)

        val state = viewModel.state.value
        assertEquals(listOf(1, 2, 3), state.results.map { it.sequenceNumber })
        assertTrue(state.results[1].isTimeout)
        assertEquals(3, state.totalSent)
        assertEquals(2, state.totalReceived)
    }

    // PingScreen keys its list on sequenceNumber; a duplicate row here would be the repo's
    // known LazyColumn duplicate-key crash. The late reply must replace, not append.
    @Test
    fun `a late reply upgrades its synthetic timeout row without duplicating the key`() = runTest {
        fakePinger.results = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10f, ttl = 56, ip = "1.1.1.1"),
            PingResult(sequenceNumber = 4, latencyMs = 11f, ttl = 56, ip = "1.1.1.1"),
            PingResult(sequenceNumber = 2, latencyMs = 900f, ttl = 56, ip = "1.1.1.1"),
        )

        viewModel.startPing("example.com", 4)

        val state = viewModel.state.value
        assertEquals(listOf(1, 2, 3, 4), state.results.map { it.sequenceNumber })
        assertEquals(state.results.size, state.results.map { it.sequenceNumber }.distinct().size)
        assertEquals(900f, state.results[1].latencyMs)
        assertTrue(state.results[2].isTimeout)
        assertEquals(4, state.totalSent)
        assertEquals(3, state.totalReceived)
    }

    // Total silence with the stream still open — no later reply ever reveals the gap, so only
    // the watchdog can paint it. Virtual time: first synthetic row after 3s, then 1/s.
    @Test
    fun `continuous silence paints watchdog timeout rows`() = runTest {
        try {
            fakePinger.holdOpen = true
            fakePinger.continuousResults = emptyList()
            viewModel.onModeChanged(PingMode.CONTINUOUS)

            viewModel.startPing("10.255.255.1", 0)
            advanceTimeBy(3_001)
            assertEquals(1, viewModel.state.value.results.size)

            advanceTimeBy(2_000)
            val state = viewModel.state.value
            assertEquals(3, state.results.size)
            assertTrue(state.results.all { it.isTimeout })
            assertEquals(listOf(1, 2, 3), state.results.map { it.sequenceNumber })
            assertEquals(3, state.totalSent)
            assertEquals(100f, state.summary?.lossPercent)
            // Watchdog synthetics drive the foreground notification too — a dead host must
            // show 100% loss there, not a frozen last-known value.
            assertEquals(3, fakeServiceController.lastNotificationSent)
            assertEquals(100f, fakeServiceController.lastNotificationLoss)
        } finally {
            // A failed assertion must still stop the watchdog — its self-perpetuating
            // delay loop otherwise hangs runTest's advance-until-idle cleanup forever.
            viewModel.stopPing()
        }
    }

    // A real reply restarts the silence clock. The reply is delayed to t=1500 so the two
    // behaviours diverge: with the reset, the next synthetic row lands at t=4500; without it,
    // the watchdog started at t=0 would fire at t=3000 — which the mid-window assertion catches.
    @Test
    fun `a reply resets the watchdog silence window`() = runTest {
        try {
            fakePinger.holdOpen = true
            fakePinger.emitDelayMs = 1_500
            fakePinger.continuousResults = listOf(
                PingResult(sequenceNumber = 1, latencyMs = 10f, ttl = 56, ip = "1.1.1.1"),
            )
            viewModel.onModeChanged(PingMode.CONTINUOUS)

            viewModel.startPing("example.com", 0)
            advanceTimeBy(3_100)
            // t=3100: an un-reset watchdog would have fired at t=3000; the reset one waits to 4500.
            assertEquals(1, viewModel.state.value.results.size)

            advanceTimeBy(1_500)
            val state = viewModel.state.value
            assertEquals(2, state.results.size)
            assertTrue(state.results[1].isTimeout)
        } finally {
            // A failed assertion must still stop the watchdog — its self-perpetuating
            // delay loop otherwise hangs runTest's advance-until-idle cleanup forever.
            viewModel.stopPing()
        }
    }

    // A hung fixed run paints progressively but never invents more packets than were asked for.
    @Test
    fun `watchdog rows stop at the fixed count`() = runTest {
        try {
            fakePinger.holdOpen = true
            fakePinger.results = emptyList()

            viewModel.startPing("10.255.255.1", 2)
            advanceTimeBy(10_000)

            assertEquals(2, viewModel.state.value.results.size)
            assertTrue(viewModel.state.value.results.all { it.isTimeout })
        } finally {
            // A failed assertion must still stop the watchdog — its self-perpetuating
            // delay loop otherwise hangs runTest's advance-until-idle cleanup forever.
            viewModel.stopPing()
        }
    }

    // Stopping is not losing: the un-pinged remainder of a cancelled fixed run was never sent,
    // so nothing may be fabricated for it.
    @Test
    fun `cancelling a fixed run does not fabricate trailing timeouts`() = runTest {
        fakePinger.holdOpen = true
        fakePinger.results = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10f, ttl = 56, ip = "1.1.1.1"),
        )

        viewModel.startPing("example.com", 4)
        viewModel.stopPing()

        assertEquals(1, viewModel.state.value.results.size)
        assertEquals(1, viewModel.state.value.totalSent)
    }

    // Some ping variants DO print a per-packet failure line ("no answer", "Destination Host
    // Unreachable"), which the parser emits as a real isTimeout result. If the watchdog already
    // painted that seq, the parser's copy must be dropped — one lost packet, one row, once.
    @Test
    fun `a parser timeout for a watchdog-painted seq does not double count`() = runTest {
        fakePinger.holdOpen = true
        fakePinger.emitDelayMs = 3_500
        fakePinger.continuousResults = listOf(
            PingResult(sequenceNumber = 1, isTimeout = true),
        )
        viewModel.onModeChanged(PingMode.CONTINUOUS)

        try {
            viewModel.startPing("10.255.255.1", 0)
            advanceTimeBy(3_100)
            // Watchdog painted seq 1 at t=3000.
            assertEquals(1, viewModel.state.value.results.size)

            advanceTimeBy(500)
            // t=3600: the parser's own timeout for seq 1 arrived at t=3500 — and changed nothing.
            val state = viewModel.state.value
            assertEquals(1, state.results.count { it.sequenceNumber == 1 })
            assertEquals(1, state.totalSent)
            assertEquals(0, state.totalReceived)
        } finally {
            viewModel.stopPing()
        }
    }

    // A duplicated reply — ping prints "(DUP!)" lines the unanchored reply regex still parses —
    // is dropped by reconcile, and its latency must not leak into the cumulative min/max that
    // feed the live summary and ping history.
    @Test
    fun `a dropped duplicate reply does not pollute the cumulative stats`() = runTest {
        fakePinger.continuousResults = listOf(
            PingResult(sequenceNumber = 1, latencyMs = 10f, ttl = 56, ip = "1.1.1.1"),
            PingResult(sequenceNumber = 1, latencyMs = 9_000f, ttl = 56, ip = "1.1.1.1"),
            PingResult(sequenceNumber = 2, latencyMs = 20f, ttl = 56, ip = "1.1.1.1"),
        )
        viewModel.onModeChanged(PingMode.CONTINUOUS)

        viewModel.startPing("example.com", 0)

        val summary = checkNotNull(viewModel.state.value.summary)
        assertEquals(20f, summary.maxMs)
        assertEquals(2, summary.transmitted)
        assertEquals(2, summary.received)
    }

    // Continuous mode keeps a 100-row window; crossing it must not confuse the gap logic or
    // the counters, and the window must hold the LAST 100 rows.
    @Test
    fun `crossing the rolling buffer keeps the last 100 rows and exact totals`() = runTest {
        fakePinger.continuousResults = (1..150).map {
            PingResult(sequenceNumber = it, latencyMs = it.toFloat(), ttl = 56, ip = "1.1.1.1")
        }
        viewModel.onModeChanged(PingMode.CONTINUOUS)

        viewModel.startPing("example.com", 0)

        val state = viewModel.state.value
        assertEquals(100, state.results.size)
        assertEquals((51..150).toList(), state.results.map { it.sequenceNumber })
        assertEquals(150, state.totalSent)
        assertEquals(150, state.totalReceived)
    }
}
