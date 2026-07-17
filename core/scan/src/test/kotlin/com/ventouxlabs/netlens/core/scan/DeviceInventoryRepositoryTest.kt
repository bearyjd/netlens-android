package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeviceInventoryRepositoryTest {

    private fun repo(
        dao: InMemoryKnownDeviceDao,
        notifier: RecordingNewDeviceNotifier,
    ) = DeviceInventoryRepositoryImpl(dao, notifier)

    @Test
    fun `new device with MAC is persisted and notified once`() = runTest {
        val dao = InMemoryKnownDeviceDao()
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
        val dao = InMemoryKnownDeviceDao()
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
        val dao = InMemoryKnownDeviceDao()
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
        val dao = InMemoryKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        repo(dao, notifier).persistScan(
            listOf(LanDevice(ip = "192.168.1.5", macAddress = "AA:BB:CC:DD:EE:05")),
            networkId = 42L,
        )
        assertEquals(42L, dao.getByMac("AA:BB:CC:DD:EE:05")?.networkId)
    }
}
