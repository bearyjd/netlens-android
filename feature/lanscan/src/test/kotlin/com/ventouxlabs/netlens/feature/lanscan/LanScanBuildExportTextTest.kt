package com.ventouxlabs.netlens.feature.lanscan

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
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.LanScanHistoryDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.LanScanHistoryEntry
import com.ventouxlabs.netlens.core.network.NetworkInterfaceInfo
import com.ventouxlabs.netlens.core.network.NetworkInterfaceProvider
import com.ventouxlabs.netlens.core.scan.engine.ArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.DeviceFingerprinter
import com.ventouxlabs.netlens.core.scan.engine.LanMdnsScanner
import com.ventouxlabs.netlens.core.scan.engine.NetBiosProber
import com.ventouxlabs.netlens.core.scan.engine.PortFingerprint
import com.ventouxlabs.netlens.core.scan.engine.SubnetScanner
import com.ventouxlabs.netlens.core.scan.engine.SsdpScanner
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepositoryImpl
import com.ventouxlabs.netlens.core.scan.NewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.NetBiosInfo
import com.ventouxlabs.netlens.feature.lanscan.model.ScanRangeMode
import com.ventouxlabs.netlens.feature.lanscan.model.LanScanHistoryUiModel
import com.ventouxlabs.netlens.feature.lanscan.model.ScanSnapshotDevice
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ventouxlabs.netlens.core.scan.model.SsdpDevice
import com.ventouxlabs.netlens.feature.portscan.engine.PortScanner
import com.ventouxlabs.netlens.feature.portscan.model.PortResult

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
            knownDeviceDao = FakeKnownDeviceDao(),
            deviceInventoryRepository = DeviceInventoryRepositoryImpl(FakeKnownDeviceDao(), FakeNewDeviceNotifier()),
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

    @Test
    fun `event export includes captured snapshot and optional coordinates`() {
        val event = LanScanHistoryUiModel(
            id = 7,
            timestamp = 1_700_000_000_000,
            subnet = "192.168.1.0/24",
            deviceCount = 1,
            devicesJson = Json.encodeToString(listOf(ScanSnapshotDevice("192.168.1.4", hostname = "router", vendor = "Example"))),
            latitude = 45.5,
            longitude = -73.6,
        )

        val text = viewModel.buildEventExportText(event)

        assertTrue(text.contains("LAN Scan Event"))
        assertTrue(text.contains("Location: 45.5, -73.6"))
        assertTrue(text.contains("192.168.1.4 (router)  Vendor=Example"))
    }

    @Test
    fun `event export retains legacy IP-only history`() {
        val text = viewModel.buildEventExportText(
            LanScanHistoryUiModel(1, 0, "10.0.0.0/24", 1, devicesJson = "[\"10.0.0.1\"]"),
        )

        assertTrue(text.contains("10.0.0.1"))
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

private class FakeKnownDeviceDao : KnownDeviceDao {
    override fun getAllDevices(): Flow<List<KnownDeviceEntity>> = flowOf(emptyList())
    override suspend fun getByMac(mac: String): KnownDeviceEntity? = null
    override suspend fun getByIpWithoutMac(ip: String): KnownDeviceEntity? = null
    override fun getUnknownDevices(): Flow<List<KnownDeviceEntity>> = flowOf(emptyList())
    override suspend fun insertIfNew(device: KnownDeviceEntity): Long = 1L
    override suspend fun updateLastSeen(id: Long, hostname: String?, ip: String, vendor: String?, lastSeen: Long, deviceType: String?, osGuess: String?) {}
    override suspend fun setMacAddress(id: Long, mac: String) {}
    override suspend fun setKnown(id: Long, isKnown: Boolean) {}
    override suspend fun setCustomName(id: Long, customName: String?) {}
    override suspend fun getById(id: Long): KnownDeviceEntity? = null
    override suspend fun updateUserDetails(
        id: Long,
        customName: String?,
        tags: String?,
        notes: String?,
        location: String?,
    ) {}
    override suspend fun setNetworkId(id: Long, networkId: Long?) {}
    override fun search(query: String): Flow<List<KnownDeviceEntity>> = flowOf(emptyList())
    override suspend fun delete(id: Long) {}
    override suspend fun deleteAll() {}
}

private class FakeNewDeviceNotifier : NewDeviceNotifier {
    override fun createChannel() {}
    override fun notify(device: KnownDeviceEntity) {}
}
