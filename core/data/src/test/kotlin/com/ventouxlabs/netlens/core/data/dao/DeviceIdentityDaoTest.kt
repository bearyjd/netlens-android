package com.ventouxlabs.netlens.core.data.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ventouxlabs.netlens.core.data.NetLensDatabase
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Real-SQL tests for the transactional identity DAO: merge ordering, fill-only-empty, atomic insert. */
@RunWith(RobolectricTestRunner::class)
class DeviceIdentityDaoTest {

    private lateinit var db: NetLensDatabase
    private lateinit var dao: DeviceIdentityDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NetLensDatabase::class.java,
        ).build()
        dao = db.deviceIdentityDao()
    }

    @After
    fun tearDown() = db.close()

    private fun device(mac: String? = null, ip: String = "10.0.0.1", firstSeen: Long = 1, lastSeen: Long = 1) =
        KnownDeviceEntity(macAddress = mac, hostname = null, ip = ip, vendor = null, firstSeen = firstSeen, lastSeen = lastSeen)

    private fun ident(kind: String = "UPNP_UDN", value: String = "uuid:a", scopeKey: String = "") = DeviceIdentifierEntity(
        deviceId = 0, kind = kind, value = value, scopeKey = scopeKey, source = "SSDP", firstSeen = 1, lastSeen = 1,
    )

    private fun obs(value: String) = DeviceObservationEntity(
        deviceId = 0, source = "SSDP", field = "MODEL", value = value, weight = 10, detail = null, lastSeen = 1,
    )

    @Test
    fun `insertDeviceWithIdentifiers stores the row, its identifiers and observations`() = runBlocking {
        val id = dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:01"), listOf(ident()), listOf(obs("x")), dedupeByMac = true)

        assertTrue(id > 0)
        assertEquals(id, db.deviceIdentifierDao().find("UPNP_UDN", "uuid:a", "")?.deviceId)
        assertEquals(1, db.deviceObservationDao().getForDevice(id).size)
    }

    @Test
    fun `insertDeviceWithIdentifiers returns -1 and inserts nothing when an identifier is already owned`() = runBlocking {
        val first = dao.insertDeviceWithIdentifiers(device(), listOf(ident()), emptyList(), dedupeByMac = true)

        val second = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.2"), listOf(ident(value = "uuid:b"), ident()), emptyList(), dedupeByMac = true)

        assertEquals(-1L, second)
        assertEquals(first, db.deviceIdentifierDao().find("UPNP_UDN", "uuid:a", "")?.deviceId)
        assertNull(db.deviceIdentifierDao().find("UPNP_UDN", "uuid:b", ""))
        assertNull(db.knownDeviceDao().getById(first + 1))
    }

    @Test
    fun `insertDeviceWithIdentifiers dedupes on macAddress only when asked`() = runBlocking {
        dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:01"), emptyList(), emptyList(), dedupeByMac = true)

        assertEquals(-1L, dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:01", "10.0.0.2"), emptyList(), emptyList(), dedupeByMac = true))
        assertTrue(dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:01", "10.0.0.3"), emptyList(), emptyList(), dedupeByMac = false) > 0)
    }

    @Test
    fun `a failing identifier insert rolls the whole transaction back`() = runBlocking {
        var thrown = false
        try {
            // Duplicate inside one call: passes the pre-check, then violates the unique index.
            dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.9"), listOf(ident(), ident()), emptyList(), dedupeByMac = false)
        } catch (e: SQLiteConstraintException) {
            thrown = true
        }

        assertTrue(thrown)
        assertNull(db.knownDeviceDao().getByIpWithoutMac("10.0.0.9"))
        assertNull(db.deviceIdentifierDao().find("UPNP_UDN", "uuid:a", ""))
    }

    @Test
    fun `a merge that fails at the final delete rolls every earlier step back`() = runBlocking {
        val survivor = dao.insertDeviceWithIdentifiers(device(), listOf(ident(value = "uuid:s")), listOf(obs("s")), true)
        val loser = dao.insertDeviceWithIdentifiers(
            device(ip = "10.0.0.2").copy(customName = "Theirs"), listOf(ident(value = "uuid:l")), listOf(obs("l")), true,
        )
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_loser_delete BEFORE DELETE ON known_devices WHEN OLD.id = $loser " +
                "BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )

        var thrown = false
        try {
            dao.mergeDevices(survivor, loser, isKnown = true)
        } catch (e: SQLiteConstraintException) {
            thrown = true
        }

        assertTrue(thrown)
        assertEquals(loser, db.deviceIdentifierDao().find("UPNP_UDN", "uuid:l", "")?.deviceId)
        assertEquals(listOf("l"), db.deviceObservationDao().getForDevice(loser).map { it.value })
        val untouched = db.knownDeviceDao().getById(survivor)!!
        assertNull(untouched.customName)
        assertFalse(untouched.isKnown)
    }

    @Test
    fun `merge moves children before deleting the loser so the cascade cannot take them`() = runBlocking {
        val survivor = dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:01", firstSeen = 1), listOf(ident(value = "uuid:s")), listOf(obs("s")), true)
        val loser = dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:02", "10.0.0.2", firstSeen = 5), listOf(ident(value = "uuid:l")), listOf(obs("l")), true)
        db.deviceSightingDao().upsert(DeviceSightingEntity(loser, "netA", null, "10.0.0.2", 5, 5))
        db.deviceSightingDao().upsert(DeviceSightingEntity(survivor, "netB", null, "10.0.0.1", 1, 1))

        assertTrue(dao.mergeDevices(survivor, loser, isKnown = null))

        assertNull(db.knownDeviceDao().getById(loser))
        assertEquals(survivor, db.deviceIdentifierDao().find("UPNP_UDN", "uuid:l", "")?.deviceId)
        assertEquals(2, db.deviceIdentifierDao().getForDevice(survivor).size)
        assertEquals(setOf("s", "l"), db.deviceObservationDao().getForDevice(survivor).map { it.value }.toSet())
        assertEquals(setOf("netA", "netB"), db.deviceSightingDao().getForDevice(survivor).map { it.networkKey }.toSet())
    }

    @Test
    fun `merge tolerates a sighting and observation both rows already have`() = runBlocking {
        val survivor = dao.insertDeviceWithIdentifiers(device(), emptyList(), listOf(obs("same")), true)
        val loser = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.2"), emptyList(), listOf(obs("same")), true)
        db.deviceSightingDao().upsert(DeviceSightingEntity(survivor, "net", null, "10.0.0.1", 1, 1))
        db.deviceSightingDao().upsert(DeviceSightingEntity(loser, "net", null, "10.0.0.2", 2, 2))

        assertTrue(dao.mergeDevices(survivor, loser, null))

        assertEquals(1, db.deviceObservationDao().getForDevice(survivor).size)
        assertEquals(1, db.deviceSightingDao().getForDevice(survivor).size)
    }

    @Test
    fun `merge fills only empty user fields including the overrides and keeps the survivor's isKnown`() = runBlocking {
        val survivor = dao.insertDeviceWithIdentifiers(device().copy(isKnown = true, customName = "Mine", notes = "  "), emptyList(), emptyList(), true)
        val loser = dao.insertDeviceWithIdentifiers(
            device(ip = "10.0.0.2").copy(
                customName = "Theirs", notes = "n", tags = "t", location = "Garage",
                deviceTypeOverride = "NAS", osOverride = "TrueNAS", modelOverride = "M1",
            ),
            emptyList(), emptyList(), true,
        )

        dao.mergeDevices(survivor, loser, isKnown = null)

        val merged = db.knownDeviceDao().getById(survivor)!!
        assertEquals("Mine", merged.customName)
        assertEquals("n", merged.notes)
        assertEquals("t", merged.tags)
        assertEquals("Garage", merged.location)
        assertEquals("NAS", merged.deviceTypeOverride)
        assertEquals("TrueNAS", merged.osOverride)
        assertEquals("M1", merged.modelOverride)
        assertTrue(merged.isKnown)
    }

    @Test
    fun `merge applies an explicit isKnown choice`() = runBlocking {
        val survivor = dao.insertDeviceWithIdentifiers(device().copy(isKnown = true), emptyList(), emptyList(), true)
        val loser = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.2"), emptyList(), emptyList(), true)

        dao.mergeDevices(survivor, loser, isKnown = false)

        assertFalse(db.knownDeviceDao().getById(survivor)!!.isKnown)
    }

    @Test
    fun `merge with a missing row is a no-op returning false`() = runBlocking {
        val only = dao.insertDeviceWithIdentifiers(device(), listOf(ident()), emptyList(), true)

        assertFalse(dao.mergeDevices(only, only + 99, null))
        assertFalse(dao.mergeDevices(only + 99, only, null))
        assertNotNull(db.knownDeviceDao().getById(only))
        assertEquals(1, db.deviceIdentifierDao().getForDevice(only).size)
    }

    @Test
    fun `getSeenSince returns only recent rows newest first`() = runBlocking {
        dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.1", lastSeen = 10), emptyList(), emptyList(), true)
        dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.2", lastSeen = 300), emptyList(), emptyList(), true)
        dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.3", lastSeen = 200), emptyList(), emptyList(), true)

        assertEquals(listOf("10.0.0.2", "10.0.0.3"), db.knownDeviceDao().getSeenSince(100).map { it.ip })
    }

    @Test
    fun `countByKind counts a device's identifiers of one kind`() = runBlocking {
        val id = dao.insertDeviceWithIdentifiers(device(), listOf(ident(value = "a"), ident(value = "b"), ident("SMB_SERVER_GUID", "g")), emptyList(), true)

        assertEquals(2, db.deviceIdentifierDao().countByKind(id, "UPNP_UDN"))
    }
}
