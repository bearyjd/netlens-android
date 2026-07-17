package com.ventouxlabs.netlens.feature.lanscan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import com.ventouxlabs.netlens.core.scan.NewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.NetBiosInfo
import com.ventouxlabs.netlens.feature.lanscan.model.ScanRangeMode
import com.ventouxlabs.netlens.core.scan.model.SsdpDevice
import com.ventouxlabs.netlens.feature.portscan.engine.PortScanner
import com.ventouxlabs.netlens.feature.portscan.model.PortResult

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceInventoryTest {

    private lateinit var fakeSubnetScanner: InventoryTestSubnetScanner
    private lateinit var fakeKnownDeviceDao: InMemoryKnownDeviceDao
    private lateinit var fakeNotifier: RecordingNewDeviceNotifier
    private lateinit var viewModel: LanScanViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeSubnetScanner = InventoryTestSubnetScanner()
        fakeKnownDeviceDao = InMemoryKnownDeviceDao()
        fakeNotifier = RecordingNewDeviceNotifier()
        viewModel = LanScanViewModel(
            subnetScanner = fakeSubnetScanner,
            mdnsScanner = StubLanMdnsScanner(),
            fingerprinter = StubDeviceFingerprinter(),
            portScanner = StubPortScanner(),
            ssdpScanner = StubSsdpScanner(),
            netBiosProber = StubNetBiosProber(),
            arpTableReader = StubArpTableReader(),
            networkInterfaceProvider = StubNetworkInterfaceProvider(),
            lanScanHistoryDao = StubLanScanHistoryDao(),
            knownDeviceDao = fakeKnownDeviceDao,
            newDeviceNotifier = fakeNotifier,
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
    fun `device without MAC is still persisted, keyed by IP`() = runTest {
        fakeSubnetScanner.devices = listOf(
            LanDevice(ip = "192.168.1.20", hostname = "no-mac-device", macAddress = null),
        )

        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()

        val stored = fakeKnownDeviceDao.getByIpWithoutMac("192.168.1.20")
        assertNotNull(stored)
        assertEquals("no-mac-device", stored?.hostname)
        assertEquals(null, stored?.macAddress)
    }

    @Test
    fun `re-scanning the same mac-less device updates it instead of duplicating`() = runTest {
        fakeSubnetScanner.devices = listOf(
            LanDevice(ip = "192.168.1.21", hostname = "no-mac-device", macAddress = null),
        )
        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()
        viewModel.startScan()

        assertEquals(1, fakeKnownDeviceDao.allDevices.size)
    }

    @Test
    fun `mac-less device upgrades in place once a MAC resolves for its IP`() = runTest {
        fakeSubnetScanner.devices = listOf(
            LanDevice(ip = "192.168.1.22", hostname = "device", macAddress = null),
        )
        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()

        fakeSubnetScanner.devices = listOf(
            LanDevice(ip = "192.168.1.22", hostname = "device", macAddress = "AA:BB:CC:DD:EE:22"),
        )
        viewModel.startScan()

        assertEquals(1, fakeKnownDeviceDao.allDevices.size)
        val upgraded = fakeKnownDeviceDao.getByMac("AA:BB:CC:DD:EE:22")
        assertNotNull(upgraded)
        assertEquals(null, fakeKnownDeviceDao.getByIpWithoutMac("192.168.1.22"))
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
    fun `existing device updates lastSeen without notification`() = runTest {
        // Pre-populate a known device
        fakeKnownDeviceDao.insertIfNew(
            KnownDeviceEntity(
                macAddress = "AA:BB:CC:DD:EE:04",
                hostname = "old-name",
                ip = "192.168.1.40",
                vendor = "OldVendor",
                firstSeen = 1000L,
                lastSeen = 1000L,
            ),
        )
        fakeNotifier.notified.clear()

        fakeSubnetScanner.devices = listOf(
            LanDevice(
                ip = "192.168.1.41",
                hostname = "new-name",
                macAddress = "AA:BB:CC:DD:EE:04",
                vendor = "NewVendor",
            ),
        )

        viewModel.onRangeModeChanged(ScanRangeMode.CUSTOM)
        viewModel.onCustomRangeChanged("192.168.1.0/24")
        viewModel.startScan()

        // Notification should NOT fire for existing device
        assertTrue(fakeNotifier.notified.isEmpty())

        // But the device should be updated
        val updated = fakeKnownDeviceDao.getByMac("AA:BB:CC:DD:EE:04")
        assertNotNull(updated)
        assertEquals("192.168.1.41", updated?.ip)
        assertEquals("new-name", updated?.hostname)
        // firstSeen should remain unchanged
        assertEquals(1000L, updated?.firstSeen)
        // lastSeen should be updated
        assertTrue((updated?.lastSeen ?: 0) > 1000L)
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

private class InventoryTestSubnetScanner : SubnetScanner {
    var devices: List<LanDevice> = emptyList()
    override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> =
        flowOf(*devices.toTypedArray())
}

private class StubLanMdnsScanner : LanMdnsScanner {
    override fun discover(timeoutMs: Long): Flow<LanDevice> = emptyFlow()
}

private class StubDeviceFingerprinter : DeviceFingerprinter {
    override suspend fun fingerprint(device: LanDevice): LanDevice = device
    override fun classifyFromServices(services: List<String>): Pair<String?, String?> = null to null
    override fun classifyFromSsdp(ssdpDevice: SsdpDevice): Pair<String?, String?> = null to null
    override fun classifyFromNetBios(info: NetBiosInfo): String? = null
    override fun fingerprintWithPorts(device: LanDevice, openPorts: List<Int>): PortFingerprint =
        PortFingerprint(null, null, emptyList())
}

private class StubPortScanner : PortScanner {
    override fun scan(host: String, ports: List<Int>, timeoutMs: Int): Flow<PortResult> = emptyFlow()
}

private class StubSsdpScanner : SsdpScanner {
    override fun discover(timeoutMs: Long): Flow<SsdpDevice> = emptyFlow()
}

private class StubNetBiosProber : NetBiosProber {
    override suspend fun probe(ip: String): NetBiosInfo? = null
}

private class StubArpTableReader : ArpTableReader {
    override suspend fun getMacForIp(ip: String): String? = null
    override suspend fun getAll(): Map<String, String> = emptyMap()
    override fun invalidateCache() {}
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

private class InMemoryKnownDeviceDao : KnownDeviceDao {
    val allDevices = mutableListOf<KnownDeviceEntity>()
    private var nextId = 1L
    private val _flow = MutableStateFlow<List<KnownDeviceEntity>>(emptyList())

    override fun getAllDevices(): Flow<List<KnownDeviceEntity>> = _flow

    override suspend fun getByMac(mac: String): KnownDeviceEntity? =
        allDevices.find { it.macAddress == mac }

    override suspend fun getByIpWithoutMac(ip: String): KnownDeviceEntity? =
        allDevices.find { it.ip == ip && it.macAddress == null }

    override fun getUnknownDevices(): Flow<List<KnownDeviceEntity>> =
        flowOf(allDevices.filter { !it.isKnown })

    override suspend fun insertIfNew(device: KnownDeviceEntity): Long {
        if (device.macAddress != null && allDevices.any { it.macAddress == device.macAddress }) return -1L
        val withId = device.copy(id = nextId++)
        allDevices.add(withId)
        _flow.update { allDevices.toList() }
        return withId.id
    }

    override suspend fun updateLastSeen(
        id: Long,
        hostname: String?,
        ip: String,
        vendor: String?,
        lastSeen: Long,
        deviceType: String?,
        osGuess: String?,
    ) {
        val index = allDevices.indexOfFirst { it.id == id }
        if (index >= 0) {
            allDevices[index] = allDevices[index].copy(
                hostname = hostname,
                ip = ip,
                vendor = vendor,
                lastSeen = lastSeen,
                deviceType = deviceType,
                osGuess = osGuess,
            )
            _flow.update { allDevices.toList() }
        }
    }

    override suspend fun setMacAddress(id: Long, mac: String) {
        val index = allDevices.indexOfFirst { it.id == id }
        if (index >= 0) {
            allDevices[index] = allDevices[index].copy(macAddress = mac)
            _flow.update { allDevices.toList() }
        }
    }

    override suspend fun setKnown(id: Long, isKnown: Boolean) {
        val index = allDevices.indexOfFirst { it.id == id }
        if (index >= 0) {
            allDevices[index] = allDevices[index].copy(isKnown = isKnown)
            _flow.update { allDevices.toList() }
        }
    }

    override fun search(query: String): Flow<List<KnownDeviceEntity>> =
        flowOf(allDevices.filter { it.hostname?.contains(query) == true || it.ip.contains(query) })

    override suspend fun delete(id: Long) {
        allDevices.removeAll { it.id == id }
        _flow.update { allDevices.toList() }
    }

    override suspend fun deleteAll() {
        allDevices.clear()
        _flow.update { emptyList() }
    }
}
