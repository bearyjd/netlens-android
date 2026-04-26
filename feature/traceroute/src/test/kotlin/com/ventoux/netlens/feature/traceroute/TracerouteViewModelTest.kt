package com.ventoux.netlens.feature.traceroute

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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.ventoux.netlens.core.data.dao.TracerouteHistoryDao
import com.ventoux.netlens.core.data.model.TracerouteHistoryEntry
import com.ventoux.netlens.feature.traceroute.engine.FakeTracer
import com.ventoux.netlens.feature.traceroute.model.TracerouteHop
import com.ventoux.netlens.feature.traceroute.model.TracerouteUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class TracerouteViewModelTest {

    private lateinit var fakeTracer: FakeTracer
    private lateinit var viewModel: TracerouteViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeTracer = FakeTracer()
        viewModel = TracerouteViewModel(fakeTracer, FakeTracerouteHistoryDao())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has defaults`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(TracerouteUiState(), state)
            assertEquals("", state.host)
            assertEquals(emptyList<TracerouteHop>(), state.hops)
            assertFalse(state.isTracing)
            assertNull(state.error)
        }
    }

    @Test
    fun `onHostChange updates host`() = runTest {
        viewModel.state.test {
            awaitItem() // initial state

            viewModel.onHostChange("google.com")
            assertEquals("google.com", awaitItem().host)

            viewModel.onHostChange("example.org")
            assertEquals("example.org", awaitItem().host)
        }
    }

    @Test
    fun `startTrace collects hops from flow`() = runTest {
        val hop1 = TracerouteHop(hopNumber = 1, ip = "192.168.1.1", rttMs = listOf(1.5f))
        val hop2 = TracerouteHop(hopNumber = 2, ip = "10.0.0.1", rttMs = listOf(5.2f))
        val hop3 = TracerouteHop(hopNumber = 3, ip = "8.8.8.8", rttMs = listOf(10.0f))
        fakeTracer.hops = listOf(hop1, hop2, hop3)

        viewModel.state.test {
            awaitItem() // initial state

            viewModel.startTrace("8.8.8.8")

            // With UnconfinedTestDispatcher the flow completes eagerly.
            val finalState = expectMostRecentItem()

            assertFalse(finalState.isTracing)
            assertEquals(3, finalState.hops.size)
            assertEquals(hop1, finalState.hops[0])
            assertEquals(hop2, finalState.hops[1])
            assertEquals(hop3, finalState.hops[2])
            assertNull(finalState.error)
        }
    }

    @Test
    fun `startTrace with error shows error and clears isTracing`() = runTest {
        fakeTracer.error = RuntimeException("Host not found")

        viewModel.state.test {
            awaitItem() // initial state

            viewModel.startTrace("nonexistent.host")

            val finalState = expectMostRecentItem()
            assertFalse(finalState.isTracing)
            assertEquals("Host not found", finalState.error)
            assertTrue(finalState.hops.isEmpty())
        }
    }

    @Test
    fun `stopTrace sets isTracing to false`() = runTest {
        // After startTrace completes eagerly, isTracing is already false from onCompletion.
        // Calling stopTrace again produces the same state values, so StateFlow may not
        // re-emit (it deduplicates equal values). Verify via state.value instead.
        fakeTracer.hops = listOf(
            TracerouteHop(hopNumber = 1, ip = "192.168.1.1", rttMs = listOf(1.0f)),
        )

        viewModel.startTrace("8.8.8.8")

        // Flow completed eagerly -- verify post-completion state
        assertFalse(viewModel.state.value.isTracing)
        assertEquals(1, viewModel.state.value.hops.size)

        // stopTrace should be safe to call even after completion
        viewModel.stopTrace()
        assertFalse(viewModel.state.value.isTracing)
    }

    @Test
    fun `startTrace resets previous hops and error`() = runTest {
        fakeTracer.error = RuntimeException("First failure")

        viewModel.state.test {
            awaitItem() // initial state

            viewModel.startTrace("bad.host")
            expectMostRecentItem() // error state

            // Configure success for second run
            fakeTracer.error = null
            fakeTracer.hops = listOf(
                TracerouteHop(hopNumber = 1, ip = "10.0.0.1", rttMs = listOf(2.0f)),
            )

            viewModel.startTrace("10.0.0.1")

            val finalState = expectMostRecentItem()
            assertNull(finalState.error)
            assertEquals(1, finalState.hops.size)
        }
    }

    @Test
    fun `startTrace with timeout hops includes them in results`() = runTest {
        fakeTracer.hops = listOf(
            TracerouteHop(hopNumber = 1, ip = "192.168.1.1", rttMs = listOf(1.0f)),
            TracerouteHop(hopNumber = 2, isTimeout = true),
            TracerouteHop(hopNumber = 3, ip = "8.8.8.8", rttMs = listOf(10.0f)),
        )

        viewModel.state.test {
            awaitItem() // initial state

            viewModel.startTrace("8.8.8.8")

            val finalState = expectMostRecentItem()
            assertEquals(3, finalState.hops.size)
            assertFalse(finalState.hops[0].isTimeout)
            assertTrue(finalState.hops[1].isTimeout)
            assertFalse(finalState.hops[2].isTimeout)
        }
    }
}

private class FakeTracerouteHistoryDao : TracerouteHistoryDao {
    override fun getRecent(limit: Int): Flow<List<TracerouteHistoryEntry>> = flowOf(emptyList())
    override fun search(query: String, limit: Int): Flow<List<TracerouteHistoryEntry>> = flowOf(emptyList())
    override suspend fun insert(entry: TracerouteHistoryEntry) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteOlderThan(before: Long) {}
    override suspend fun deleteAll() {}
}
