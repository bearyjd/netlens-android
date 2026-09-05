package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.model.FingerprintEvidence
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceInventoryRepositoryTest {

    private fun repo(
        dao: FakeKnownDeviceDao,
        notifier: RecordingNewDeviceNotifier,
    ) = DeviceInventoryRepositoryImpl(dao, notifier)

    @Test
    fun `new device with MAC is persisted and notified once`() = runTest {
        val dao = FakeKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        repo(dao, notifier).persistScan(
            listOf(LanDevice(ip = "192.168.1.10", hostname = "phone", macAddress = "AA:BB:CC:DD:EE:01")),
            networkId = null,
        )
        val stored = dao.getByMac("AA:BB:CC:DD:EE:01")
        assertNotNull(stored)
        assertEquals("192.168.1.10", stored?.ip)
        assertEquals(1, notifier.notified.size)
    }

    @Test
    fun `re-seen device updates lastSeen and does not notify`() = runTest {
        val dao = FakeKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        dao.insertIfNew(
            KnownDeviceEntity(
                macAddress = "AA:BB:CC:DD:EE:04",
                hostname = "old", ip = "192.168.1.40", vendor = "Old",
                firstSeen = 1000L, lastSeen = 1000L,
            ),
        )
        notifier.notified.clear()
        repo(dao, notifier).persistScan(
            listOf(LanDevice(ip = "192.168.1.41", hostname = "new", macAddress = "AA:BB:CC:DD:EE:04")),
            networkId = 7L,
        )
        val updated = dao.getByMac("AA:BB:CC:DD:EE:04")
        assertEquals("192.168.1.41", updated?.ip)
        assertEquals(1000L, updated?.firstSeen)
        assertEquals(7L, updated?.networkId)
        assertEquals(0, notifier.notified.size)
    }

    @Test
    fun `mac-less device is persisted keyed by IP then upgrades in place`() = runTest {
        val dao = FakeKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        val r = repo(dao, notifier)
        r.persistScan(listOf(LanDevice(ip = "192.168.1.22", hostname = "d", macAddress = null)), networkId = null)
        r.persistScan(listOf(LanDevice(ip = "192.168.1.22", hostname = "d", macAddress = "AA:BB:CC:DD:EE:22")), networkId = null)
        assertEquals(1, dao.allDevices.size)
        assertNotNull(dao.getByMac("AA:BB:CC:DD:EE:22"))
        assertNull(dao.getByIpWithoutMac("192.168.1.22"))
    }

    @Test
    fun `insert tags the network id`() = runTest {
        val dao = FakeKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        repo(dao, notifier).persistScan(
            listOf(LanDevice(ip = "192.168.1.5", macAddress = "AA:BB:CC:DD:EE:05")),
            networkId = 42L,
        )
        assertEquals(42L, dao.getByMac("AA:BB:CC:DD:EE:05")?.networkId)
    }

    // --- confidence-aware cross-scan merge (the actual cross-subnet-relevant scenario) ---

    @Test
    fun `second scan with higher confidence upgrades deviceType and osGuess`() = runTest {
        val dao = FakeKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        val r = repo(dao, notifier)
        r.persistScan(
            listOf(
                LanDevice(
                    ip = "192.168.1.70", macAddress = "AA:BB:CC:DD:EE:70",
                    deviceType = "Phone", osGuess = "iOS", fingerprintConfidence = 40,
                    fingerprintEvidence = listOf("hostname: some-phone"),
                ),
            ),
            networkId = null,
        )
        r.persistScan(
            listOf(
                LanDevice(
                    ip = "192.168.1.71", macAddress = "AA:BB:CC:DD:EE:70",
                    deviceType = "Chromecast", osGuess = "Android", fingerprintConfidence = 90,
                    fingerprintEvidence = listOf("mDNS: googlecast"),
                ),
            ),
            networkId = null,
        )

        val updated = dao.getByMac("AA:BB:CC:DD:EE:70")
        assertEquals("Chromecast", updated?.deviceType)
        assertEquals("Android", updated?.osGuess)
        assertEquals(90, updated?.fingerprintConfidence)
    }

    @Test
    fun `second scan with lower confidence does not downgrade deviceType or osGuess`() = runTest {
        val dao = FakeKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        val r = repo(dao, notifier)
        r.persistScan(
            listOf(
                LanDevice(
                    ip = "192.168.1.72", macAddress = "AA:BB:CC:DD:EE:72",
                    deviceType = "Chromecast", osGuess = "Android", fingerprintConfidence = 90,
                    fingerprintEvidence = listOf("mDNS: googlecast"),
                ),
            ),
            networkId = null,
        )
        r.persistScan(
            listOf(
                LanDevice(
                    ip = "192.168.1.73", macAddress = "AA:BB:CC:DD:EE:72",
                    deviceType = "Phone", osGuess = "iOS", fingerprintConfidence = 40,
                    fingerprintEvidence = listOf("hostname: some-phone"),
                ),
            ),
            networkId = null,
        )

        val updated = dao.getByMac("AA:BB:CC:DD:EE:72")
        assertEquals("Chromecast", updated?.deviceType)
        assertEquals("Android", updated?.osGuess)
        assertEquals(90, updated?.fingerprintConfidence)
    }

    @Test
    fun `fingerprintEvidence unions across scans rather than replacing`() = runTest {
        val dao = FakeKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        val r = repo(dao, notifier)
        r.persistScan(
            listOf(
                LanDevice(
                    ip = "192.168.1.74", macAddress = "AA:BB:CC:DD:EE:74",
                    fingerprintConfidence = 90, fingerprintEvidence = listOf("mDNS: googlecast"),
                ),
            ),
            networkId = null,
        )
        r.persistScan(
            listOf(
                LanDevice(
                    ip = "192.168.1.74", macAddress = "AA:BB:CC:DD:EE:74",
                    fingerprintConfidence = 80, fingerprintEvidence = listOf("port 8008"),
                ),
            ),
            networkId = null,
        )

        assertEquals(
            listOf("mDNS: googlecast", "port 8008"),
            FingerprintEvidence.parse(dao.getByMac("AA:BB:CC:DD:EE:74")?.fingerprintEvidence),
        )
    }

    // Regression: evidence used to be persisted as a raw ", "-joined string and split back on
    // that same delimiter — an SSDP friendly name or mDNS label containing a literal comma would
    // fracture into bogus extra entries on the next merge. FingerprintEvidence strips the
    // separator at write time instead, so a comma inside an entry survives round-trip.
    @Test
    fun `evidence containing a comma survives the merge round-trip intact`() = runTest {
        val dao = FakeKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        val r = repo(dao, notifier)
        r.persistScan(
            listOf(
                LanDevice(
                    ip = "192.168.1.76", macAddress = "AA:BB:CC:DD:EE:76",
                    fingerprintConfidence = 90,
                    fingerprintEvidence = listOf("SSDP: Marantz SR7013, Zone 2"),
                ),
            ),
            networkId = null,
        )
        r.persistScan(
            listOf(
                LanDevice(
                    ip = "192.168.1.76", macAddress = "AA:BB:CC:DD:EE:76",
                    fingerprintConfidence = 40,
                    fingerprintEvidence = listOf("hostname: marantz"),
                ),
            ),
            networkId = null,
        )

        val evidence = FingerprintEvidence.parse(dao.getByMac("AA:BB:CC:DD:EE:76")?.fingerprintEvidence)
        assertEquals(2, evidence.size)
        assertTrue(evidence.any { it.startsWith("SSDP: Marantz SR7013") })
    }

    // Regression: a second scan winning overall on confidence used to overwrite BOTH deviceType
    // and osGuess, so a candidate with an opinion on only one field (e.g. SSDP: deviceType but
    // not osGuess) silently erased the other field's existing value instead of leaving it alone.
    @Test
    fun `higher-confidence scan's null field does not erase the existing value for that field`() = runTest {
        val dao = FakeKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        val r = repo(dao, notifier)
        r.persistScan(
            listOf(
                LanDevice(
                    ip = "192.168.1.77", macAddress = "AA:BB:CC:DD:EE:77",
                    deviceType = null, osGuess = "Linux", fingerprintConfidence = 40,
                    fingerprintEvidence = listOf("hostname: some-router"),
                ),
            ),
            networkId = null,
        )
        r.persistScan(
            listOf(
                LanDevice(
                    ip = "192.168.1.77", macAddress = "AA:BB:CC:DD:EE:77",
                    deviceType = "Router", osGuess = null, fingerprintConfidence = 90,
                    fingerprintEvidence = listOf("SSDP: WAP Router"),
                ),
            ),
            networkId = null,
        )

        val updated = dao.getByMac("AA:BB:CC:DD:EE:77")
        assertEquals("Router", updated?.deviceType)
        assertEquals("Linux", updated?.osGuess)
        assertEquals(90, updated?.fingerprintConfidence)
    }

    @Test
    fun `a device with no prior classification still picks up a fresh low-confidence signal`() = runTest {
        val dao = FakeKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        val r = repo(dao, notifier)
        r.persistScan(
            listOf(LanDevice(ip = "192.168.1.75", macAddress = "AA:BB:CC:DD:EE:75")),
            networkId = null,
        )
        r.persistScan(
            listOf(
                LanDevice(
                    ip = "192.168.1.75", macAddress = "AA:BB:CC:DD:EE:75",
                    deviceType = "Printer", osGuess = null, fingerprintConfidence = 40,
                    fingerprintEvidence = listOf("hostname: office-printer"),
                ),
            ),
            networkId = null,
        )

        val updated = dao.getByMac("AA:BB:CC:DD:EE:75")
        assertEquals("Printer", updated?.deviceType)
        assertEquals(40, updated?.fingerprintConfidence)
    }
}
