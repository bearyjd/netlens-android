package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepositoryImpl
import com.ventouxlabs.netlens.core.scan.engine.FakeArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.FakeLanMdnsScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeLanNetworkBinder
import com.ventouxlabs.netlens.core.scan.engine.FakeNetBiosProber
import com.ventouxlabs.netlens.core.scan.engine.FakePortScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeSsdpScanner
import com.ventouxlabs.netlens.core.scan.engine.SubnetScanner
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.feature.lanscan.engine.FakeScanLocationProvider
import com.ventouxlabs.netlens.feature.lanscan.model.ScanRangeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Discovery must run with the process bound to the LAN network.
 *
 * Issue #152: Android binds an app's sockets to its default network, which is the VPN whenever one
 * is up — including on-device ad-blockers, which are all `VpnService` implementations. LAN probes
 * then go into the tunnel and are dropped, and because `isReachable` reports a dropped probe as
 * `false` rather than raising, the sweep finishes "successfully" having found nothing. Measured on
 * a Pixel 9 Pro Fold: the same `/24` returned 0 devices with Tailscale up and 5 with it down.
 *
 * **What these tests can and cannot prove.** The binding itself is `ConnectivityManager` and cannot
 * be exercised on the JVM, so nothing here would catch a wrong `NetworkCapabilities` filter — that
 * needs a device A/B. What they do pin is the part that actually regressed: that scan work happens
 * *inside* the binder rather than around it, and that the binding is released afterwards. Moving
 * an engine out of the bound block — easy to do while refactoring, and silent at runtime for
 * anyone without a VPN — fails these.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanScanVpnBindingTest {

    private lateinit var binder: FakeLanNetworkBinder
    private lateinit var viewModel: LanScanViewModel

    /** Records whether the process was bound at the moment the sweep actually ran. */
    private var boundDuringPingSweep: Boolean? = null

    private val recordingSubnetScanner = object : SubnetScanner {
        override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow {
            boundDuringPingSweep = binder.isBound
            emit(LanDevice(ip = "192.168.1.10", hostname = "nas"))
        }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        binder = FakeLanNetworkBinder()
        boundDuringPingSweep = null
        viewModel = LanScanViewModel(
            subnetScanner = recordingSubnetScanner,
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
    fun `ping sweep runs while the process is bound to the LAN network`() = runTest {
        startScan()

        assertEquals(
            true,
            boundDuringPingSweep,
            "the sweep ran unbound — its probes would go into the VPN tunnel (#152)",
        )
    }

    @Test
    fun `scan binds exactly once rather than per engine`() = runTest {
        startScan()

        assertEquals(1, binder.bindCount)
    }

    @Test
    fun `binding is released once the scan finishes`() = runTest {
        startScan()

        assertFalse(binder.isBound, "the process stayed pinned to the LAN after the scan")
    }

    @Test
    fun `devices discovered while bound still reach the UI`() = runTest {
        startScan()

        val devices = viewModel.uiState.value.devices
        assertTrue(
            devices.any { it.ip == "192.168.1.10" },
            "binding the process must not swallow results: $devices",
        )
    }
}
