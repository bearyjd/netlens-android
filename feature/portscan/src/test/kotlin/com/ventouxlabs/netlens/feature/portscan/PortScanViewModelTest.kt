package com.ventouxlabs.netlens.feature.portscan

import androidx.lifecycle.SavedStateHandle
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
import com.ventouxlabs.netlens.core.data.dao.PortScanHistoryDao
import com.ventouxlabs.netlens.core.data.model.PortScanHistoryEntry
import com.ventouxlabs.netlens.core.ui.UiText
import com.ventouxlabs.netlens.core.scan.engine.FakePortScanner
import com.ventouxlabs.netlens.core.scan.engine.PortScanner
import com.ventouxlabs.netlens.core.scan.model.PortResult
import com.ventouxlabs.netlens.feature.portscan.model.PortScanUiState

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

    private lateinit var savedState: SavedStateHandle

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakePortScanner = FakePortScanner()
        savedState = SavedStateHandle()
        viewModel = PortScanViewModel(fakePortScanner, fakePortScanHistoryDao, savedState)
    }

    /** A fresh ViewModel over the same saved state — what survives process death sees. */
    private fun restoredViewModel() =
        PortScanViewModel(fakePortScanner, fakePortScanHistoryDao, SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) }))

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
            assertEquals(UiText.Dynamic("Host unreachable"), finalState.error)
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
            assertEquals(UiText.Resource(R.string.portscan_error_scan_failed), finalState.error)
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
        val vm = PortScanViewModel(hangingScanner, fakePortScanHistoryDao, SavedStateHandle())

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

    @Test
    fun `arriving from another tool prefills the host`() {
        // The bug this pins: the screen kept the host in a local rememberSaveable that never read
        // state, so "scan this host" from LAN Scan or DNS silently opened an empty field.
        viewModel.prefillHost("192.168.1.50")
        assertEquals("192.168.1.50", viewModel.state.value.host)
    }

    @Test
    fun `a prefill applies once so a recreated screen cannot clobber typing`() {
        // The screen's LaunchedEffect re-runs whenever the composition is recreated — a rotation,
        // a fold — while the ViewModel survives. Re-applying there would discard what was typed.
        viewModel.prefillHost("192.168.1.50")
        viewModel.onHostChanged("nas.local")
        viewModel.prefillHost("192.168.1.50")

        assertEquals("nas.local", viewModel.state.value.host)
    }

    @Test
    fun `a second scan-this-host for a different device still applies`() {
        // navigateToTool uses launchSingleTop, so a "scan this host" arriving while Port Scan is
        // already on top reuses the back stack entry and therefore this ViewModel. Guarding on a
        // once-ever boolean made that tap do nothing at all — the user would press it and watch
        // the previous host sit there. The guard is against re-applying the *same* argument, not
        // against ever hearing a new one.
        viewModel.prefillHost("192.168.1.50")
        viewModel.prefillHost("192.168.1.60")

        assertEquals("192.168.1.60", viewModel.state.value.host)
    }

    @Test
    fun `a re-prefill of the same host is still a no-op after an edit and a restore`() {
        // Both halves of the guard survive the change from a boolean to a value: the recreated
        // composition case, and the restored-nav-argument case, for the same host.
        viewModel.prefillHost("192.168.1.50")
        viewModel.prefillHost("192.168.1.60")
        viewModel.onHostChanged("nas.local")

        val restored = restoredViewModel()
        restored.prefillHost("192.168.1.60")

        assertEquals("nas.local", restored.state.value.host)
    }

    @Test
    fun `a typed host survives process death`() {
        // The host used to live in a rememberSaveable, which wrote to the instance-state Bundle.
        // A plain MutableStateFlow does not, so moving it into the ViewModel would have quietly
        // dropped it when the system reclaimed the process.
        viewModel.onHostChanged("192.168.1.99")

        assertEquals("192.168.1.99", restoredViewModel().state.value.host)
    }

    @Test
    fun `a stale nav argument cannot overwrite an edit after process death`() {
        // The nastier half: the back stack restores the original ?query= argument, so the screen
        // re-fires prefillHost against a ViewModel that is genuinely new. If "already prefilled"
        // lived in a plain field it would read false, and the user's edit would be replaced by a
        // stale value rather than merely lost.
        viewModel.prefillHost("192.168.1.5")
        viewModel.onHostChanged("192.168.1.7")

        val restored = restoredViewModel()
        restored.prefillHost("192.168.1.5")

        assertEquals("192.168.1.7", restored.state.value.host)
    }

    @Test
    fun `the scanned host is what gets restored, not the untrimmed text`() {
        viewModel.onHostChanged("  10.0.0.1  ")
        viewModel.scan("10.0.0.1", listOf(80))

        assertEquals("10.0.0.1", restoredViewModel().state.value.host)
    }
}
