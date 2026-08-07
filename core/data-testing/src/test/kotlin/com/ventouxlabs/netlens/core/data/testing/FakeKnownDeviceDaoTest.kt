package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Four separate `KnownDeviceDao` doubles existed before this one and had drifted apart. These
 * pin the behaviours the weak copies got wrong, so they cannot come back:
 *
 *  - `feature:lanscan`'s `FakeKnownDeviceDao` was **inert** — every write a no-op, every read
 *    empty, `insertIfNew` returning a constant without storing. Any persistence test written
 *    against it would have passed regardless of the real `@Query`.
 *  - `feature:devices`' copy returned **every** row from `search()`, ignoring the query, and its
 *    `insertIfNew` neither deduped nor assigned an id.
 */
class FakeKnownDeviceDaoTest {

    private fun device(
        mac: String? = null,
        hostname: String? = null,
        ip: String = "192.168.1.2",
        id: Long = 0,
    ) = KnownDeviceEntity(id = id, macAddress = mac, hostname = hostname, ip = ip, vendor = null)

    @Test
    fun `writes are actually stored — the inert copy's failure`() = runTest {
        val dao = FakeKnownDeviceDao()
        val id = dao.insertIfNew(device(mac = "AA"))

        assertNotNull(dao.getByMac("AA"))
        assertEquals(id, dao.getById(id)?.id)
        assertEquals(1, dao.allDevices.size)
    }

    @Test
    fun `insertIfNew dedupes on MAC and reports the duplicate`() = runTest {
        val dao = FakeKnownDeviceDao()
        val first = dao.insertIfNew(device(mac = "AA"))
        val second = dao.insertIfNew(device(mac = "AA"))

        assertTrue(first > 0)
        assertEquals(FakeKnownDeviceDao.DUPLICATE_ROW, second)
        assertEquals(1, dao.allDevices.size)
    }

    @Test
    fun `insertIfNew assigns sequential ids and ignores the caller's`() = runTest {
        val dao = FakeKnownDeviceDao()
        val a = dao.insertIfNew(device(mac = "AA", id = 99))
        val b = dao.insertIfNew(device(mac = "BB", id = 99))

        assertEquals(1L, a)
        assertEquals(2L, b)
    }

    @Test
    fun `seed preserves the caller's id, unlike insertIfNew`() = runTest {
        val dao = FakeKnownDeviceDao()
        dao.seed(device(mac = "AA", id = 5), device(mac = "BB", id = 7))

        assertEquals("AA", dao.byId(5)?.macAddress)
        assertEquals("BB", dao.byId(7)?.macAddress)
    }

    @Test
    fun `search filters instead of returning everything`() = runTest {
        val dao = FakeKnownDeviceDao()
        dao.seed(
            device(mac = "AA", hostname = "printer", ip = "192.168.1.2", id = 1),
            device(mac = "BB", hostname = "laptop", ip = "10.0.0.9", id = 2),
        )

        assertEquals(listOf("printer"), dao.search("print").first().map { it.hostname })
        assertEquals(listOf("laptop"), dao.search("10.0.0").first().map { it.hostname })
        assertTrue(dao.search("nothing-matches").first().isEmpty())
    }

    @Test
    fun `user detail edits persist and are observable`() = runTest {
        val dao = FakeKnownDeviceDao()
        dao.seed(device(mac = "AA", id = 1))

        dao.updateUserDetails(1, customName = "Living Room TV", tags = "media", notes = null, location = "Lounge")

        val saved = dao.byId(1)
        assertEquals("Living Room TV", saved?.customName)
        assertEquals("media", saved?.tags)
        assertEquals("Lounge", saved?.location)
        assertEquals("Living Room TV", dao.getAllDevices().first().single().customName)
    }

    @Test
    fun `getAllDevices emits the current rows after each write`() = runTest {
        val dao = FakeKnownDeviceDao()
        assertTrue(dao.getAllDevices().first().isEmpty())

        val id = dao.insertIfNew(device(mac = "AA"))
        assertEquals(1, dao.getAllDevices().first().size)

        dao.delete(id)
        assertTrue(dao.getAllDevices().first().isEmpty())
    }

    @Test
    fun `getByIpWithoutMac only matches rows that have no MAC`() = runTest {
        val dao = FakeKnownDeviceDao()
        dao.seed(device(mac = "AA", ip = "192.168.1.5", id = 1))
        assertNull(dao.getByIpWithoutMac("192.168.1.5"))

        dao.seed(device(mac = null, ip = "192.168.1.6", id = 2))
        assertEquals(2L, dao.getByIpWithoutMac("192.168.1.6")?.id)
    }

    @Test
    fun `deleteAll clears rows and the flow`() = runTest {
        val dao = FakeKnownDeviceDao()
        dao.seed(device(mac = "AA", id = 1), device(mac = "BB", id = 2))

        dao.deleteAll()

        assertTrue(dao.allDevices.isEmpty())
        assertTrue(dao.getAllDevices().first().isEmpty())
    }
}
