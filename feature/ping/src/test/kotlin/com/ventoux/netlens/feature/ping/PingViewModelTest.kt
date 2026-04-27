package com.ventoux.netlens.feature.ping

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import com.ventoux.netlens.core.data.dao.PingHistoryDao
import com.ventoux.netlens.core.data.model.PingHistoryEntry
import com.ventoux.netlens.feature.ping.engine.FakePinger
import com.ventoux.netlens.feature.ping.model.PingMode
import com.ventoux.netlens.feature.ping.model.PingResult
import com.ventoux.netlens.feature.ping.model.PingUiState
import com.ventoux.netlens.feature.ping.service.PingServiceController

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
        override fun start(host: String) { startCalled = true }
        override fun stop() { stopCalled = true }
        override fun requestStop() { _stopRequested.value = true }
        override fun updateNotification(host: String, sent: Int, lossPercent: Float) {
            lastNotificationSent = sent
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
}
