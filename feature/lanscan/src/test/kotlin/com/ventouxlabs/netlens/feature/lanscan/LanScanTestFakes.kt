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
import com.ventouxlabs.netlens.feature.lanscan.engine.FakeScanLocationProvider
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
import com.ventouxlabs.netlens.core.scan.engine.FakePortScanner

/**
 * Shared fakes for `:feature:lanscan`'s ViewModel tests. Extracted from
 * `LanScanBuildExportTextTest` when a second test file needed them — `internal` rather than
 * `private` so the next one does not make an eleventh copy.
 *
 * **Five of these shadow doubles that already exist in `core:scan-testing`** —
 * `FakeSubnetScanner`, `FakeLanMdnsScanner`, `FakeArpTableReader`, `FakeSsdpScanner` and
 * `FakeNetBiosProber`. The module already depends on that artifact (it imports
 * `FakePortScanner` from it). Deleting these in favour of the shared ones is a worthwhile
 * follow-up; it was left out of the location change to keep that diff about location.
 */
internal class FakeSubnetScanner : SubnetScanner {
    var devices: List<LanDevice> = emptyList()
    override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> =
        flowOf(*devices.toTypedArray())
}

internal class FakeLanMdnsScanner : LanMdnsScanner {
    override fun discover(timeoutMs: Long): Flow<LanDevice> = emptyFlow()
}

internal class FakeDeviceFingerprinter : DeviceFingerprinter {
    override suspend fun fingerprint(device: LanDevice): LanDevice = device
    override fun classifyFromServices(services: List<String>): Pair<String?, String?> = null to null
    override fun classifyFromSsdp(ssdpDevice: SsdpDevice): Pair<String?, String?> = null to null
    override fun classifyFromNetBios(info: NetBiosInfo): String? = null
    override fun fingerprintWithPorts(device: LanDevice, openPorts: List<Int>): PortFingerprint =
        PortFingerprint(null, null, emptyList())
}

internal class FakeSsdpScanner : SsdpScanner {
    override fun discover(timeoutMs: Long): Flow<SsdpDevice> = emptyFlow()
}

internal class FakeNetBiosProber : NetBiosProber {
    override suspend fun probe(ip: String): NetBiosInfo? = null
}

internal class FakeArpTableReader : ArpTableReader {
    override suspend fun getMacForIp(ip: String): String? = null
    override suspend fun getAll(): Map<String, String> = emptyMap()
    override fun invalidateCache() {}
}

internal class FakeNetworkInterfaceProvider : NetworkInterfaceProvider {
    override fun getNetworkInterfaces(): List<NetworkInterfaceInfo> = emptyList()
    override fun getActiveNetworkInterface(): NetworkInterfaceInfo? = null
}

internal class FakeLanScanHistoryDao : LanScanHistoryDao {
    override fun getRecent(limit: Int): Flow<List<LanScanHistoryEntry>> = flowOf(emptyList())
    override fun search(query: String, limit: Int): Flow<List<LanScanHistoryEntry>> = flowOf(emptyList())
    override suspend fun getById(id: Long): LanScanHistoryEntry? = null
    override suspend fun insert(entry: LanScanHistoryEntry) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteOlderThan(before: Long) {}
    override suspend fun deleteAll() {}
}

internal class FakeKnownDeviceDao : KnownDeviceDao {
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

internal class FakeNewDeviceNotifier : NewDeviceNotifier {
    override fun createChannel() {}
    override fun notify(device: KnownDeviceEntity) {}
}
