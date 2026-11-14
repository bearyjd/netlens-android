package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.scan.model.LanDevice
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Step 1 (grouping / Conflict) with the corrected rules: group only when authenticated. */
class ResolverConflictTest {
    private val h = IdentityHarness()

    // Existing device 101: MAC1 + udn X, last seen at .10.
    private suspend fun seedIncumbent() =
        h.seedRow(101, "10.0.0.10", ids = listOf(universalMac(MAC1_ID), udn("uuid:x")), mac = MAC1)

    private val incumbent = lan("10.0.0.10", MAC1, udn("uuid:x"))

    @Test
    fun `an unauthenticated claim of a known identifier from a second address with no MAC is a Conflict`() = runTest {
        seedIncumbent()
        val copycat = lan("10.0.0.20", null, udn("uuid:x"), hostname = "totally-the-nas")

        val result = h.resolve(incumbent, copycat)

        val (first, second) = result.devices
        assertEquals(Resolution.Existing(101), first.resolution)
        val conflict = second.resolution as Resolution.Conflict
        assertTrue(conflict.inserted)
        assertEquals(101L, conflict.claimedDeviceId)
        assertTrue(first.sharesIdentifier && second.sharesIdentifier)
        assertEquals(listOf(conflict.deviceId), result.newConflictRows.map { it.id })
        assertEquals("10.0.0.20", result.newConflictRows.single().ip)
        assertEquals("totally-the-nas", result.newConflictRows.single().hostname)
    }

    @Test
    fun `a Conflict never takes unique-identifier ownership and keeps the claim as untrusted evidence`() = runTest {
        seedIncumbent()

        val result = h.resolve(incumbent, lan("10.0.0.20", null, udn("uuid:x")))
        val conflict = result.devices[1].resolution as Resolution.Conflict

        assertEquals(101L, h.identifiers.find("UPNP_UDN", "uuid:x", "")?.deviceId)
        assertTrue(h.identifiers.getForDevice(conflict.deviceId).isEmpty())
        val claim = h.observations.getForDevice(conflict.deviceId).single()
        assertEquals(0, claim.weight)
        assertTrue(claim.value.contains("uuid:x"))
        assertEquals(setOf("uuid:x"), result.devices[1].untrustedClaims.map { it.value }.toSet())
        assertFalse(result.devices[1].ownedIdentifiers.any { it.value == "uuid:x" })
    }

    @Test
    fun `a second scan of the same pair finds the Conflict row instead of inserting another`() = runTest {
        seedIncumbent()
        val copycat = lan("10.0.0.20", null, udn("uuid:x"))
        val first = h.resolve(incumbent, copycat)
        val conflictId = (first.devices[1].resolution as Resolution.Conflict).deviceId

        val second = h.resolve(incumbent, copycat)

        assertEquals(Resolution.Existing(conflictId), second.devices[1].resolution)
        assertEquals(Resolution.Existing(101), second.devices[0].resolution)
        assertTrue(second.newConflictRows.isEmpty())
        assertEquals(2, h.known.allDevices.size)
        assertTrue(second.devices[1].sharesIdentifier)
    }

    @Test
    fun `a Conflict is not merged into the victim even when a later scan shows both with different data`() = runTest {
        seedIncumbent()
        h.resolve(incumbent, lan("10.0.0.20", null, udn("uuid:x")))

        h.resolve(incumbent, lan("10.0.0.20", null, udn("uuid:x"), hostname = "other"))

        assertEquals(setOf(101L, 102L), h.known.allDevices.map { it.id }.toSet())
    }

    @Test
    fun `a claimant with a different universal MAC resolves on its own as New without the claimed identifier`() = runTest {
        seedIncumbent()

        val result = h.resolve(incumbent, lan("10.0.0.20", MAC2, udn("uuid:x")))

        val second = result.devices[1]
        assertEquals(Resolution.New, second.resolution)
        assertTrue(second.sharesIdentifier && result.devices[0].sharesIdentifier)
        assertEquals(setOf(MAC2_ID), second.ownedIdentifiers.map { it.value }.toSet())
        assertEquals(setOf("uuid:x"), second.untrustedClaims.map { it.value }.toSet())
        assertTrue(result.newConflictRows.isEmpty())
    }

    @Test
    fun `sightings sharing the incumbent's MAC are one device and not a conflict`() = runTest {
        seedIncumbent()

        val result = h.resolve(incumbent, lan("10.0.0.20", MAC1, udn("uuid:x")))

        assertEquals(listOf(Resolution.Existing(101), Resolution.Existing(101)), result.devices.map { it.resolution })
    }

    @Test
    fun `sightings that both prove the same identifier are grouped as one multi-homed device`() = runTest {
        seedIncumbent()

        val result = h.resolve(
            LanDevice(ip = "10.0.0.10", macAddress = MAC1, identifiers = setOf(udn("uuid:x", proven = true))),
            LanDevice(ip = "10.0.0.20", identifiers = setOf(udn("uuid:x", proven = true))),
        )

        val device = result.devices.single()
        assertEquals(Resolution.Existing(101), device.resolution)
        assertEquals("10.0.0.10", device.device.ip) // most identifiers wins the primary address
        assertEquals(setOf("10.0.0.20"), device.device.additionalIps)
    }

    @Test
    fun `one proven and one unproven sighting is not authenticated`() = runTest {
        seedIncumbent()

        val result = h.resolve(
            LanDevice(ip = "10.0.0.10", macAddress = MAC1, identifiers = setOf(udn("uuid:x", proven = true))),
            LanDevice(ip = "10.0.0.20", identifiers = setOf(udn("uuid:x"))),
        )

        assertEquals(2, result.devices.size)
        assertTrue(result.devices[1].resolution is Resolution.Conflict)
    }

    @Test
    fun `a lone sighting claiming a known identifier is accepted as that device`() = runTest {
        seedIncumbent()

        val result = h.resolve(lan("10.0.0.77", null, udn("uuid:x")))

        assertEquals(Resolution.Existing(101), result.devices.single().resolution)
        assertFalse(result.devices.single().sharesIdentifier)
    }

    @Test
    fun `two new sightings sharing an unproven identifier are separate and only one owns it`() = runTest {
        val result = h.resolve(
            lan("10.0.0.30", MAC2, udn("uuid:y")),
            lan("10.0.0.40", MAC3, udn("uuid:y")),
        )

        assertEquals(listOf(Resolution.New, Resolution.New), result.devices.map { it.resolution })
        val owners = result.devices.filter { d -> d.ownedIdentifiers.any { it.value == "uuid:y" } }
        assertEquals(1, owners.size)
        assertEquals("10.0.0.30", owners.single().device.ip) // tie on identifier count: lowest IP
        assertTrue(result.devices.all { it.sharesIdentifier })
        assertNull(result.newConflictRows.firstOrNull())
    }

    @Test
    fun `the incumbent is the sighting tied to the row, not the lowest address`() = runTest {
        seedIncumbent()

        // The copycat sorts first by IP; the incumbent still wins because its MAC belongs to the row.
        val result = h.resolve(lan("10.0.0.2", null, udn("uuid:x")), lan("10.0.0.10", MAC1, udn("uuid:x")))

        assertTrue(result.devices[0].resolution is Resolution.Conflict)
        assertEquals(Resolution.Existing(101), result.devices[1].resolution)
    }
}
