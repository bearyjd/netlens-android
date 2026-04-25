package us.beary.netlens.feature.ping

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import us.beary.netlens.feature.ping.engine.FakePinger
import us.beary.netlens.feature.ping.model.PingResult
import us.beary.netlens.feature.ping.model.PingUiState

@OptIn(ExperimentalCoroutinesApi::class)
class PingViewModelTest {

    private lateinit var fakePinger: FakePinger
    private lateinit var viewModel: PingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakePinger = FakePinger()
        viewModel = PingViewModel(fakePinger)
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
        }
    }

    @Test
    fun `onHostChange updates host`() = runTest {
        viewModel.state.test {
            awaitItem() // initial state

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
            awaitItem() // initial state

            viewModel.startPing("8.8.8.8", 3)

            // With UnconfinedTestDispatcher the flow completes eagerly.
            // We may see intermediate states collapsed; find the final state.
            val finalState = expectMostRecentItem()

            assertFalse(finalState.isPinging)
            assertEquals(3, finalState.results.size)
            assertEquals(result1, finalState.results[0])
            assertEquals(result2, finalState.results[1])
            assertEquals(result3, finalState.results[2])
            assertNull(finalState.error)

            // Summary should be computed after flow completes
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
            awaitItem() // initial state

            viewModel.startPing("bad.host", 4)

            val finalState = expectMostRecentItem()
            assertFalse(finalState.isPinging)
            assertEquals("Network unreachable", finalState.error)
            assertTrue(finalState.results.isEmpty())
        }
    }

    @Test
    fun `stopPing sets isPinging to false and computes summary`() = runTest {
        // After startPing completes eagerly, isPinging is already false from onCompletion.
        // Calling stopPing again produces the same state values, so StateFlow may not
        // re-emit (it deduplicates equal values). Verify via state.value instead.
        val result1 = PingResult(sequenceNumber = 1, latencyMs = 15.0f, ttl = 64, ip = "8.8.8.8")
        fakePinger.results = listOf(result1)

        viewModel.startPing("8.8.8.8", 4)

        // Flow completed eagerly -- verify post-completion state
        assertFalse(viewModel.state.value.isPinging)
        assertNotNull(viewModel.state.value.summary)

        // stopPing should be safe to call even after completion
        viewModel.stopPing()
        assertFalse(viewModel.state.value.isPinging)
        assertNotNull(viewModel.state.value.summary)
    }

    @Test
    fun `startPing resets previous results and error`() = runTest {
        fakePinger.error = RuntimeException("First failure")

        viewModel.state.test {
            awaitItem() // initial state

            viewModel.startPing("8.8.8.8", 4)
            expectMostRecentItem() // error state

            // Now configure success for the second run
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
            awaitItem() // initial state

            viewModel.startPing("8.8.8.8", 3)

            val finalState = expectMostRecentItem()
            val summary = finalState.summary
            assertNotNull(summary)
            assertEquals(3, summary!!.transmitted)
            assertEquals(2, summary.received)
            // 1 out of 3 lost = ~33.33%
            assertTrue(summary.lossPercent > 33f)
            assertTrue(summary.lossPercent < 34f)
        }
    }
}
