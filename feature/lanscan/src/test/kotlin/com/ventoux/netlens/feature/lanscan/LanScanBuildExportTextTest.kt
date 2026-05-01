package com.ventoux.netlens.feature.lanscan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.ventoux.netlens.core.data.dao.LanScanHistoryDao
import com.ventoux.netlens.core.data.model.LanScanHistoryEntry
import com.ventoux.netlens.core.network.NetworkInterfaceInfo
import com.ventoux.netlens.core.network.NetworkInterfaceProvider
import com.ventoux.netlens.feature.lanscan.engine.ArpTableReader
import com.ventoux.netlens.feature.lanscan.engine.DeviceFingerprinter
import com.ventoux.netlens.feature.lanscan.engine.LanMdnsScanner
import com.ventoux.netlens.feature.lanscan.engine.NetBiosProber
import com.ventoux.netlens.feature.lanscan.engine.PortFingerprint
import com.ventoux.netlens.feature.lanscan.engine.SubnetScanner
import com.ventoux.netlens.feature.lanscan.engine.SsdpScanner
import com.ventoux.netlens.feature.lanscan.model.LanDevice
import com.ventoux.netlens.feature.lanscan.model.NetBiosInfo
import com.ventoux.netlens.feature.lanscan.model.ScanRangeMode
import com.ventoux.netlens.feature.lanscan.model.SsdpDevice
import com.ventoux.netlens.feature.portscan.engine.PortScanner
import com.ventoux.netlens.feature.portscan.model.PortResult

@OptIn(ExperimentalCoroutinesApi::class)
class LanScanBuildExportTextTest {

    private lateinit var fakeSubnetScanner: FakeSubnetScanner
    private lateinit var viewModel: LanScanViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeSubnetScanner = FakeSubnetScanner()
        viewModel = LanScanViewModel(
            subnetScanner = fakeSubnetScanner,
            mdnsScanner = FakeLanMdnsScanner(),
            fingerprinter = FakeDeviceFingerprinter(),
            portScanner = FakePortScanner(),
            ssdpScanner = FakeSsdpScanner(),
            netBiosProber = FakeNetBiosProber(),
            arpTableReader = FakeArpTableReader(),
            networkInterfaceProvider = FakeNetworkInterfaceProvider(),
            lanScanHistoryDao = FakeLanScanHistoryDao(),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `buildExportText formats device list`() = runTest {
        fakeSubnetScanner.devices = listOf(
            LanDevice(ip = "192.168.1.1", hostname = "router.local", latencyMs = 2, macAddress = "AA:BB:CC:DD:EE:FF", vendor = "Cisco"),
            LanDevice(ip = "192.168.1.100", hostname = "desktop-pc", latencyMs = 5),
        )

        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()

        val text = viewModel.buildExportText()
        assertTrue(text.contains("LAN Scan results (192.168.1.0/24):"))
        assertTrue(text.contains("Devices found: 2"))
        assertTrue(text.contains("192.168.1.1 (router.local)  MAC=AA:BB:CC:DD:EE:FF  Vendor=Cisco  2ms"))
        assertTrue(text.contains("192.168.1.100 (desktop-pc)  5ms"))
    }

    @Test
    fun `buildExportText with null hostname and present MAC`() = runTest {
        fakeSubnetScanner.devices = listOf(
            LanDevice(ip = "192.168.1.50", hostname = null, latencyMs = 3, macAddress = "11:22:33:44:55:66"),
        )

        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()

        val text = viewModel.buildExportText()
        assertTrue(text.contains("Devices found: 1"))
        assertTrue(text.contains("192.168.1.50  MAC=11:22:33:44:55:66  3ms"))
        assertFalse(text.contains("192.168.1.50 ("))
    }
}

private class FakeSubnetScanner : SubnetScanner {
    var devices: List<LanDevice> = emptyList()
    override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> =
        flowOf(*devices.toTypedArray())
}

private class FakeLanMdnsScanner : LanMdnsScanner {
    override fun discover(timeoutMs: Long): Flow<LanDevice> = emptyFlow()
}

private class FakeDeviceFingerprinter : DeviceFingerprinter {
    override suspend fun fingerprint(device: LanDevice): LanDevice = device
    override fun classifyFromServices(services: List<String>): Pair<String?, String?> = null to null
    override fun classifyFromSsdp(ssdpDevice: SsdpDevice): Pair<String?, String?> = null to null
    override fun classifyFromNetBios(info: NetBiosInfo): String? = null
    override fun fingerprintWithPorts(device: LanDevice, openPorts: List<Int>): PortFingerprint =
        PortFingerprint(null, null, emptyList())
}

private class FakePortScanner : PortScanner {
    override fun scan(host: String, ports: List<Int>, timeoutMs: Int): Flow<PortResult> = emptyFlow()
}

private class FakeSsdpScanner : SsdpScanner {
    override fun discover(timeoutMs: Long): Flow<SsdpDevice> = emptyFlow()
}

private class FakeNetBiosProber : NetBiosProber {
    override suspend fun probe(ip: String): NetBiosInfo? = null
}

private class FakeArpTableReader : ArpTableReader {
    override suspend fun getMacForIp(ip: String): String? = null
    override suspend fun getAll(): Map<String, String> = emptyMap()
    override fun invalidateCache() {}
}

private class FakeNetworkInterfaceProvider : NetworkInterfaceProvider {
    override fun getNetworkInterfaces(): List<NetworkInterfaceInfo> = emptyList()
    override fun getActiveNetworkInterface(): NetworkInterfaceInfo? = null
}

private class FakeLanScanHistoryDao : LanScanHistoryDao {
    override fun getRecent(limit: Int): Flow<List<LanScanHistoryEntry>> = flowOf(emptyList())
    override fun search(query: String, limit: Int): Flow<List<LanScanHistoryEntry>> = flowOf(emptyList())
    override suspend fun getById(id: Long): LanScanHistoryEntry? = null
    override suspend fun insert(entry: LanScanHistoryEntry) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteOlderThan(before: Long) {}
    override suspend fun deleteAll() {}
}
