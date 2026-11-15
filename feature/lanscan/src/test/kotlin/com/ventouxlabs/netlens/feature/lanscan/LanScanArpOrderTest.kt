package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.network.NetworkInterfaceInfo
import com.ventouxlabs.netlens.core.network.NetworkInterfaceProvider
import com.ventouxlabs.netlens.core.scan.engine.ArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.FakePortScanner
import com.ventouxlabs.netlens.core.scan.engine.SubnetScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeLanNetworkBinder
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.network.FakeNetworkIdentity
import com.ventouxlabs.netlens.core.scan.network.NetworkSnapshot
import com.ventouxlabs.netlens.core.scan.testing.InventoryFakes
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the ARP-cache order of a scan, which decides which ARP table enrichment reads.
 *
 * Real order for an automatic range: drop the cache, take the identity snapshot (which re-reads
 * ARP, so the cache holds the PRE-sweep table), sweep, enrich from that cached table. A host that
 * only appears in ARP after the sweep therefore keeps a null MAC. For a custom range there is no
 * snapshot, the cache stays empty through the sweep, and enrichment reads the post-sweep table.
 * Both are the behaviour from before the pipeline extraction; a post-sweep read for the automatic
 * range would be a deliberate follow-up, not something to slip in here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanScanArpOrderTest {

    private val calls = mutableListOf<String>()
    private val postSweepHost = "192.168.1.77"
    private val postSweepMac = "AA:BB:CC:00:00:77"

    /** ARP as the kernel shows it; the host is only learned once the sweep has run. */
    private var live = emptyMap<String, String>()

    /** Mimics `ArpTableReaderImpl`: reads are cached until invalidated. */
    private val arp = object : ArpTableReader {
        private var cache: Map<String, String>? = null
        override suspend fun getMacForIp(ip: String): String? = getAll()[ip]
        override suspend fun getAll(): Map<String, String> =
            (cache ?: live.also { cache = it }).also { calls += "arp-read" }
        override fun invalidateCache() { cache = null; calls += "invalidate" }
    }

    private val identity = object : FakeNetworkIdentity() {
        override suspend fun snapshot(): NetworkSnapshot {
            // The real currentGatewayMac() drops the ARP cache and re-reads /proc/net/arp.
            arp.invalidateCache()
            arp.getAll()
            calls += "snapshot"
            return super.snapshot()
        }
    }

    private val sweeper = object : SubnetScanner {
        override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow {
            calls += "sweep"
            live = mapOf(postSweepHost to postSweepMac)
            emit(LanDevice(ip = postSweepHost))
        }
    }

    private val activeInterface = object : NetworkInterfaceProvider {
        override fun getNetworkInterfaces() = emptyList<NetworkInterfaceInfo>()
        override fun getActiveNetworkInterface() = NetworkInterfaceInfo(
            ip = "192.168.1.5", prefixLength = 24, interfaceName = "wlan0", label = "Wi-Fi", isVpn = false,
        )
    }

    private lateinit var viewModel: LanScanViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = LanScanViewModel(
            discoveryPipeline = enginePipeline(subnetScanner = sweeper, arpTableReader = arp),
            arpTableReader = arp,
            fingerprinter = FakeDeviceFingerprinter(),
            portScanner = FakePortScanner(),
            lanNetworkBinder = FakeLanNetworkBinder(),
            networkInterfaceProvider = activeInterface,
            lanScanHistoryDao = FakeLanScanHistoryDao(),
            knownDeviceDao = FakeKnownDeviceDao(),
            deviceInventoryRepository = InventoryFakes().repository(FakeNewDeviceNotifier(), autoMerge = false),
            scanLocationProvider = FakeScanLocationProvider(),
            networkIdentity = identity,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an automatic scan enriches from the pre-sweep ARP table`() = runTest {
        viewModel.onRangeModeChanged(ScanRangeMode.AUTO)
        viewModel.startScan()

        // invalidate, then the start snapshot (itself invalidate + read), then the sweep, then the
        // enrichment read (served from the cache). The end-of-scan snapshot follows and is not pinned.
        assertEquals(
            listOf("invalidate", "invalidate", "arp-read", "snapshot", "sweep", "arp-read"),
            calls.take(6),
            "call order: $calls",
        )
        assertNull(
            viewModel.uiState.value.devices.single().macAddress,
            "a host that only shows up in ARP after the sweep must keep a null MAC, as before the extraction",
        )
    }

    @Test
    fun `a custom range enriches from the post-sweep ARP table`() = runTest {
        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()

        assertEquals(listOf("invalidate", "sweep", "arp-read"), calls)
        assertEquals(postSweepMac, viewModel.uiState.value.devices.single().macAddress)
    }
}
