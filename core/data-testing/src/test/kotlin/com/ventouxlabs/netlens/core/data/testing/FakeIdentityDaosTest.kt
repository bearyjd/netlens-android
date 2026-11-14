package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity
import com.ventouxlabs.netlens.core.data.model.NetworkKeyAliasEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins each identity fake to the contract the real DAO is tested against in core:data's
 * `DeviceIdentityDaosTest` (same inputs, same asserted values), plus the REPLACE / trim / alias gaps.
 */
class FakeIdentityDaosTest {

    private fun identifier(deviceId: Long, value: String = "v", scopeKey: String = "") = DeviceIdentifierEntity(
        deviceId = deviceId, kind = "MAC_LOCAL", value = value, scopeKey = scopeKey,
        source = "ARP", firstSeen = 1, lastSeen = 1,
    )

    private fun observation(value: String, lastSeen: Long, deviceId: Long = 1, id: Long = 0) = DeviceObservationEntity(
        id = id, deviceId = deviceId, source = "SSDP", field = "MODEL", value = value, weight = 5, detail = null, lastSeen = lastSeen,
    )

    @Test
    fun `identifier insertIfNew returns id then -1 for a duplicate and find returns the owner`() = runTest {
        val dao = FakeDeviceIdentifierDao()

        assertTrue(dao.insertIfNew(identifier(1)) > 0)
        assertEquals(-1L, dao.insertIfNew(identifier(2)))
        assertEquals(1L, dao.find("MAC_LOCAL", "v", "")?.deviceId)
    }

    @Test
    fun `same identifier value in a different scope inserts`() = runTest {
        val dao = FakeDeviceIdentifierDao()
        dao.insertIfNew(identifier(1, scopeKey = "netA"))

        assertTrue(dao.insertIfNew(identifier(2, scopeKey = "netB")) > 0)
    }

    @Test
    fun `rescope onto an occupied scope does not throw and leaves the row unchanged`() = runTest {
        val dao = FakeDeviceIdentifierDao()
        dao.insertIfNew(identifier(1, scopeKey = ""))
        dao.insertIfNew(identifier(2, scopeKey = "netA"))
        val legacy = dao.find("MAC_LOCAL", "v", "")!!

        assertEquals(0, dao.rescope(legacy.id, "netA"))
        assertEquals(1L, dao.find("MAC_LOCAL", "v", "")?.deviceId)
    }

    @Test
    fun `rescope to a free scope moves the row`() = runTest {
        val dao = FakeDeviceIdentifierDao()
        dao.insertIfNew(identifier(1))
        val id = dao.find("MAC_LOCAL", "v", "")!!.id

        assertEquals(1, dao.rescope(id, "netA"))
        assertNotNull(dao.find("MAC_LOCAL", "v", "netA"))
    }

    @Test
    fun `identifier touch updates lastSeen and countByKind counts one device's kind`() = runTest {
        val dao = FakeDeviceIdentifierDao()
        dao.insertIfNew(identifier(1, "a"))
        dao.insertIfNew(identifier(1, "b"))
        dao.insertIfNew(identifier(2, "c"))
        dao.touch(dao.find("MAC_LOCAL", "a", "")!!.id, 99)

        assertEquals(99L, dao.find("MAC_LOCAL", "a", "")?.lastSeen)
        assertEquals(2, dao.countByKind(1, "MAC_LOCAL"))
        assertEquals(0, dao.countByKind(1, "UPNP_UDN"))
    }

    @Test
    fun `scan suggestion never downgrades a user distinct decision`() = runTest {
        val dao = FakeDeviceMergeDecisionDao()
        dao.setDistinct(1, 2, decidedAt = 5)

        val inserted = dao.suggest(DeviceMergeDecisionEntity(1, 2, "SUGGESTED", 80, "name", 9))
        val updated = dao.updateSuggestion(1, 2, 90, "mac")

        assertEquals(-1L, inserted)
        assertEquals(0, updated)
        assertEquals("DISTINCT", dao.get(1, 2)?.decision)
        assertTrue(dao.observeSuggestions().first().isEmpty())
    }

    @Test
    fun `scan suggestion inserts then refreshes score and reasons while still suggested`() = runTest {
        val dao = FakeDeviceMergeDecisionDao()
        dao.suggest(DeviceMergeDecisionEntity(1, 2, "SUGGESTED", 60, "name", 1))

        dao.updateSuggestion(1, 2, 75, "name,oui")

        val row = dao.get(1, 2)!!
        assertEquals(75, row.score)
        assertEquals("name,oui", row.reasons)
        assertEquals(1, dao.observeSuggestions().first().size)
    }

    @Test
    fun `user decision may replace a suggestion`() = runTest {
        val dao = FakeDeviceMergeDecisionDao()
        dao.suggest(DeviceMergeDecisionEntity(1, 2, "SUGGESTED", 60, null, 1))

        dao.setDistinct(1, 2, decidedAt = 9)

        assertEquals("DISTINCT", dao.get(1, 2)?.decision)
    }

    @Test
    fun `suggestions are ordered by score descending`() = runTest {
        val dao = FakeDeviceMergeDecisionDao()
        dao.suggest(DeviceMergeDecisionEntity(1, 2, "SUGGESTED", 60, null, 1))
        dao.suggest(DeviceMergeDecisionEntity(1, 3, "SUGGESTED", 90, null, 1))

        assertEquals(listOf(3L, 2L), dao.observeSuggestions().first().map { it.deviceIdHigh })
    }

    @Test
    fun `sighting upsert keeps the original firstSeen and refreshes ip and lastSeen`() = runTest {
        val dao = FakeDeviceSightingDao()
        dao.upsert(DeviceSightingEntity(1, "net", "Home", "10.0.0.1", firstSeen = 100, lastSeen = 100))

        dao.upsert(DeviceSightingEntity(1, "net", "Home2", "10.0.0.9", firstSeen = 500, lastSeen = 500))

        val row = dao.getForDevice(1).single()
        assertEquals(100L, row.firstSeen)
        assertEquals(500L, row.lastSeen)
        assertEquals("10.0.0.9", row.lastIp)
        assertEquals("Home2", row.networkLabel)
    }

    @Test
    fun `sighting insertIfNew returns -1 for the same device and network`() = runTest {
        val dao = FakeDeviceSightingDao()

        assertTrue(dao.insertIfNew(DeviceSightingEntity(1, "net", null, "10.0.0.1", 1, 1)) != -1L)
        assertEquals(-1L, dao.insertIfNew(DeviceSightingEntity(1, "net", null, "10.0.0.2", 2, 2)))
    }

    @Test
    fun `observation upsert replaces the colliding row and assigns a new id`() = runTest {
        val dao = FakeDeviceObservationDao()
        val first = dao.upsert(observation("x", lastSeen = 1))

        val second = dao.upsert(observation("x", lastSeen = 2))

        assertNotEquals(first, second)
        val rows = dao.getForDevice(1)
        assertEquals(1, rows.size)
        assertEquals(2L, rows.single().lastSeen)
    }

    @Test
    fun `trimToNewest keeps the newest rows with id as the tie-break`() = runTest {
        val dao = FakeDeviceObservationDao()
        dao.upsert(observation("old", lastSeen = 1))
        dao.upsert(observation("tieA", lastSeen = 5))
        dao.upsert(observation("tieB", lastSeen = 5))
        dao.upsert(observation("other", lastSeen = 1, deviceId = 2))

        dao.trimToNewest(deviceId = 1, keep = 2)

        assertEquals(setOf("tieA", "tieB"), dao.getForDevice(1).map { it.value }.toSet())
        assertEquals(1, dao.getForDevice(2).size)
    }

    @Test
    fun `alias insertIfNew returns -1 for an existing alias and canonicalFor resolves it`() = runTest {
        val dao = FakeNetworkKeyAliasDao()

        assertTrue(dao.insertIfNew(NetworkKeyAliasEntity("a", "canon", 1)) != -1L)
        assertEquals(-1L, dao.insertIfNew(NetworkKeyAliasEntity("a", "other", 2)))
        assertEquals("canon", dao.canonicalFor("a"))
        assertNull(dao.canonicalFor("missing"))
    }
}
