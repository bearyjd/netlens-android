package com.ventouxlabs.netlens.feature.lanscan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.ventouxlabs.netlens.feature.lanscan.engine.FakeScanLocationProvider
import com.ventouxlabs.netlens.core.data.dao.LanScanHistoryDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.LanScanHistoryEntry
import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.network.NetworkInterfaceInfo
import com.ventouxlabs.netlens.core.network.NetworkInterfaceProvider
import com.ventouxlabs.netlens.core.scan.engine.DeviceFingerprinter
import com.ventouxlabs.netlens.core.scan.engine.PortFingerprint
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepositoryImpl
import com.ventouxlabs.netlens.core.scan.NewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.NetBiosInfo
import com.ventouxlabs.netlens.feature.lanscan.model.ScanRangeMode
import com.ventouxlabs.netlens.core.scan.model.SsdpDevice
import com.ventouxlabs.netlens.core.scan.engine.FakePortScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.FakeLanMdnsScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeLanNetworkBinder
import com.ventouxlabs.netlens.core.scan.engine.FakeNetBiosProber
import com.ventouxlabs.netlens.core.scan.engine.FakeSsdpScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeSubnetScanner

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceInventoryTest {

    private lateinit var fakeSubnetScanner: FakeSubnetScanner
    private lateinit var fakeKnownDeviceDao: FakeKnownDeviceDao
    private lateinit var fakeNotifier: RecordingNewDeviceNotifier
    private lateinit var viewModel: LanScanViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeSubnetScanner = FakeSubnetScanner()
        fakeKnownDeviceDao = FakeKnownDeviceDao()
        fakeNotifier = RecordingNewDeviceNotifier()
        viewModel = LanScanViewModel(
            subnetScanner = fakeSubnetScanner,
            mdnsScanner = FakeLanMdnsScanner(),
            fingerprinter = StubDeviceFingerprinter(),
            portScanner = FakePortScanner(),
            ssdpScanner = FakeSsdpScanner(),
            netBiosProber = FakeNetBiosProber(),
            arpTableReader = FakeArpTableReader(),
            lanNetworkBinder = FakeLanNetworkBinder(),
            networkInterfaceProvider = StubNetworkInterfaceProvider(),
            lanScanHistoryDao = StubLanScanHistoryDao(),
            knownDeviceDao = fakeKnownDeviceDao,
            deviceInventoryRepository = DeviceInventoryRepositoryImpl(fakeKnownDeviceDao, fakeNotifier),
            scanLocationProvider = FakeScanLocationProvider(),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `new device with MAC is persisted after scan`() = runTest {
        fakeSubnetScanner.devices = listOf(
            LanDevice(ip = "192.168.1.10", hostname = "phone", macAddress = "AA:BB:CC:DD:EE:01"),
        )

        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()

        val stored = fakeKnownDeviceDao.getByMac("AA:BB:CC:DD:EE:01")
        assertNotNull(stored)
        assertEquals("192.168.1.10", stored?.ip)
        assertEquals("phone", stored?.hostname)
    }

    @Test
    fun `new device triggers notification`() = runTest {
        fakeSubnetScanner.devices = listOf(
            LanDevice(ip = "192.168.1.30", macAddress = "AA:BB:CC:DD:EE:03", vendor = "Acme"),
        )

        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()

        assertEquals(1, fakeNotifier.notified.size)
        assertEquals("AA:BB:CC:DD:EE:03", fakeNotifier.notified.first().macAddress)
    }

    @Test
    fun `toggleKnown flips isKnown flag`() = runTest {
        fakeKnownDeviceDao.insertIfNew(
            KnownDeviceEntity(
                macAddress = "AA:BB:CC:DD:EE:05",
                hostname = "test",
                ip = "192.168.1.50",
                vendor = null,
                isKnown = false,
            ),
        )
        val id = fakeKnownDeviceDao.getByMac("AA:BB:CC:DD:EE:05")!!.id

        viewModel.toggleKnown(id)

        val toggled = fakeKnownDeviceDao.getByMac("AA:BB:CC:DD:EE:05")
        assertTrue(toggled?.isKnown == true)

        viewModel.toggleKnown(id)

        val toggledBack = fakeKnownDeviceDao.getByMac("AA:BB:CC:DD:EE:05")
        assertFalse(toggledBack?.isKnown == true)
    }

    @Test
    fun `deleteDevice removes from inventory`() = runTest {
        fakeKnownDeviceDao.insertIfNew(
            KnownDeviceEntity(
                macAddress = "AA:BB:CC:DD:EE:06",
                hostname = "to-delete",
                ip = "192.168.1.60",
                vendor = null,
            ),
        )
        val id = fakeKnownDeviceDao.getByMac("AA:BB:CC:DD:EE:06")!!.id

        viewModel.deleteDevice(id)

        val deleted = fakeKnownDeviceDao.getByMac("AA:BB:CC:DD:EE:06")
        assertEquals(null, deleted)
    }

    @Test
    fun `multiple devices in single scan are all persisted`() = runTest {
        fakeSubnetScanner.devices = listOf(
            LanDevice(ip = "192.168.1.1", macAddress = "AA:BB:CC:DD:EE:A1"),
            LanDevice(ip = "192.168.1.2", macAddress = "AA:BB:CC:DD:EE:A2"),
            LanDevice(ip = "192.168.1.3", macAddress = "AA:BB:CC:DD:EE:A3"),
        )

        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()

        assertEquals(3, fakeKnownDeviceDao.allDevices.size)
        assertEquals(3, fakeNotifier.notified.size)
    }
}

// --- Test fakes ---

private class StubDeviceFingerprinter : DeviceFingerprinter {
    override suspend fun fingerprint(device: LanDevice): LanDevice = device
    override fun classifyFromServices(services: List<String>): Pair<String?, String?> = null to null
    override fun classifyFromSsdp(ssdpDevice: SsdpDevice): Pair<String?, String?> = null to null
    override fun classifyFromNetBios(info: NetBiosInfo): String? = null
    override fun fingerprintWithPorts(device: LanDevice, openPorts: List<Int>): PortFingerprint =
        PortFingerprint(null, null, emptyList())
}

private class StubNetworkInterfaceProvider : NetworkInterfaceProvider {
    override fun getNetworkInterfaces(): List<NetworkInterfaceInfo> = emptyList()
    override fun getActiveNetworkInterface(): NetworkInterfaceInfo? = null
}

private class StubLanScanHistoryDao : LanScanHistoryDao {
    override fun getRecent(limit: Int): Flow<List<LanScanHistoryEntry>> = flowOf(emptyList())
    override fun search(query: String, limit: Int): Flow<List<LanScanHistoryEntry>> = flowOf(emptyList())
    override suspend fun getById(id: Long): LanScanHistoryEntry? = null
    override suspend fun insert(entry: LanScanHistoryEntry) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteOlderThan(before: Long) {}
    override suspend fun deleteAll() {}
}

private class RecordingNewDeviceNotifier : NewDeviceNotifier {
    val notified = mutableListOf<KnownDeviceEntity>()
    override fun createChannel() {}
    override fun notify(device: KnownDeviceEntity) {
        notified.add(device)
    }
}

