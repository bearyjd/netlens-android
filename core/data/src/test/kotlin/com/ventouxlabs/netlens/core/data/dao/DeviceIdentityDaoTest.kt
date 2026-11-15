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
    fun `a confirmed merge fills only empty user fields including the overrides`() = runBlocking {
        val survivor = dao.insertDeviceWithIdentifiers(device().copy(isKnown = true, customName = "Mine", notes = "  "), emptyList(), emptyList(), true)
        val loser = dao.insertDeviceWithIdentifiers(
            device(ip = "10.0.0.2").copy(
                customName = "Theirs", notes = "n", tags = "t", location = "Garage",
                deviceTypeOverride = "NAS", osOverride = "TrueNAS", modelOverride = "M1",
            ),
            emptyList(), emptyList(), true,
        )

        dao.mergeDevices(survivor, loser, isKnown = true) // user-confirmed: explicit trust choice

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

    @Test
    fun `a confirmed merge leaves a fully user-authored survivor row unchanged`() = runBlocking {
        fun mine() = device().copy(
            customName = "mine", tags = "mine", notes = "mine", location = "mine",
            deviceTypeOverride = "mine", osOverride = "mine", modelOverride = "mine",
        )
        val survivor = dao.insertDeviceWithIdentifiers(mine(), emptyList(), emptyList(), true)
        val loser = dao.insertDeviceWithIdentifiers(
            mine().copy(
                ip = "10.0.0.2", customName = "other", tags = "other", notes = "other", location = "other",
                deviceTypeOverride = "other", osOverride = "other", modelOverride = "other",
            ),
            emptyList(), emptyList(), true,
        )
        val before = db.knownDeviceDao().getById(survivor)!!

        assertTrue(dao.mergeDevices(survivor, loser, isKnown = false))

        assertEquals(before, db.knownDeviceDao().getById(survivor))
    }

    @Test
    fun `an automatic merge is refused inside the transaction when both rows now carry user details`() = runBlocking {
        val survivor = dao.insertDeviceWithIdentifiers(device(), listOf(ident(value = "uuid:s")), emptyList(), true)
        val loser = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.2"), listOf(ident(value = "uuid:l")), emptyList(), true)
        db.knownDeviceDao().setCustomName(survivor, "named")
        db.knownDeviceDao().setCustomName(loser, "also named")

        assertFalse(dao.mergeDevices(survivor, loser, isKnown = null))

        assertNotNull(db.knownDeviceDao().getById(loser))
        assertEquals(loser, db.deviceIdentifierDao().find("UPNP_UDN", "uuid:l", "")?.deviceId)
    }

    @Test
    fun `an automatic merge is refused when the user marked the pair DISTINCT or the trust differs`() = runBlocking {
        val a = dao.insertDeviceWithIdentifiers(device(), emptyList(), emptyList(), true)
        val b = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.2"), emptyList(), emptyList(), true)
        db.deviceMergeDecisionDao().setDistinct(a, b, 1)
        assertFalse(dao.mergeDevices(a, b, isKnown = null))

        val c = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.3").copy(isKnown = true), emptyList(), emptyList(), true)
        val d = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.4"), emptyList(), emptyList(), true)
        assertFalse(dao.mergeDevices(c, d, isKnown = null))
        assertNotNull(db.knownDeviceDao().getById(d))
    }

    @Test
    fun `an automatic merge whose loser has a DISTINCT decision with a third device is refused and the decision survives`() = runBlocking {
        val survivor = dao.insertDeviceWithIdentifiers(device(), emptyList(), emptyList(), true)
        val loser = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.2"), emptyList(), emptyList(), true)
        val third = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.3"), emptyList(), emptyList(), true)
        db.deviceMergeDecisionDao().setDistinct(loser, third, 1)

        assertFalse(dao.mergeDevices(survivor, loser, isKnown = null))
        assertEquals("DISTINCT", db.deviceMergeDecisionDao().get(loser, third)?.decision)
        // The survivor touching a DISTINCT row is fine: that row survives with it.
        assertTrue(dao.mergeDevices(loser, survivor, isKnown = null))
        assertEquals("DISTINCT", db.deviceMergeDecisionDao().get(loser, third)?.decision)
    }

    @Test
    fun `touchesDistinct sees a DISTINCT decision on either side and ignores suggestions`() = runBlocking {
        val decisions = db.deviceMergeDecisionDao()
        val ids = listOf(1L, 2L, 3L).map { dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.$it"), emptyList(), emptyList(), true) }
        decisions.suggest(com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity(ids[0], ids[1], "SUGGESTED", 60, null, 1))
        assertFalse(decisions.touchesDistinct(listOf(ids[0], ids[1])))

        decisions.setDistinct(ids[1], ids[2], 1)
        assertTrue(decisions.touchesDistinct(listOf(ids[1])))
        assertTrue(decisions.touchesDistinct(listOf(ids[2])))
        assertFalse(decisions.touchesDistinct(listOf(ids[0])))
    }

    @Test
    fun `insertBelowCap inserts up to the cap then refuses`() = runBlocking {
        val id = dao.insertDeviceWithIdentifiers(device(), emptyList(), emptyList(), true)
        val identifiers = db.deviceIdentifierDao()

        assertTrue(identifiers.insertBelowCap(ident(value = "a").copy(deviceId = id), cap = 2) > 0)
        assertTrue(identifiers.insertBelowCap(ident(value = "b").copy(deviceId = id), cap = 2) > 0)
        assertEquals(-1L, identifiers.insertBelowCap(ident(value = "c").copy(deviceId = id), cap = 2))
        assertEquals(2, identifiers.countByKind(id, "UPNP_UDN"))
    }

    @Test
    fun `two inserts of the same MAC both succeed with different ids since v19`() = runBlocking {
        val known = db.knownDeviceDao()

        val first = known.insertIfNew(device("AA:BB:CC:00:00:01"))
        val second = known.insertIfNew(device("AA:BB:CC:00:00:01", ip = "10.0.0.2"))

        assertTrue(first > 0 && second > 0)
        assertTrue(first != second)
    }

    @Test
    fun `trimToStrongest evicts the lowest weight first, then the oldest, and only for that device`() = runBlocking {
        val devices = db.knownDeviceDao()
        val a = devices.insertIfNew(device(ip = "10.0.0.1"))
        val b = devices.insertIfNew(device(ip = "10.0.0.2"))
        val dao = db.deviceObservationDao()
        fun o(device: Long, value: String, weight: Int, lastSeen: Long) = DeviceObservationEntity(
            deviceId = device, source = "SSDP", field = "MODEL", value = value, weight = weight, detail = null, lastSeen = lastSeen,
        )
        dao.upsert(o(a, "weak-new", weight = 1, lastSeen = 99))
        dao.upsert(o(a, "strong-old", weight = 90, lastSeen = 1))
        dao.upsert(o(a, "mid-old", weight = 50, lastSeen = 1))
        dao.upsert(o(a, "mid-new", weight = 50, lastSeen = 5))
        dao.upsert(o(b, "other", weight = 0, lastSeen = 1))

        dao.trimToStrongest(a, keep = 2)

        assertEquals(setOf("strong-old", "mid-new"), dao.getForDevice(a).map { it.value }.toSet())
        assertEquals(1, dao.getForDevice(b).size)
    }

    @Test
    fun `trimToStrongest breaks equal weights by keeping the newer row`() = runBlocking {
        val a = db.knownDeviceDao().insertIfNew(device(ip = "10.0.0.1"))
        val dao = db.deviceObservationDao()
        fun o(value: String, lastSeen: Long) = DeviceObservationEntity(
            deviceId = a, source = "SSDP", field = "MODEL", value = value, weight = 5, detail = null, lastSeen = lastSeen,
        )
        dao.upsert(o("older", 1))
        dao.upsert(o("newer", 2))

        dao.trimToStrongest(a, keep = 1)

        assertEquals(listOf("newer"), dao.getForDevice(a).map { it.value })
    }

    @Test
    fun `a transaction block that writes a device field and an observation then throws leaves both untouched`() = runBlocking {
        val devices = db.knownDeviceDao()
        val id = devices.insertIfNew(device(ip = "10.0.0.1"))
        val before = devices.getById(id)

        val failure = runCatching {
            com.ventouxlabs.netlens.core.data.RoomTransactionRunner(db).run {
                devices.setNetworkId(id, 99L)
                db.deviceObservationDao().upsert(obs("x").copy(deviceId = id))
                error("boom")
            }
        }

        assertTrue(failure.isFailure)
        assertEquals(before, devices.getById(id))
        assertTrue(db.deviceObservationDao().getForDevice(id).isEmpty())
    }
}
