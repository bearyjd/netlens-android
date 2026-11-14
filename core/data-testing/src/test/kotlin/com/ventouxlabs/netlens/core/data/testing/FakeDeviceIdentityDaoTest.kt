package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.test.runTest
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

        assertTrue(dao.mergeDevices(survivor, loser, isKnown = null))

        assertNull(devices.byId(loser))
        assertEquals(survivor, identifiers.find("UPNP_UDN", "uuid:l", "")?.deviceId)
        assertEquals(setOf("s", "l"), observations.getForDevice(survivor).map { it.value }.toSet())
        assertEquals(setOf("netA", "netB"), sightings.getForDevice(survivor).map { it.networkKey }.toSet())
    }

    @Test
    fun `merge fills only empty user fields and keeps the survivor's isKnown`() = runTest {
        val survivor = dao.insertDeviceWithIdentifiers(device().copy(isKnown = true, customName = "Mine", notes = "  "), emptyList(), emptyList(), true)
        val loser = dao.insertDeviceWithIdentifiers(
            device(ip = "10.0.0.2").copy(customName = "Theirs", notes = "n", osOverride = "TrueNAS"),
            emptyList(), emptyList(), true,
        )

        dao.mergeDevices(survivor, loser, isKnown = null)

        val merged = devices.byId(survivor)!!
        assertEquals("Mine", merged.customName)
        assertEquals("n", merged.notes)
        assertEquals("TrueNAS", merged.osOverride)
        assertTrue(merged.isKnown)
    }

    @Test
    fun `merge with a missing row is a no-op returning false`() = runTest {
        val only = dao.insertDeviceWithIdentifiers(device(), listOf(ident()), emptyList(), true)

        assertFalse(dao.mergeDevices(only, only + 99, null))
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
}
