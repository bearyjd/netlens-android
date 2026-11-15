package com.ventouxlabs.netlens.core.scan.inventory

import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository
import com.ventouxlabs.netlens.core.scan.RecordingNewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.identity.IdentityHarness
import com.ventouxlabs.netlens.core.scan.identity.MAC1
import com.ventouxlabs.netlens.core.scan.identity.MAC1_ID
import com.ventouxlabs.netlens.core.scan.identity.MergePreview
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

    /** Accepts with the survivor and hash a UI would have captured from the rows as they are now. */
    private suspend fun accept(
        pair: MergeSuggestion.BetweenRows,
        isKnown: Boolean? = null,
        repository: DeviceInventoryRepository = repo,
        harness: IdentityHarness = h,
        survivorOverride: Long? = null,
        hashDelta: Int = 0,
    ): Boolean {
        val low = harness.known.byId(pair.deviceIdLow)
        val high = harness.known.byId(pair.deviceIdHigh)
        if (low == null || high == null) return repository.acceptSuggestion(pair, isKnown, -1, 0)
        val (survivor, loser) = MergePreview.survivorAndLoser(low, high)
        val hash = MergePreview.hash(survivor, loser, harness.identifiers.getForDevice(loser.id))
        return repository.acceptSuggestion(pair, isKnown, survivorOverride ?: survivor.id, hash + hashDelta)
    }

    @Test
    fun `accepting is refused when the survivor is not the one the user was shown`() = runTest {
        seedPair()
        repo.persistScan(scan, networkId = null)

        assertFalse(accept(pending().single(), survivorOverride = 101))

        assertNotNull(h.known.byId(101))
        assertNotNull(h.known.byId(102))
    }

    @Test
    fun `accepting is refused when the previewed hash no longer matches`() = runTest {
        seedPair()
        repo.persistScan(scan, networkId = null)

        assertFalse(accept(pending().single(), hashDelta = 1))

        assertNotNull(h.known.byId(101))
        assertNotNull(h.known.byId(102))
    }

    @Test
    fun `a field or identifier changed after the preview changes the hash`() = runTest {
        seedPair()
        val low = h.known.byId(101)!!
        val high = h.known.byId(102)!!
        val (survivor, loser) = MergePreview.survivorAndLoser(low, high)
        val base = MergePreview.hash(survivor, loser, h.identifiers.getForDevice(loser.id))

        assertTrue(base != MergePreview.hash(survivor.copy(notes = "changed"), loser, h.identifiers.getForDevice(loser.id)))
        assertTrue(base != MergePreview.hash(survivor, loser.copy(isKnown = !loser.isKnown), h.identifiers.getForDevice(loser.id)))
        assertTrue(base != MergePreview.hash(survivor, loser, emptyList()))
    }

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

        assertTrue(accept(pending().single()))

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
    fun `accepting applies the user's explicit isKnown choice`() = runTest {
        seedPair(aKnown = true, bKnown = false)
        repo.persistScan(scan, networkId = null)

        assertTrue(accept(pending().single(), isKnown = true))

        assertEquals(true, h.known.byId(102)!!.isKnown)
    }

    @Test
    fun `accepting without a trust choice is refused when the rows disagree, in either order`() = runTest {
        for ((aKnown, bKnown) in listOf(true to false, false to true)) {
            val x = IdentityHarness()
            val r = x.fakes.repository(RecordingNewDeviceNotifier(), autoMerge = true)
            x.seedRow(101, "10.0.0.1", ids = listOf(udn("uuid:a", proven = true)), firstSeen = 10, customName = "A", lastSeen = 50, isKnown = aKnown)
            x.seedRow(102, "10.0.0.2", ids = listOf(universalMac(MAC1_ID)), mac = MAC1, firstSeen = 5, notes = "B", lastSeen = 40, isKnown = bKnown)
            r.persistScan(scan, networkId = null)

            assertFalse(accept(r.pendingMergeSuggestions.first().single(), repository = r, harness = x))

            assertNotNull(x.known.byId(101))
            assertNotNull(x.known.byId(102))
        }
    }

    @Test
    fun `accepting without a trust choice is fine when the rows agree`() = runTest {
        seedPair(aKnown = true, bKnown = true)
        repo.persistScan(scan, networkId = null)

        assertTrue(accept(pending().single()))
        assertEquals(true, h.known.byId(102)!!.isKnown)
    }

    @Test
    fun `an accepted merge trims the survivor to the 64 strongest observations`() = runTest {
        seedPair()
        fun obs(device: Long, prefix: String, weight: Int) = (1..64).map {
            com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity(
                deviceId = device, source = "PORTS", field = "PRODUCT", value = "$prefix$it", weight = weight, detail = null, lastSeen = 1,
            )
        }
        obs(102, "old", 10).forEach { h.observations.upsert(it) }
        obs(101, "new", 50).forEach { h.observations.upsert(it) }
        repo.persistScan(scan, networkId = null)

        assertTrue(accept(pending().single()))

        val kept = h.observations.getForDevice(102)
        assertEquals(64, kept.size)
        assertTrue(kept.all { it.weight == 50 })
        assertEquals(0, notifier.notified.size)
    }

    @Test
    fun `accepting is refused when the loser carries a DISTINCT decision, and the decision survives`() = runTest {
        seedPair()
        h.seedRow(103, "10.0.0.3", firstSeen = 20)
        repo.persistScan(scan, networkId = null)
        h.decisions.setDistinct(101, 103, 7)

        assertFalse(accept(pending().single()))

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

        assertFalse(accept(pair))

        assertNotNull(h.known.byId(101))
        assertNotNull(h.known.byId(102))
    }

    @Test
    fun `accepting a pair that is not a pending suggestion does nothing`() = runTest {
        seedPair()

        assertFalse(accept(MergeSuggestion.BetweenRows(101, 102, 100, emptyList())))

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
    fun `rejecting a pair that is not pending, or is given high-before-low, writes nothing`() = runTest {
        seedPair()

        assertFalse(repo.rejectSuggestion(MergeSuggestion.BetweenRows(101, 102, 100, emptyList())))
        repo.persistScan(scan, networkId = null)
        assertFalse(repo.rejectSuggestion(MergeSuggestion.BetweenRows(102, 101, 100, emptyList())))

        assertEquals("SUGGESTED", h.decisions.get(101, 102)?.decision)
        assertNull(h.decisions.get(102, 101))
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
