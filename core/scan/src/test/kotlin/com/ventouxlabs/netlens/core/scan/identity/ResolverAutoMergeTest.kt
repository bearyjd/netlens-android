package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Step 4. Each blocker test isolates ONE rule: every other rule passes (rows reached through strong
 * identifiers, equal isKnown, no DISTINCT, no contradictory identifiers), so reverting that rule's
 * guard is the only thing that can turn the test red.
 */
class ResolverAutoMergeTest {
    private val h = IdentityHarness()

    // Row 101 owns udn A, row 102 owns MAC1: a device carrying both points at two rows.
    private suspend fun seedPair(
        a: suspend () -> KnownDeviceEntity = { h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 10, lastSeen = 50) },
        b: suspend () -> KnownDeviceEntity = { h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, lastSeen = 40) },
    ) {
        a()
        b()
    }

    private suspend fun scan() = h.resolve(lan("10.0.0.9", MAC1, udn("uuid:a", proven = true)))

    @Test
    fun `rows reached through strong identifiers with no user data merge into the earliest`() = runTest {
        seedPair()

        val r = scan().devices.single().resolution

        assertEquals(Resolution.Existing(deviceId = 102, mergedFrom = listOf(101)), r)
        assertNull(h.known.byId(101))
        assertEquals(setOf("uuid:a", MAC1_ID), h.identifiers.getForDevice(102).map { it.value }.toSet())
    }

    @Test
    fun `a merge keeps the survivor's isKnown when both rows are known`() = runTest {
        seedPair(
            a = { h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 10, isKnown = true) },
            b = { h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, isKnown = true) },
        )

        scan()

        assertTrue(h.known.byId(102)!!.isKnown)
    }

    @Test
    fun `a merge fills the survivor's empty user fields from the loser`() = runTest {
        seedPair(
            a = { h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 10, customName = "Printer", tags = "office", notes = "n", location = "Hall") },
            b = { h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5) },
        )

        scan()

        val merged = h.known.byId(102)!!
        assertEquals("Printer", merged.customName)
        assertEquals("office", merged.tags)
        assertEquals("n", merged.notes)
        assertEquals("Hall", merged.location)
    }

    @Test
    fun `a merge never overwrites the survivor's non-empty user fields`() = runTest {
        seedPair(
            a = { h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 10) },
            b = { h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, customName = "Mine", tags = "t", notes = "n", location = "L", osOverride = "O") },
        )

        scan()

        val merged = h.known.byId(102)!!
        assertEquals(listOf("Mine", "t", "n", "L", "O"), listOf(merged.customName, merged.tags, merged.notes, merged.location, merged.osOverride))
    }

    @Test
    fun `when both rows have user fields nothing merges and a suggestion is persisted`() = runTest {
        seedPair(
            a = { h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 10, customName = "A", lastSeen = 50) },
            b = { h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, notes = "B", lastSeen = 40) },
        )

        val result = scan()

        val r = result.devices.single().resolution as Resolution.Ambiguous
        assertEquals(MergeBlock.USER_DETAILS, r.blockedBy)
        assertEquals(101L, r.attachedTo) // most recently seen candidate
        assertEquals(setOf(101L, 102L), r.deviceIds.toSet())
        assertNotNull(h.known.byId(101))
        assertNotNull(h.known.byId(102))
        assertEquals("A", h.known.byId(101)!!.customName)
        assertEquals("SUGGESTED", h.decisions.get(101, 102)?.decision)
        assertEquals(1, h.decisions.observeSuggestions().first().size)
        assertEquals(listOf(MergeSuggestion.BetweenRows(101, 102, 100, listOf("shared_identifier", "user_details"))), result.suggestions)
    }

    @Test
    fun `two rows that each carry only an override never auto-merge`() = runTest {
        seedPair(
            a = { h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 10, osOverride = "Linux") },
            b = { h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, modelOverride = "X1") },
        )

        val r = scan().devices.single().resolution

        assertTrue(r is Resolution.Ambiguous)
        assertNotNull(h.known.byId(101))
    }

    @Test
    fun `a device-type override alone also blocks the merge`() = runTest {
        seedPair(
            a = { h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 10, deviceTypeOverride = "NAS") },
            b = { h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, customName = "B") },
        )

        assertEquals(MergeBlock.USER_DETAILS, (scan().devices.single().resolution as Resolution.Ambiguous).blockedBy)
    }

    @Test
    fun `rows with differing isKnown are never ORed into a merge`() = runTest {
        seedPair(
            a = { h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 10, isKnown = true) },
            b = { h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, isKnown = false) },
        )

        val result = scan()

        assertEquals(MergeBlock.TRUST_DIFFERS, (result.devices.single().resolution as Resolution.Ambiguous).blockedBy)
        assertEquals(true, h.known.byId(101)!!.isKnown)
        assertEquals(false, h.known.byId(102)!!.isKnown)
        assertEquals(1, result.suggestions.size)
    }

    @Test
    fun `a DISTINCT decision suppresses both the merge and the suggestion`() = runTest {
        seedPair()
        h.decisions.setDistinct(101, 102, 7)

        val result = scan()

        assertEquals(MergeBlock.DISTINCT_DECISION, (result.devices.single().resolution as Resolution.Ambiguous).blockedBy)
        assertTrue(result.suggestions.isEmpty())
        assertEquals("DISTINCT", h.decisions.get(101, 102)?.decision)
        assertNotNull(h.known.byId(101))
    }

    @Test
    fun `a candidate reached only through a MEDIUM identifier blocks the merge`() = runTest {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 10)
        h.seedRow(102, "10.0.0.2", ids = listOf(localMac(MAC_LOCAL_ID)), mac = MAC_LOCAL_RAW, firstSeen = 5, scopeKey = NET)

        val r = h.resolve(lan("10.0.0.9", MAC_LOCAL_RAW, udn("uuid:a", proven = true))).devices.single().resolution

        assertEquals(MergeBlock.WEAK_LINK, (r as Resolution.Ambiguous).blockedBy)
    }

    @Test
    fun `rows owning different values of a one-per-machine kind do not merge`() = runTest {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a"), smb("guid-1")), firstSeen = 10)
        h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID), smb("guid-2")), mac = MAC1, firstSeen = 5)

        val r = scan().devices.single().resolution

        assertEquals(MergeBlock.CONTRADICTORY_IDENTIFIER, (r as Resolution.Ambiguous).blockedBy)
    }

    @Test
    fun `different MAC_LOCAL values on two rows are normal and do not block`() = runTest {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a"), localMac("02bbcc000001")), firstSeen = 10, scopeKey = NET)
        h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID), localMac("02bbcc000002")), mac = MAC1, firstSeen = 5, scopeKey = NET)

        assertTrue(scan().devices.single().resolution is Resolution.Existing)
    }

    @Test
    fun `three candidates merge into the earliest and a single user-field holder is allowed`() = runTest {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 30)
        h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 20, customName = "Keep")
        h.seedRow(103, "10.0.0.3", ids = listOf(udn("uuid:b")), firstSeen = 10)

        val r = h.resolve(lan("10.0.0.9", MAC1, udn("uuid:a", true), udn("uuid:b", true))).devices.single().resolution

        assertEquals(103L, (r as Resolution.Existing).deviceId)
        assertEquals(setOf(101L, 102L), r.mergedFrom.toSet())
        assertEquals("Keep", h.known.byId(103)!!.customName)
        assertEquals(1, h.known.allDevices.size)
    }

    @Test
    fun `one sighting claiming two rows' unproven identifiers never merges them and still alerts`() = runTest {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 10)
        h.seedRow(102, "10.0.0.2", ids = listOf(udn("uuid:b")), firstSeen = 5)

        val result = h.resolve(lan("10.0.0.9", null, udn("uuid:a"), udn("uuid:b")))

        assertTrue(result.devices.single().resolution is Resolution.Conflict)
        assertEquals(1, result.newConflictRows.size)
        assertNotNull(h.known.byId(101))
        assertNotNull(h.known.byId(102))
        assertEquals(101L, h.identifiers.find("UPNP_UDN", "uuid:a", "")?.deviceId)
    }

    @Test
    fun `a mix of proof and an unproven claim is blocked, not merged`() = runTest {
        seedPair()

        val r = h.resolve(lan("10.0.0.9", MAC1, udn("uuid:a"))).devices.single().resolution // MAC proves 102 only

        assertEquals(MergeBlock.WEAK_LINK, (r as Resolution.Ambiguous).blockedBy)
        assertNotNull(h.known.byId(101))
    }

    @Test
    fun `a merge whose loser has a DISTINCT decision with a third device is blocked`() = runTest {
        // A=101 is DISTINCT from C=103. A later than B, so A would be the loser.
        seedPair(
            a = { h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 20) },
            b = { h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5) },
        )
        h.seedRow(103, "10.0.0.3", firstSeen = 1)
        h.decisions.setDistinct(101, 103, 7)

        val r = scan().devices.single().resolution

        assertEquals(MergeBlock.DISTINCT_DECISION, (r as Resolution.Ambiguous).blockedBy)
        assertNotNull(h.known.byId(101))
        assertEquals("DISTINCT", h.decisions.get(101, 103)?.decision)
    }

    @Test
    fun `when the DISTINCT row is the survivor the merge proceeds and the decision is retained`() = runTest {
        seedPair(
            a = { h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a")), firstSeen = 1) },
            b = { h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5) },
        )
        h.seedRow(103, "10.0.0.3", firstSeen = 1)
        h.decisions.setDistinct(101, 103, 7)

        assertEquals(Resolution.Existing(101, listOf(102)), scan().devices.single().resolution)
        assertEquals("DISTINCT", h.decisions.get(101, 103)?.decision)
    }
}
