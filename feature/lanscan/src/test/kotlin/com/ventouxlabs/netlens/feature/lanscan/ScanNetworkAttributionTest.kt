package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.network.NetworkInterfaceInfo
import com.ventouxlabs.netlens.core.network.NetworkInterfaceProvider
import com.ventouxlabs.netlens.core.scan.engine.FakeArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.FakeLanMdnsScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeLanNetworkBinder
import com.ventouxlabs.netlens.core.scan.engine.FakeNetBiosProber
import com.ventouxlabs.netlens.core.scan.engine.FakePortScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeSsdpScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeSubnetScanner
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.network.FakeNetworkIdentity
import com.ventouxlabs.netlens.core.scan.network.NetworkSnapshot
import com.ventouxlabs.netlens.core.scan.testing.InventoryFakes
import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.feature.lanscan.engine.FakeScanLocationProvider
import com.ventouxlabs.netlens.feature.lanscan.model.ScanRangeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Which network a foreground scan's sightings are attributed to. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScanNetworkAttributionTest {
    private val fakes = InventoryFakes(FakeKnownDeviceDao())
    private val binder = FakeLanNetworkBinder()
    private var boundAtSnapshot = mutableListOf<Boolean>()

    private val identity = object : FakeNetworkIdentity() {
        override suspend fun snapshot(): NetworkSnapshot {
            boundAtSnapshot += binder.isBound
            return super.snapshot()
        }
    }.apply {
        gatewayMac = "AA:BB:CC:DD:EE:00"; gatewayIp = "192.168.1.1"; subnet = "192.168.1.0/24"; ssid = "Home"
    }

    private lateinit var viewModel: LanScanViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = LanScanViewModel(
            discoveryPipeline = enginePipeline(subnetScanner = FakeSubnetScanner().apply { devices = listOf(LanDevice(ip = "192.168.1.10", macAddress = "00:BB:CC:00:00:01")) }, arpTableReader = FakeArpTableReader()),
            arpTableReader = FakeArpTableReader(),
            fingerprinter = FakeDeviceFingerprinter(),
            portScanner = FakePortScanner(),
            lanNetworkBinder = binder,
            networkInterfaceProvider = object : NetworkInterfaceProvider {
                override fun getNetworkInterfaces() = emptyList<NetworkInterfaceInfo>()
                override fun getActiveNetworkInterface() = NetworkInterfaceInfo("192.168.1.20", 24, "wlan0", "Wi-Fi", false, "192.168.1.1")
            },
            lanScanHistoryDao = FakeLanScanHistoryDao(),
            knownDeviceDao = fakes.known,
            deviceInventoryRepository = fakes.repository(FakeNewDeviceNotifier()),
            scanLocationProvider = FakeScanLocationProvider(),
            networkIdentity = identity,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun sighting() = fakes.sightings.getForDevice(1).single()

    @Test
    fun `an automatic scan stores the canonical key and the label`() = runTest {
        viewModel.startScan()

        assertEquals("gw-mac:aabbccddee00", sighting().networkKey)
        assertEquals("Home", sighting().networkLabel)
    }

    @Test
    fun `a custom range is attributed to no network`() = runTest {
        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()

        assertEquals("unknown-network", sighting().networkKey)
        assertTrue(fakes.aliases.all.isEmpty())
        assertTrue(identity.snapshotsTaken.isEmpty())
    }

    @Test
    fun `the identity is read inside the LAN binding, at the start and the end of the scan`() = runTest {
        viewModel.startScan()

        assertEquals(listOf(true, true), boundAtSnapshot)
    }

    @Test
    fun `a scan during which the network changed is attributed to none`() = runTest {
        identity.scriptedSnapshots += NetworkSnapshot("aa:bb:cc:dd:ee:00", "192.168.1.1", "192.168.1.0/24")
        identity.scriptedSnapshots += NetworkSnapshot("10:22:33:44:55:66", "10.0.0.1", "10.0.0.0/24")
        viewModel.startScan()

        assertEquals("unknown-network", sighting().networkKey)
        assertTrue(fakes.aliases.all.isEmpty())
    }

    @Test
    fun `a gateway MAC that only appears during the scan is not a network change`() = runTest {
        identity.scriptedSnapshots += NetworkSnapshot(null, "192.168.1.1", "192.168.1.0/24")
        identity.scriptedSnapshots += NetworkSnapshot("aa:bb:cc:dd:ee:00", "192.168.1.1", "192.168.1.0/24")
        viewModel.startScan()

        assertEquals("gw-mac:aabbccddee00", sighting().networkKey)
    }
}
