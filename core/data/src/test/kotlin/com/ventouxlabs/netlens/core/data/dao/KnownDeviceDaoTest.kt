package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ventouxlabs.netlens.core.data.NetLensDatabase
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises [KnownDeviceDao]'s real `@Query` statements against a real Room database under
 * Robolectric (real Context, real Android SQLite) — see [AndroidRobolectricConventionPlugin] for
 * why this is the one DAO in the repo tested this way instead of against a hand-written fake.
 * Every other DAO test in this repo runs against a fake, which can agree with a broken SQL string;
 * this is the one place the SQL itself is checked.
 */
@RunWith(RobolectricTestRunner::class)
class KnownDeviceDaoTest {

    private lateinit var database: NetLensDatabase
    private lateinit var dao: KnownDeviceDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NetLensDatabase::class.java,
        ).build()
        dao = database.knownDeviceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleDevice() = KnownDeviceEntity(
        macAddress = "AA:BB:CC:DD:EE:FF",
        hostname = "printer.local",
        ip = "192.168.1.50",
        vendor = "Acme",
        deviceType = "printer",
        osGuess = "embedded",
        customName = "Old Name",
        tags = "old,tags",
        notes = "old notes",
        location = "Garage",
    )

    // The reason this test file exists: updateLastSeen (scan-derived columns) and
    // updateUserDetails (user-authored columns) must never touch each other's columns, or a
    // re-scan silently clobbers what the user typed. This was previously enforced only by a
    // code comment on KnownDeviceDao.kt.
    @Test
    fun `updateLastSeen does not touch user-authored columns`() = runBlocking {
        val id = dao.insertIfNew(sampleDevice())

        dao.updateLastSeen(
            id = id,
            hostname = "new-hostname.local",
            ip = "192.168.1.99",
            vendor = "NewVendor",
            lastSeen = 999L,
            deviceType = "router",
            osGuess = "linux",
        )

        val row = dao.getById(id)
        requireNotNull(row)
        assertEquals("new-hostname.local", row.hostname)
        assertEquals("192.168.1.99", row.ip)
        assertEquals("NewVendor", row.vendor)
        assertEquals("router", row.deviceType)
        assertEquals("linux", row.osGuess)
        // User-authored columns must survive untouched.
        assertEquals("Old Name", row.customName)
        assertEquals("old,tags", row.tags)
        assertEquals("old notes", row.notes)
        assertEquals("Garage", row.location)
    }

    @Test
    fun `updateUserDetails does not touch scan-derived columns`() = runBlocking {
        val id = dao.insertIfNew(sampleDevice())

        dao.updateUserDetails(
            id = id,
            customName = "New Name",
            tags = "new,tags",
            notes = "new notes",
            location = "Kids' room",
        )

        val row = dao.getById(id)
        requireNotNull(row)
        assertEquals("New Name", row.customName)
        assertEquals("new,tags", row.tags)
        assertEquals("new notes", row.notes)
        assertEquals("Kids' room", row.location)
        // Scan-derived columns must survive untouched.
        assertEquals("printer.local", row.hostname)
        assertEquals("192.168.1.50", row.ip)
        assertEquals("Acme", row.vendor)
        assertEquals("printer", row.deviceType)
        assertEquals("embedded", row.osGuess)
    }

    @Test
    fun `search matches hostname, ip, vendor and mac substrings`() = runBlocking {
        val printer = sampleDevice()
        val router = KnownDeviceEntity(
            macAddress = "11:22:33:44:55:66",
            hostname = "router-main",
            ip = "192.168.1.1",
            vendor = "Netgear",
        )
        dao.insertIfNew(printer)
        dao.insertIfNew(router)

        assertEquals(listOf("printer.local"), dao.search("print").first().map { it.hostname })
        assertEquals(listOf("router-main"), dao.search("Netgear").first().map { it.hostname })
        // Substring match: 192.168.1.50 does not contain "192.168.1.1", so only the router matches.
        assertEquals(listOf("router-main"), dao.search("192.168.1.1").first().map { it.hostname })
        assertEquals(emptyList<String>(), dao.search("nonexistent").first().map { it.hostname })
        // Mid-string only — distinguishes a real substring LIKE '%x%' from a prefix LIKE 'x%'
        // mutation, which the other assertions above don't catch (every match here also happens
        // to start with the query).
        assertEquals(listOf("printer.local"), dao.search("inter").first().map { it.hostname })
    }

    // Fallback identity: a mac-less device is matched by IP so a re-scan updates the existing
    // row instead of creating a duplicate. Must not match a row that already has a MAC.
    @Test
    fun `getByIpWithoutMac only matches rows with a null mac`() = runBlocking {
        val macLess = KnownDeviceEntity(macAddress = null, hostname = null, ip = "192.168.1.77", vendor = null)
        val macKnown = KnownDeviceEntity(macAddress = "AA:AA:AA:AA:AA:AA", hostname = null, ip = "192.168.1.78", vendor = null)
        dao.insertIfNew(macLess)
        dao.insertIfNew(macKnown)

        val foundMacLess = dao.getByIpWithoutMac("192.168.1.77")
        assertEquals("192.168.1.77", foundMacLess?.ip)

        val foundMacKnown = dao.getByIpWithoutMac("192.168.1.78")
        assertNull(foundMacKnown)
    }
}
