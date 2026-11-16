package com.ventouxlabs.netlens.core.data.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ventouxlabs.netlens.core.data.NetLensDatabase
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.MergeKind
import com.ventouxlabs.netlens.core.data.model.MergeLogEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Real-SQL tests for the merge_log audit row `DeviceIdentityDao.mergeDevices` writes. */
@RunWith(RobolectricTestRunner::class)
class MergeLogTest {

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

    private fun device(ip: String) =
        KnownDeviceEntity(macAddress = null, hostname = null, ip = ip, vendor = null, firstSeen = 1, lastSeen = 1)

    private fun ident(value: String) = DeviceIdentifierEntity(
        deviceId = 0, kind = "UPNP_UDN", value = value, scopeKey = "", source = "SSDP", firstSeen = 1, lastSeen = 1,
    )

    private suspend fun insert(entity: KnownDeviceEntity, vararg identifiers: DeviceIdentifierEntity): Long =
        dao.insertDeviceWithIdentifiers(entity, identifiers.toList(), emptyList(), dedupeByMac = false)

    private suspend fun pair(n: Int): Pair<Long, Long> =
        insert(device("10.1.$n.1")) to insert(device("10.1.$n.2"))

    @Test
    fun `a user merge writes exactly one row with the snapshots taken before the merge`() = runBlocking {
        val survivor = insert(device("10.0.0.1").copy(customName = "Mine", notes = null))
        val loser = insert(
            device("10.0.0.2").copy(macAddress = "AA:BB:CC:00:00:02", hostname = "nas", customName = "Theirs", notes = "n", isKnown = true),
            ident("uuid:l"),
        )
        db.deviceSightingDao().upsert(DeviceSightingEntity(loser, "netA", "Home", "10.0.0.2", 3, 4))
        db.deviceMergeDecisionDao().suggest(DeviceMergeDecisionEntity(survivor, loser, "SUGGESTED", 70, "MAC,NAME", 1))

        assertTrue(dao.mergeDevices(survivor, loser, isKnown = true, kind = MergeKind.USER))

        val log = mergeLog(db).single()
        assertEquals(survivor, log.survivorId)
        assertEquals(loser, log.loserId)
        assertEquals("USER", log.kind)
        assertFalse(log.survivorIsKnownBefore)
        assertEquals(70, log.decisionScore)
        assertEquals("MAC,NAME", log.decisionReasons)
        assertEquals(1, log.snapshotVersion)
        assertTrue(log.mergedAt > 0)
        val row = JSONObject(log.loserRowJson)
        assertEquals(loser, row.getLong("id"))
        assertEquals("nas", row.getString("hostname"))
        assertEquals("AA:BB:CC:00:00:02", row.getString("macAddress"))
        assertTrue(row.getBoolean("isKnown"))
        assertTrue(row.isNull("vendor"))
        val idents = JSONArray(log.loserIdentifiersJson)
        assertEquals(1, idents.length())
        assertEquals("uuid:l", idents.getJSONObject(0).getString("value"))
        assertEquals(loser, idents.getJSONObject(0).getLong("deviceId"))
        val sightings = JSONArray(log.loserSightingsJson)
        assertEquals("Home", sightings.getJSONObject(0).getString("networkLabel"))
        assertEquals("10.0.0.2", sightings.getJSONObject(0).getString("lastIp"))
        val before = JSONObject(log.survivorUserFieldsBeforeJson)
        assertEquals("Mine", before.getString("customName"))
        assertTrue(before.has("notes"))
        assertTrue(before.isNull("notes"))
    }

    @Test
    fun `an automatic merge is logged as AUTO with no decision row`() = runBlocking {
        val (survivor, loser) = pair(0)

        assertTrue(dao.mergeDevices(survivor, loser, isKnown = null, kind = MergeKind.AUTO))

        val log = mergeLog(db).single()
        assertEquals("AUTO", log.kind)
        assertEquals(null, log.decisionScore)
        assertEquals(null, log.decisionReasons)
        assertEquals("[]", log.loserIdentifiersJson)
        assertEquals("[]", log.loserSightingsJson)
    }

    @Test
    fun `the loser row snapshot carries every known_devices column`() = runBlocking {
        val (survivor, loser) = pair(0)

        dao.mergeDevices(survivor, loser, isKnown = null, kind = MergeKind.AUTO)

        val columns = db.openHelper.readableDatabase.query("PRAGMA table_info(known_devices)").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }
        assertEquals(columns, JSONObject(mergeLog(db).single().loserRowJson).keys().asSequence().toSet())
    }

    @Test
    fun `hostile text round-trips byte-exact through every snapshot`() = runBlocking {
        val hostile = "line1\nline2 \u202Eevil\u202C \u2028sep \"quoted\" back\\slash {\"x\":[1]} </script>"
        val longHost = "h".repeat(4096)
        val survivor = insert(device("10.0.0.1").copy(notes = hostile))
        val loser = insert(
            device("10.0.0.2").copy(hostname = longHost, vendor = hostile, fingerprintEvidence = hostile, customName = hostile),
            ident(hostile),
        )
        db.deviceSightingDao().upsert(DeviceSightingEntity(loser, hostile, hostile, "10.0.0.2", 1, 1))

        dao.mergeDevices(survivor, loser, isKnown = false, kind = MergeKind.USER)

        val log = mergeLog(db).single()
        val row = JSONObject(log.loserRowJson)
        assertEquals(longHost, row.getString("hostname"))
        assertEquals(hostile, row.getString("vendor"))
        assertEquals(hostile, row.getString("fingerprintEvidence"))
        assertEquals(hostile, row.getString("customName"))
        assertEquals(hostile, JSONArray(log.loserIdentifiersJson).getJSONObject(0).getString("value"))
        assertEquals(hostile, JSONArray(log.loserSightingsJson).getJSONObject(0).getString("networkKey"))
        assertEquals(hostile, JSONArray(log.loserSightingsJson).getJSONObject(0).getString("networkLabel"))
        assertEquals(hostile, JSONObject(log.survivorUserFieldsBeforeJson).getString("notes"))
    }

    @Test
    fun `every refused merge writes nothing`() = runBlocking {
        val a = insert(device("10.0.0.1"))
        val b = insert(device("10.0.0.2"))
        assertFalse(dao.mergeDevices(a, a, isKnown = true, kind = MergeKind.USER))
        assertFalse(dao.mergeDevices(a, a + 99, isKnown = null, kind = MergeKind.AUTO))
        assertFalse(dao.mergeDevices(a + 99, a, isKnown = null, kind = MergeKind.AUTO))
        db.deviceMergeDecisionDao().setDistinct(a, b, 1)
        assertFalse(dao.mergeDevices(a, b, isKnown = null, kind = MergeKind.AUTO))
        val c = insert(device("10.0.0.3").copy(customName = "c"))
        val d = insert(device("10.0.0.4").copy(customName = "d"))
        assertFalse(dao.mergeDevices(c, d, isKnown = null, kind = MergeKind.AUTO))
        val e = insert(device("10.0.0.5").copy(isKnown = true))
        val f = insert(device("10.0.0.6"))
        assertFalse(dao.mergeDevices(e, f, isKnown = null, kind = MergeKind.AUTO))

        assertEquals(0, mergeLogCount(db))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a kind that contradicts isKnown is rejected loudly`(): Unit = runBlocking {
        val (survivor, loser) = pair(0)
        dao.mergeDevices(survivor, loser, isKnown = true, kind = MergeKind.AUTO)
    }

    @Test
    fun `a merge that aborts at the loser delete leaves no log row and no moved identifier`() = runBlocking {
        val survivor = insert(device("10.0.0.1"))
        val loser = insert(device("10.0.0.2"), ident("uuid:l"))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_loser_delete BEFORE DELETE ON known_devices WHEN OLD.id = $loser " +
                "BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )

        val failure = runCatching { dao.mergeDevices(survivor, loser, isKnown = true, kind = MergeKind.USER) }

        assertTrue(failure.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(0, mergeLogCount(db))
        assertEquals(loser, db.deviceIdentifierDao().find("UPNP_UDN", "uuid:l", "")?.deviceId)
    }

    @Test
    fun `the log keeps the newest rows up to the cap`() = runBlocking {
        repeat(DeviceIdentityDao.MERGE_LOG_CAP + 3) {
            val (s, l) = pair(it)
            dao.mergeDevices(s, l, isKnown = false, kind = MergeKind.USER)
        }

        val log = mergeLog(db)
        assertEquals(DeviceIdentityDao.MERGE_LOG_CAP, log.size)
        // The three oldest (lowest loser ids) went first.
        val allLosers = (0 until DeviceIdentityDao.MERGE_LOG_CAP + 3).map { 2L * it + 2 }
        assertEquals(allLosers.drop(3), log.map { it.loserId })
    }

    @Test
    fun `an auto-merge flood evicts AUTO rows before any USER row`() = runBlocking {
        val (s0, l0) = pair(0)
        dao.mergeDevices(s0, l0, isKnown = false, kind = MergeKind.USER)
        repeat(DeviceIdentityDao.MERGE_LOG_CAP + 5) {
            val (s, l) = pair(it + 1)
            dao.mergeDevices(s, l, isKnown = null, kind = MergeKind.AUTO)
        }

        val log = mergeLog(db)
        assertEquals(DeviceIdentityDao.MERGE_LOG_CAP, log.size)
        assertEquals(listOf(l0), log.filter { it.kind == "USER" }.map { it.loserId })
        // The newest AUTO rows are the ones kept.
        assertEquals(l0 + 2L * (DeviceIdentityDao.MERGE_LOG_CAP + 5), log.last().loserId)
    }

    @Test
    fun `with the cap full of USER rows a new AUTO row is the one pruned`() = runBlocking {
        repeat(DeviceIdentityDao.MERGE_LOG_CAP) {
            val (s, l) = pair(it)
            dao.mergeDevices(s, l, isKnown = false, kind = MergeKind.USER)
        }
        val (s, l) = pair(DeviceIdentityDao.MERGE_LOG_CAP)

        assertTrue(dao.mergeDevices(s, l, isKnown = null, kind = MergeKind.AUTO))

        val log = mergeLog(db)
        assertEquals(DeviceIdentityDao.MERGE_LOG_CAP, log.size)
        assertTrue(log.all { it.kind == "USER" })
    }

    @Test
    fun `the prune query's USER literal matches the enum name`() {
        assertEquals("USER", MergeKind.USER.name)
    }

    @Test
    fun `deleting the end of a merge chain removes every ancestor's rows and leaves other chains alone`() = runBlocking {
        val (a, b) = pair(0)
        val c = insert(device("10.0.9.1"))
        dao.mergeDevices(b, a, isKnown = false, kind = MergeKind.USER) // A folded into B
        dao.mergeDevices(c, b, isKnown = false, kind = MergeKind.USER) // B folded into C
        val (x, y) = pair(1)
        dao.mergeDevices(x, y, isKnown = false, kind = MergeKind.USER) // unrelated sibling chain

        db.knownDeviceDao().delete(c)

        assertEquals(listOf(x to y), mergeLog(db).map { it.survivorId to it.loserId })
    }

    @Test
    fun `clearing the inventory empties both known_devices and merge_log`() = runBlocking {
        val (s, l) = pair(0)
        dao.mergeDevices(s, l, isKnown = false, kind = MergeKind.USER)

        db.knownDeviceDao().deleteAll()

        assertEquals(0, mergeLogCount(db))
        assertEquals(null, db.knownDeviceDao().getById(s))
    }

    @Test
    fun `deleting a device removes the log rows it survived and keeps the rest`() = runBlocking {
        val (s1, l1) = pair(0)
        val (s2, l2) = pair(1)
        dao.mergeDevices(s1, l1, isKnown = false, kind = MergeKind.USER)
        dao.mergeDevices(s2, l2, isKnown = false, kind = MergeKind.USER)

        db.knownDeviceDao().delete(s1)

        assertEquals(listOf(s2), mergeLog(db).map { it.survivorId })
        assertEquals(null, db.knownDeviceDao().getById(s1))
    }
}

internal fun mergeLogCount(db: NetLensDatabase): Int = mergeLog(db).size

/** Raw read: production has no merge_log SELECT (the table is write-only until an undo UI exists). */
internal fun mergeLog(db: NetLensDatabase): List<MergeLogEntity> =
    db.openHelper.readableDatabase.query("SELECT * FROM merge_log ORDER BY id").use { c ->
        fun str(name: String) = c.getString(c.getColumnIndexOrThrow(name))
        fun long(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
        fun intOrNull(name: String) = c.getColumnIndexOrThrow(name).let { if (c.isNull(it)) null else c.getInt(it) }
        buildList {
            while (c.moveToNext()) {
                add(
                    MergeLogEntity(
                        id = long("id"), mergedAt = long("mergedAt"), survivorId = long("survivorId"),
                        loserId = long("loserId"), kind = str("kind"),
                        survivorIsKnownBefore = long("survivorIsKnownBefore") != 0L,
                        decisionScore = intOrNull("decisionScore"),
                        decisionReasons = c.getColumnIndexOrThrow("decisionReasons").let { if (c.isNull(it)) null else c.getString(it) },
                        snapshotVersion = long("snapshotVersion").toInt(),
                        loserRowJson = str("loserRowJson"), loserIdentifiersJson = str("loserIdentifiersJson"),
                        loserSightingsJson = str("loserSightingsJson"),
                        survivorUserFieldsBeforeJson = str("survivorUserFieldsBeforeJson"),
                    ),
                )
            }
        }
    }
