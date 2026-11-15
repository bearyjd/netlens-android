package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryEvent
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryMode
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryRequest
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryStage
import com.ventouxlabs.netlens.core.scan.engine.FakeArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.FakeDeviceDiscoveryPipeline
import com.ventouxlabs.netlens.core.scan.engine.FakeLanNetworkBinder
import com.ventouxlabs.netlens.core.scan.engine.FakePortScanner
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.network.FakeNetworkIdentity
import com.ventouxlabs.netlens.core.scan.testing.InventoryFakes
import com.ventouxlabs.netlens.feature.lanscan.engine.FakeScanLocationProvider
import com.ventouxlabs.netlens.feature.lanscan.model.ScanRangeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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

/**
 * What the ViewModel does with a discovery run, against a scripted pipeline: UI state, progress,
 * errors and cancellation. How devices are merged and enriched is `DeviceDiscoveryPipelineTest`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanScanDiscoveryStateTest {

    private lateinit var binder: FakeLanNetworkBinder
    private lateinit var pipeline: FakeDeviceDiscoveryPipeline
    private lateinit var viewModel: LanScanViewModel

    private val a = LanDevice(ip = "192.168.1.2", latencyMs = 30)
    private val b = LanDevice(ip = "192.168.1.10", latencyMs = 5)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        binder = FakeLanNetworkBinder()
        pipeline = FakeDeviceDiscoveryPipeline()
        viewModel = LanScanViewModel(
            discoveryPipeline = pipeline,
            arpTableReader = FakeArpTableReader(),
            fingerprinter = FakeDeviceFingerprinter(),
            portScanner = FakePortScanner(),
            lanNetworkBinder = binder,
            networkInterfaceProvider = FakeNetworkInterfaceProvider(),
            lanScanHistoryDao = FakeLanScanHistoryDao(),
            knownDeviceDao = FakeKnownDeviceDao(),
            deviceInventoryRepository = InventoryFakes().repository(FakeNewDeviceNotifier()),
            scanLocationProvider = FakeScanLocationProvider(),
            networkIdentity = FakeNetworkIdentity(),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Runs a test that leaves the pipeline suspended, then tears the scan down unconditionally so a
     * failing assertion (or a broken `cancelScan`) fails the test instead of hanging `runTest`.
     */
    private fun suspendedScanTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            viewModel.viewModelScope.cancel()
            runCurrent()
        }
    }

    private fun startScan() {
        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()
    }

    @Test
    fun `a scan asks the pipeline for a Standard run over the chosen range`() = runTest {
        startScan()

        assertEquals(listOf(DiscoveryRequest("192.168.1.0/24", DiscoveryMode.Standard)), pipeline.requests)
    }

    @Test
    fun `the pipeline runs inside the LAN binding`() = runTest {
        var boundAtRun: Boolean? = null
        pipeline.onRun = { boundAtRun = binder.isBound }

        startScan()

        assertEquals(true, boundAtRun)
    }

    @Test
    fun `a finished scan publishes the devices sorted and completes progress`() = runTest {
        pipeline.events = listOf(
            DiscoveryEvent.DeviceUpdated(b),
            DiscoveryEvent.DeviceUpdated(a),
            DiscoveryEvent.Progress(DiscoveryStage.Arp, 1, 3),
            DiscoveryEvent.Finished(listOf(b, a)),
        )

        startScan()

        val state = viewModel.uiState.value
        assertEquals(listOf("192.168.1.2", "192.168.1.10"), state.devices.map { it.ip })
        assertEquals(2, state.deviceCount)
        assertEquals(1f, state.progress)
        assertFalse(state.isScanning)
    }

    @Test
    fun `devices are not published before discovery ends, then flushed with capped progress`() = suspendedScanTest {
        pipeline.events = listOf(DiscoveryEvent.DeviceUpdated(a))
        pipeline.suspendAfterEvents = true

        startScan()
        assertTrue(viewModel.uiState.value.devices.isEmpty())
        assertTrue(viewModel.uiState.value.isScanning)

        advanceTimeBy(250)

        val state = viewModel.uiState.value
        assertEquals(listOf("192.168.1.2"), state.devices.map { it.ip })
        assertEquals(1f / 254f, state.progress)
        assertTrue(state.isScanning)
        viewModel.cancelScan()
    }

    @Test
    fun `reaching enrichment flushes discovery at once and later updates replace devices in place`() = suspendedScanTest {
        val enriched = a.copy(macAddress = "11:22:33:44:55:66")
        pipeline.events = listOf(
            DiscoveryEvent.DeviceUpdated(a),
            DiscoveryEvent.Progress(DiscoveryStage.Arp, 1, 3),
            DiscoveryEvent.DeviceUpdated(enriched),
        )
        pipeline.suspendAfterEvents = true

        startScan()

        val state = viewModel.uiState.value
        assertEquals(listOf(enriched), state.devices)
        assertEquals(1f / 254f, state.progress)
        viewModel.cancelScan()
    }

    @Test
    fun `a failed stage surfaces its message while the scan completes`() = runTest {
        pipeline.events = listOf(
            DiscoveryEvent.StageFailed(DiscoveryStage.Discovery, "socket closed"),
            DiscoveryEvent.Finished(emptyList()),
        )

        startScan()

        assertEquals("socket closed", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isScanning)
    }

    @Test
    fun `a stage failure without a message gets the default text`() = runTest {
        pipeline.events = listOf(DiscoveryEvent.StageFailed(DiscoveryStage.Discovery, null))

        startScan()

        assertEquals("Ping sweep failed", viewModel.uiState.value.error)
    }

    @Test
    fun `cancelling stops the pipeline and leaves the scan idle`() = suspendedScanTest {
        pipeline.events = listOf(DiscoveryEvent.DeviceUpdated(a))
        pipeline.suspendAfterEvents = true
        startScan()
        runCurrent()
        assertTrue(pipeline.isRunning)

        viewModel.cancelScan()
        runCurrent()

        assertFalse(pipeline.isRunning, "the pipeline kept running after cancelScan")
        assertFalse(viewModel.uiState.value.isScanning)
        assertFalse(binder.isBound, "the LAN binding outlived the cancelled scan")
    }

    @Test
    fun `a second scan is ignored while one is running`() = suspendedScanTest {
        pipeline.suspendAfterEvents = true
        startScan()

        viewModel.startScan()

        assertEquals(1, pipeline.requests.size)
        viewModel.cancelScan()
    }

    @Test
    fun `a new scan clears the previous results and error`() = runTest {
        pipeline.events = listOf(DiscoveryEvent.StageFailed(DiscoveryStage.Discovery, "boom"))
        startScan()
        pipeline.events = listOf(DiscoveryEvent.DeviceUpdated(a), DiscoveryEvent.Progress(DiscoveryStage.Arp, 1, 3))

        startScan()

        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf(a), viewModel.uiState.value.devices)
    }

    @Test
    fun `changing the sort order re-sorts the published devices`() = runTest {
        pipeline.events = listOf(
            DiscoveryEvent.DeviceUpdated(a),
            DiscoveryEvent.DeviceUpdated(b),
            DiscoveryEvent.Progress(DiscoveryStage.Arp, 1, 3),
        )
        startScan()

        viewModel.setSortOrder(SortOrder.LATENCY)

        assertEquals(listOf("192.168.1.10", "192.168.1.2"), viewModel.uiState.value.devices.map { it.ip })
    }
}
