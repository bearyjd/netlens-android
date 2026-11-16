package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.dao.DeviceIdentityDao
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.MergeKind
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Mirrors the transactional cases of core:data's `DeviceIdentityDaoTest` against the fake. */
class FakeDeviceIdentityDaoTest {

    private val devices = FakeKnownDeviceDao()
    private val identifiers = FakeDeviceIdentifierDao()
    private val observations = FakeDeviceObservationDao()
    private val sightings = FakeDeviceSightingDao()
    private val decisions = FakeDeviceMergeDecisionDao()
    private val dao = FakeDeviceIdentityDao(devices, identifiers, observations, sightings, decisions)

    private fun device(mac: String? = null, ip: String = "10.0.0.1", firstSeen: Long = 1) =
        KnownDeviceEntity(macAddress = mac, hostname = null, ip = ip, vendor = null, firstSeen = firstSeen, lastSeen = 1)

    private fun ident(value: String = "uuid:a") = DeviceIdentifierEntity(
        deviceId = 0, kind = "UPNP_UDN", value = value, scopeKey = "", source = "SSDP", firstSeen = 1, lastSeen = 1,
    )

    private fun obs(value: String) = DeviceObservationEntity(
        deviceId = 0, source = "SSDP", field = "MODEL", value = value, weight = 10, detail = null, lastSeen = 1,
    )

    @Test
    fun `insertDeviceWithIdentifiers stores the row, its identifiers and observations`() = runTest {
        val id = dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:01"), listOf(ident()), listOf(obs("x")), true)

        assertTrue(id > 0)
        assertEquals(id, identifiers.find("UPNP_UDN", "uuid:a", "")?.deviceId)
        assertEquals(1, observations.getForDevice(id).size)
    }

    @Test
    fun `insertDeviceWithIdentifiers returns -1 and inserts nothing when an identifier is already owned`() = runTest {
        val first = dao.insertDeviceWithIdentifiers(device(), listOf(ident()), emptyList(), true)

        val second = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.2"), listOf(ident("uuid:b"), ident()), emptyList(), true)

        assertEquals(-1L, second)
        assertEquals(first, identifiers.find("UPNP_UDN", "uuid:a", "")?.deviceId)
        assertNull(identifiers.find("UPNP_UDN", "uuid:b", ""))
        assertNull(devices.byId(first + 1))
    }

    @Test
    fun `insertDeviceWithIdentifiers dedupes on macAddress only when asked`() = runTest {
        dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:01"), emptyList(), emptyList(), true)

        assertEquals(-1L, dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:01", "10.0.0.2"), emptyList(), emptyList(), true))
        assertTrue(dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:01", "10.0.0.3"), emptyList(), emptyList(), false) > 0)
    }

    @Test
    fun `merge moves children before deleting the loser`() = runTest {
        val survivor = dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:01"), listOf(ident("uuid:s")), listOf(obs("s")), true)
        val loser = dao.insertDeviceWithIdentifiers(device("AA:BB:CC:00:00:02", "10.0.0.2", 5), listOf(ident("uuid:l")), listOf(obs("l")), true)
        sightings.upsert(DeviceSightingEntity(loser, "netA", null, "10.0.0.2", 5, 5))
        sightings.upsert(DeviceSightingEntity(survivor, "netB", null, "10.0.0.1", 1, 1))

        assertTrue(dao.mergeDevices(survivor, loser, isKnown = null, kind = MergeKind.AUTO))

        assertNull(devices.byId(loser))
        assertEquals(survivor, identifiers.find("UPNP_UDN", "uuid:l", "")?.deviceId)
        assertEquals(setOf("s", "l"), observations.getForDevice(survivor).map { it.value }.toSet())
        assertEquals(setOf("netA", "netB"), sightings.getForDevice(survivor).map { it.networkKey }.toSet())
    }

    @Test
    fun `a confirmed merge fills only empty user fields`() = runTest {
        val survivor = dao.insertDeviceWithIdentifiers(device().copy(isKnown = true, customName = "Mine", notes = "  "), emptyList(), emptyList(), true)
        val loser = dao.insertDeviceWithIdentifiers(
            device(ip = "10.0.0.2").copy(customName = "Theirs", notes = "n", osOverride = "TrueNAS"),
            emptyList(), emptyList(), true,
        )

        dao.mergeDevices(survivor, loser, isKnown = true, kind = MergeKind.USER)

        val merged = devices.byId(survivor)!!
        assertEquals("Mine", merged.customName)
        assertEquals("n", merged.notes)
        assertEquals("TrueNAS", merged.osOverride)
        assertTrue(merged.isKnown)
    }

    @Test
    fun `merge with a missing row is a no-op returning false`() = runTest {
        val only = dao.insertDeviceWithIdentifiers(device(), listOf(ident()), emptyList(), true)

        assertFalse(dao.mergeDevices(only, only + 99, null, MergeKind.AUTO))
        assertEquals(1, identifiers.getForDevice(only).size)
    }

    @Test
    fun `getSeenSince returns only recent rows newest first`() = runTest {
        devices.seed(
            KnownDeviceEntity(id = 1, macAddress = null, hostname = null, ip = "10.0.0.1", vendor = null, lastSeen = 10),
            KnownDeviceEntity(id = 2, macAddress = null, hostname = null, ip = "10.0.0.2", vendor = null, lastSeen = 300),
            KnownDeviceEntity(id = 3, macAddress = null, hostname = null, ip = "10.0.0.3", vendor = null, lastSeen = 200),
        )

        assertEquals(listOf("10.0.0.2", "10.0.0.3"), devices.getSeenSince(100).map { it.ip })
    }

    @Test
    fun `an automatic merge is refused when both rows carry user details, trust differs or the loser touches DISTINCT`() = runTest {
        val a = dao.insertDeviceWithIdentifiers(device().copy(customName = "x"), emptyList(), emptyList(), true)
        val b = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.2").copy(notes = "y"), emptyList(), emptyList(), true)
        assertFalse(dao.mergeDevices(a, b, isKnown = null, kind = MergeKind.AUTO))

        val c = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.3").copy(isKnown = true), emptyList(), emptyList(), true)
        val d = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.4"), emptyList(), emptyList(), true)
        assertFalse(dao.mergeDevices(c, d, isKnown = null, kind = MergeKind.AUTO))

        val e = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.5"), emptyList(), emptyList(), true)
        decisions.setDistinct(d, e, 1)
        val f = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.6"), emptyList(), emptyList(), true)
        assertFalse(dao.mergeDevices(f, d, isKnown = null, kind = MergeKind.AUTO))
        assertTrue(dao.mergeDevices(d, f, isKnown = null, kind = MergeKind.AUTO))
        assertEquals("DISTINCT", decisions.get(d, e)?.decision)
    }

    @Test
    fun `touchesDistinct and insertBelowCap behave like the real DAOs`() = runTest {
        decisions.setDistinct(2, 3, 1)
        assertTrue(decisions.touchesDistinct(listOf(3)))
        assertFalse(decisions.touchesDistinct(listOf(1)))

        val i = FakeDeviceIdentifierDao()
        assertTrue(i.insertBelowCap(ident("a").copy(deviceId = 1), cap = 1) > 0)
        assertEquals(-1L, i.insertBelowCap(ident("b").copy(deviceId = 1), cap = 1))
    }

    // merge_log: the fake runs the production mergeDevices, so these prove its building blocks.

    private suspend fun pair(n: Int): Pair<Long, Long> =
        dao.insertDeviceWithIdentifiers(device(ip = "10.1.$n.1"), emptyList(), emptyList(), false) to
            dao.insertDeviceWithIdentifiers(device(ip = "10.1.$n.2"), emptyList(), emptyList(), false)

    @Test
    fun `a merge logs one row with its kind, decision and loser snapshots`() = runTest {
        val survivor = dao.insertDeviceWithIdentifiers(device().copy(customName = "Mine"), emptyList(), emptyList(), true)
        val loser = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.2").copy(hostname = "a\nb\u202E"), listOf(ident("uuid:l")), emptyList(), true)
        sightings.upsert(DeviceSightingEntity(loser, "netA", "Home", "10.0.0.2", 5, 5))
        decisions.suggest(DeviceMergeDecisionEntity(survivor, loser, "SUGGESTED", 70, "MAC", 1))

        assertTrue(dao.mergeDevices(survivor, loser, isKnown = true, kind = MergeKind.USER))

        val log = devices.mergeLog.single()
        assertEquals(listOf(survivor, loser, 70L), listOf(log.survivorId, log.loserId, log.decisionScore?.toLong()))
        assertEquals("USER", log.kind)
        assertEquals("MAC", log.decisionReasons)
        assertFalse(log.survivorIsKnownBefore)
        assertEquals("a\nb\u202E", JSONObject(log.loserRowJson).getString("hostname"))
        assertEquals("uuid:l", JSONArray(log.loserIdentifiersJson).getJSONObject(0).getString("value"))
        assertEquals("Home", JSONArray(log.loserSightingsJson).getJSONObject(0).getString("networkLabel"))
        assertEquals("Mine", JSONObject(log.survivorUserFieldsBeforeJson).getString("customName"))
    }

    @Test
    fun `a refused merge logs nothing`() = runTest {
        val a = dao.insertDeviceWithIdentifiers(device().copy(isKnown = true), emptyList(), emptyList(), true)
        val b = dao.insertDeviceWithIdentifiers(device(ip = "10.0.0.2"), emptyList(), emptyList(), true)

        assertFalse(dao.mergeDevices(a, b, isKnown = null, kind = MergeKind.AUTO))
        assertFalse(dao.mergeDevices(a, a + 99, isKnown = true, kind = MergeKind.USER))

        assertTrue(devices.mergeLog.isEmpty())
    }

    @Test
    fun `the log is capped and an auto flood evicts AUTO rows before USER ones`() = runTest {
        val (s0, l0) = pair(0)
        dao.mergeDevices(s0, l0, isKnown = false, kind = MergeKind.USER)
        repeat(DeviceIdentityDao.MERGE_LOG_CAP + 5) {
            val (s, l) = pair(it + 1)
            dao.mergeDevices(s, l, isKnown = null, kind = MergeKind.AUTO)
        }

        assertEquals(DeviceIdentityDao.MERGE_LOG_CAP, devices.mergeLog.size)
        assertEquals(listOf(l0), devices.mergeLog.filter { it.kind == "USER" }.map { it.loserId })
        assertEquals(l0 + 2L * (DeviceIdentityDao.MERGE_LOG_CAP + 5), devices.mergeLog.maxOf { it.loserId })
    }

    @Test
    fun `deleting a device drops its whole merge chain's rows, clearing the inventory drops them all`() = runTest {
        val (s1, l1) = pair(0)
        val (s2, l2) = pair(1)
        dao.mergeDevices(s1, l1, isKnown = false, kind = MergeKind.USER)
        dao.mergeDevices(s2, l2, isKnown = false, kind = MergeKind.USER)
        // Merging a former survivor away writes a row but deletes none: the merge path is not a user delete.
        val (s3, _) = pair(2)
        dao.mergeDevices(s3, s2, isKnown = false, kind = MergeKind.USER)
        assertEquals(listOf(s1, s2, s3), devices.mergeLog.map { it.survivorId })

        // s3's chain is s3 <- s2 <- l2: both of its rows go; s1's unrelated chain stays.
        devices.delete(s3)
        assertEquals(listOf(s1), devices.mergeLog.map { it.survivorId })

        devices.deleteAll()
        assertTrue(devices.mergeLog.isEmpty())
        assertTrue(devices.allDevices.isEmpty())
    }
}
