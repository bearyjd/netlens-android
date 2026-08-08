package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepositoryImpl
import com.ventouxlabs.netlens.core.scan.engine.FakeArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.FakeLanMdnsScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeLanNetworkBinder
import com.ventouxlabs.netlens.core.scan.engine.FakeNetBiosProber
import com.ventouxlabs.netlens.core.scan.engine.FakePortScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeSsdpScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeSubnetScanner
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.feature.lanscan.engine.FakeScanLocationProvider
import com.ventouxlabs.netlens.feature.lanscan.model.EmptyScanReason
import com.ventouxlabs.netlens.feature.lanscan.model.ScanRangeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * An empty scan result is ambiguous, and the ambiguity is the bug.
 *
 * Second half of #152. "0 devices" is what the user saw when a VPN made the whole LAN
 * unreachable — identical to what they see on a genuinely empty network. There was nothing to
 * report, nothing to retry, and no reason to suspect the app rather than the network.
 *
 * The distinguishing signal is whether the scan managed to bind to a local network at all. Bound
 * and empty means the network really had nothing to find; unbound and empty means the probes never
 * had a route out and the result is meaningless.
 *
 * Mirrors the precedent set by `LocationStatus.UNAVAILABLE`: a first-class outcome rather than an
 * error, because nothing failed — the app simply cannot answer the question it was asked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanScanEmptyResultTest {

    private lateinit var binder: FakeLanNetworkBinder
    private lateinit var subnetScanner: FakeSubnetScanner
    private lateinit var viewModel: LanScanViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        binder = FakeLanNetworkBinder()
        subnetScanner = FakeSubnetScanner()
        viewModel = LanScanViewModel(
            subnetScanner = subnetScanner,
            mdnsScanner = FakeLanMdnsScanner(),
            fingerprinter = FakeDeviceFingerprinter(),
            portScanner = FakePortScanner(),
            ssdpScanner = FakeSsdpScanner(),
            netBiosProber = FakeNetBiosProber(),
            arpTableReader = FakeArpTableReader(),
            lanNetworkBinder = binder,
            networkInterfaceProvider = FakeNetworkInterfaceProvider(),
            lanScanHistoryDao = FakeLanScanHistoryDao(),
            knownDeviceDao = FakeKnownDeviceDao(),
            deviceInventoryRepository = DeviceInventoryRepositoryImpl(
                FakeKnownDeviceDao(),
                FakeNewDeviceNotifier(),
            ),
            scanLocationProvider = FakeScanLocationProvider(),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun startScan() {
        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()
    }

    @Test
    fun `empty result with no LAN network to bind is reported as unreachable`() = runTest {
        binder.lanNetworkAvailable = false
        subnetScanner.devices = emptyList()

        startScan()

        assertEquals(
            EmptyScanReason.NETWORK_UNREACHABLE,
            viewModel.uiState.value.emptyScanReason,
            "an unreachable network must not look like an empty one (#152)",
        )
    }

    @Test
    fun `empty result while bound to the LAN is a genuinely empty network`() = runTest {
        binder.lanNetworkAvailable = true
        subnetScanner.devices = emptyList()

        startScan()

        assertNull(
            viewModel.uiState.value.emptyScanReason,
            "the scan reached the network and found nothing — that is a real answer, not a fault",
        )
    }

    @Test
    fun `devices found means no reason is reported even if binding was skipped`() = runTest {
        // Binding can legitimately fall back to unbound — a device with no Wi-Fi, scanning a range
        // reachable another way. Finding devices proves the probes had a route, so the
        // unreachable notice would be a lie.
        binder.lanNetworkAvailable = false
        subnetScanner.devices = listOf(LanDevice(ip = "192.168.1.10", hostname = "nas"))

        startScan()

        assertNull(viewModel.uiState.value.emptyScanReason)
    }

    @Test
    fun `a later successful scan clears the earlier unreachable notice`() = runTest {
        binder.lanNetworkAvailable = false
        subnetScanner.devices = emptyList()
        startScan()
        assertEquals(EmptyScanReason.NETWORK_UNREACHABLE, viewModel.uiState.value.emptyScanReason)

        binder.lanNetworkAvailable = true
        subnetScanner.devices = listOf(LanDevice(ip = "192.168.1.10"))
        startScan()

        assertNull(
            viewModel.uiState.value.emptyScanReason,
            "a stale notice on a good scan is its own wrong answer",
        )
    }
}
