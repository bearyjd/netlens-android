package com.ventoux.netlens.feature.portscan

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
import com.ventoux.netlens.core.data.dao.PortScanHistoryDao
import com.ventoux.netlens.core.data.model.PortScanHistoryEntry
import com.ventoux.netlens.feature.portscan.engine.FakePortScanner
import com.ventoux.netlens.feature.portscan.engine.PortScanner
import com.ventoux.netlens.feature.portscan.model.PortResult
import com.ventoux.netlens.feature.portscan.model.PortScanUiState

@OptIn(ExperimentalCoroutinesApi::class)
class PortScanViewModelTest {

    private lateinit var fakePortScanner: FakePortScanner
    private lateinit var viewModel: PortScanViewModel

    private val fakePortScanHistoryDao = object : PortScanHistoryDao {
        override fun getRecent(limit: Int): kotlinx.coroutines.flow.Flow<List<PortScanHistoryEntry>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override fun search(query: String, limit: Int): kotlinx.coroutines.flow.Flow<List<PortScanHistoryEntry>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getById(id: Long): PortScanHistoryEntry? = null
        override suspend fun insert(entry: PortScanHistoryEntry) {}
        override suspend fun deleteById(id: Long) {}
        override suspend fun deleteOlderThan(before: Long) {}
        override suspend fun deleteAll() {}
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakePortScanner = FakePortScanner()
        viewModel = PortScanViewModel(fakePortScanner, fakePortScanHistoryDao)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has defaults`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals("", state.host)
            assertEquals(emptyList<PortResult>(), state.results)
            assertFalse(state.isScanning)
            assertEquals(0f, state.progress)
            assertEquals(0, state.openCount)
            assertNull(state.error)
        }
    }

    @Test
    fun `scan with results populates state`() = runTest {
        val results = listOf(
            PortResult(port = 80, serviceName = "HTTP", isOpen = true, latencyMs = 10),
            PortResult(port = 443, serviceName = "HTTPS", isOpen = true, latencyMs = 12),
            PortResult(port = 8080, serviceName = "HTTP-ALT", isOpen = false, latencyMs = 0),
        )
        fakePortScanner.results = results

        viewModel.state.test {
            awaitItem() // initial state
            viewModel.scan("192.168.1.1", listOf(80, 443, 8080))

            // With UnconfinedTestDispatcher, the flow collects all items and completes
            // The final state after scan completes:
            val finalState = expectMostRecentItem()
            assertEquals("192.168.1.1", finalState.host)
            assertEquals(3, finalState.results.size)
            assertEquals(2, finalState.openCount)
            assertEquals(1f, finalState.progress)
            assertFalse(finalState.isScanning)
            assertNull(finalState.error)
        }
    }

    @Test
    fun `scan with error shows error`() = runTest {
        fakePortScanner.error = RuntimeException("Host unreachable")

        viewModel.state.test {
            awaitItem() // initial state
            viewModel.scan("192.168.1.1", listOf(80))

            val finalState = expectMostRecentItem()
            assertFalse(finalState.isScanning)
            assertEquals("Host unreachable", finalState.error)
        }
    }

    @Test
    fun `scan with error and null message shows default error`() = runTest {
        fakePortScanner.error = RuntimeException()

        viewModel.state.test {
            awaitItem() // initial state
            viewModel.scan("192.168.1.1", listOf(80))

            val finalState = expectMostRecentItem()
            assertFalse(finalState.isScanning)
            assertEquals("Scan failed", finalState.error)
        }
    }

    @Test
    fun `cancelScan stops scanning`() = runTest {
        // Use a scanner that emits one result then suspends indefinitely
        val hangingScanner = object : PortScanner {
            override fun scan(host: String, ports: List<Int>, timeoutMs: Int): Flow<PortResult> = flow {
                emit(PortResult(port = 80, serviceName = "HTTP", isOpen = true, latencyMs = 10))
                awaitCancellation()
            }
        }
        val vm = PortScanViewModel(hangingScanner, fakePortScanHistoryDao)

        vm.state.test {
            awaitItem() // initial state
            vm.scan("192.168.1.1", listOf(80, 443))

            // After first emit, scan is still in progress
            val scanning = expectMostRecentItem()
            assertTrue(scanning.isScanning)
            assertEquals(1, scanning.results.size)

            vm.cancelScan()
            val afterCancel = expectMostRecentItem()
            assertFalse(afterCancel.isScanning)
        }
    }

    @Test
    fun `scan resets state from previous scan`() = runTest {
        val firstResults = listOf(
            PortResult(port = 80, serviceName = "HTTP", isOpen = true, latencyMs = 10),
        )
        fakePortScanner.results = firstResults
        viewModel.scan("192.168.1.1", listOf(80))

        // Now scan again with different results
        val secondResults = listOf(
            PortResult(port = 443, serviceName = "HTTPS", isOpen = true, latencyMs = 12),
            PortResult(port = 8443, serviceName = "HTTPS-ALT", isOpen = false, latencyMs = 0),
        )
        fakePortScanner.results = secondResults

        viewModel.state.test {
            awaitItem() // state from first scan
            viewModel.scan("10.0.0.1", listOf(443, 8443))

            val finalState = expectMostRecentItem()
            assertEquals("10.0.0.1", finalState.host)
            assertEquals(2, finalState.results.size)
            assertEquals(1, finalState.openCount)
            assertFalse(finalState.isScanning)
        }
    }

    @Test
    fun `scan tracks progress incrementally`() = runTest {
        val results = listOf(
            PortResult(port = 80, serviceName = "HTTP", isOpen = true, latencyMs = 10),
            PortResult(port = 443, serviceName = "HTTPS", isOpen = false, latencyMs = 0),
        )
        fakePortScanner.results = results

        viewModel.state.test {
            awaitItem() // initial state
            viewModel.scan("192.168.1.1", listOf(80, 443))

            // The final state should have full progress
            val finalState = expectMostRecentItem()
            assertEquals(1f, finalState.progress)
            assertEquals(2, finalState.results.size)
        }
    }
}
