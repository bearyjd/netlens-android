package com.ventouxlabs.netlens.feature.lanscan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
 * The five engine doubles that used to live here — `FakeSubnetScanner`, `FakeLanMdnsScanner`,
 * `FakeArpTableReader`, `FakeSsdpScanner`, `FakeNetBiosProber` — are gone; they shadowed
 * `core:scan-testing`, which this module already depends on, and every local copy was the
 * weaker one (no `error` seam, hardcoded `emptyFlow()`, always-null lookups). Import them from
 * `com.ventouxlabs.netlens.core.scan.engine` instead. Anything two modules both need belongs
 * there, not here.
 *
 * What remains has no shared equivalent yet. If you add one, move it rather than copying it.
 */
internal class FakeDeviceFingerprinter : DeviceFingerprinter {
    override suspend fun fingerprint(device: LanDevice): LanDevice = device
    override fun classifyFromServices(services: List<String>): Pair<String?, String?> = null to null
    override fun classifyFromSsdp(ssdpDevice: SsdpDevice): Pair<String?, String?> = null to null
    override fun classifyFromNetBios(info: NetBiosInfo): String? = null
    override fun fingerprintWithPorts(device: LanDevice, openPorts: List<Int>): PortFingerprint =
        PortFingerprint(null, null, emptyList())
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

internal class FakeNewDeviceNotifier : NewDeviceNotifier {
    override fun createChannel() {}
    override fun notify(device: KnownDeviceEntity) {}
}
