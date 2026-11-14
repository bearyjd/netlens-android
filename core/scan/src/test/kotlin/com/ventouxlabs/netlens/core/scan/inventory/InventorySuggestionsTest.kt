package com.ventouxlabs.netlens.core.scan.inventory

import com.ventouxlabs.netlens.core.scan.RecordingNewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.identity.IdentityHarness
import com.ventouxlabs.netlens.core.scan.identity.MAC1
import com.ventouxlabs.netlens.core.scan.identity.MAC1_ID
import com.ventouxlabs.netlens.core.scan.identity.MergeSuggestion
import com.ventouxlabs.netlens.core.scan.identity.lan
import com.ventouxlabs.netlens.core.scan.identity.udn
import com.ventouxlabs.netlens.core.scan.identity.universalMac
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** pendingMergeSuggestions / acceptSuggestion / rejectSuggestion over the rows the resolver flagged. */
class InventorySuggestionsTest {
    private val h = IdentityHarness()
    private val notifier = RecordingNewDeviceNotifier()
    private val repo = h.fakes.repository(notifier, autoMerge = true)
    private val scan = listOf(lan("10.0.0.9", MAC1, udn("uuid:a", proven = true)))

    /** Two rows that both carry user data, so the scan flags them instead of merging. */
    private suspend fun seedPair(aKnown: Boolean = false, bKnown: Boolean = false) {
        h.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a", proven = true)), firstSeen = 10, customName = "A", lastSeen = 50, isKnown = aKnown)
        h.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, notes = "B", location = "Den", lastSeen = 40, isKnown = bKnown)
    }

    private suspend fun pending() = repo.pendingMergeSuggestions.first()

    @Test
    fun `a flagged pair shows up as a pending suggestion`() = runTest {
        seedPair()
        repo.persistScan(scan, networkId = null)

        assertEquals(listOf(MergeSuggestion.BetweenRows(101, 102, 100, listOf("shared_identifier", "user_details"))), pending())
    }

    @Test
    fun `accepting merges into the older row, its user fields win and the other fills only empty ones`() = runTest {
        seedPair()
        repo.persistScan(scan, networkId = null)

        assertTrue(repo.acceptSuggestion(pending().single()))

        assertNull(h.known.byId(101))
        val survivor = h.known.byId(102)!!
        assertEquals("A", survivor.customName) // filled from the newer row
        assertEquals("B", survivor.notes) // the older row's own value
        assertEquals("Den", survivor.location)
        assertEquals(setOf("uuid:a", MAC1_ID), h.identifiers.getForDevice(102).map { it.value }.toSet())
        assertTrue(pending().isEmpty())
        assertEquals(0, notifier.notified.size)
    }

    @Test
    fun `accepting applies the user's explicit isKnown choice, defaulting to the survivor's`() = runTest {
        seedPair(aKnown = true, bKnown = false)
        repo.persistScan(scan, networkId = null)
        assertTrue(repo.acceptSuggestion(pending().single(), isKnown = true))
        assertEquals(true, h.known.byId(102)!!.isKnown)

        val other = IdentityHarness()
        val repo2 = other.fakes.repository(RecordingNewDeviceNotifier(), autoMerge = true)
        other.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a", proven = true)), firstSeen = 10, customName = "A", lastSeen = 50, isKnown = true)
        other.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, notes = "B", lastSeen = 40, isKnown = false)
        repo2.persistScan(scan, networkId = null)
        assertTrue(repo2.acceptSuggestion(repo2.pendingMergeSuggestions.first().single()))
        assertEquals(false, other.known.byId(102)!!.isKnown)
    }

    @Test
    fun `accepting is refused when the loser carries a DISTINCT decision, and the decision survives`() = runTest {
        seedPair()
        h.seedRow(103, "10.0.0.3", firstSeen = 20)
        repo.persistScan(scan, networkId = null)
        h.decisions.setDistinct(101, 103, 7)

        assertFalse(repo.acceptSuggestion(pending().single()))

        assertNotNull(h.known.byId(101))
        assertNotNull(h.known.byId(102))
        assertEquals("DISTINCT", h.decisions.get(101, 103)?.decision)
    }

    @Test
    fun `accepting a pair the user already marked DISTINCT does nothing`() = runTest {
        seedPair()
        repo.persistScan(scan, networkId = null)
        val pair = pending().single()
        assertTrue(repo.rejectSuggestion(pair))

        assertFalse(repo.acceptSuggestion(pair))

        assertNotNull(h.known.byId(101))
        assertNotNull(h.known.byId(102))
    }

    @Test
    fun `accepting a pair that is not a pending suggestion does nothing`() = runTest {
        seedPair()

        assertFalse(repo.acceptSuggestion(MergeSuggestion.BetweenRows(101, 102, 100, emptyList())))

        assertNotNull(h.known.byId(101))
        assertNotNull(h.known.byId(102))
    }

    @Test
    fun `rejecting writes DISTINCT and a later scan can never downgrade it back to a suggestion`() = runTest {
        seedPair()
        repo.persistScan(scan, networkId = null)

        assertTrue(repo.rejectSuggestion(pending().single()))
        assertEquals("DISTINCT", h.decisions.get(101, 102)?.decision)
        assertTrue(pending().isEmpty())

        repo.persistScan(scan, networkId = null)

        assertEquals("DISTINCT", h.decisions.get(101, 102)?.decision)
        assertTrue(pending().isEmpty())
        assertEquals(2, h.known.allDevices.size)
    }

    @Test
    fun `rejecting a pair whose row is gone is refused`() = runTest {
        seedPair()
        repo.persistScan(scan, networkId = null)
        val pair = pending().single()
        h.known.delete(101)

        assertFalse(repo.rejectSuggestion(pair))
    }
}
