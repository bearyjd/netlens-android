package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.MergeKind
import com.ventouxlabs.netlens.core.data.model.SaveOutcome
import com.ventouxlabs.netlens.core.data.model.UserDetailField
import com.ventouxlabs.netlens.core.data.model.UserDetails
import com.ventouxlabs.netlens.core.data.model.userDetails
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Mirrors core:data's `KnownDeviceGuardedSaveTest` and the union-merge cases against the fakes. */
class FakeKnownDeviceGuardedSaveTest {

    private val known = FakeKnownDeviceDao()
    private val identity = FakeDeviceIdentityDao(
        known, FakeDeviceIdentifierDao(), FakeDeviceObservationDao(), FakeDeviceSightingDao(), FakeDeviceMergeDecisionDao(),
    )

    private suspend fun insert(ip: String, details: UserDetails = UserDetails()): Long =
        identity.insertDeviceWithIdentifiers(
            KnownDeviceEntity(
                macAddress = null, hostname = null, ip = ip, vendor = null, firstSeen = 1, lastSeen = 1,
                customName = details.customName, tags = details.tags, notes = details.notes, location = details.location,
            ),
            emptyList(), emptyList(), dedupeByMac = false,
        )

    private fun details(id: Long) = known.byId(id)?.userDetails()

    @Test
    fun `a save writes only the changed fields`() = runTest {
        val id = insert("10.0.0.1", UserDetails(customName = "old", notes = "  "))
        val base = details(id)!!

        assertEquals(SaveOutcome.Saved, known.saveUserDetails(id, base, base.copy(customName = "new")))
        assertEquals(UserDetails(customName = "new", notes = "  "), details(id))
    }

    @Test
    fun `a save to a merged loser reports the survivor and writes nothing`() = runTest {
        val survivor = insert("10.0.0.1")
        val loser = insert("10.0.0.2")
        val base = details(loser)!!
        identity.mergeDevices(survivor, loser, isKnown = false, kind = MergeKind.USER)
        val before = known.byId(survivor)

        assertEquals(SaveOutcome.Merged(survivor), known.saveUserDetails(loser, base, base.copy(notes = "typed")))
        assertEquals(before, known.byId(survivor))
    }

    @Test
    fun `a stale save after an automatic fill conflicts and the fill survives`() = runTest {
        val survivor = insert("10.0.0.1")
        val loser = insert("10.0.0.2", UserDetails(notes = "their notes"))
        val base = details(survivor)!!
        identity.mergeDevices(survivor, loser, isKnown = null, kind = MergeKind.AUTO)

        assertEquals(
            SaveOutcome.Conflict(setOf(UserDetailField.NOTES)),
            known.saveUserDetails(survivor, base, base.copy(notes = "my notes")),
        )
        assertEquals("their notes", details(survivor)?.notes)
    }

    @Test
    fun `a conflict on one of two changed fields writes neither`() = runTest {
        val survivor = insert("10.0.0.1")
        val loser = insert("10.0.0.2", UserDetails(notes = "their notes"))
        val base = details(survivor)!!
        identity.mergeDevices(survivor, loser, isKnown = null, kind = MergeKind.AUTO)

        assertEquals(
            SaveOutcome.Conflict(setOf(UserDetailField.NOTES)),
            known.saveUserDetails(survivor, base, base.copy(customName = "Mine", notes = "my notes")),
        )
        assertEquals(UserDetails(notes = "their notes"), details(survivor))
    }

    @Test
    fun `a stale save of an untouched field never overwrites a merge fill`() = runTest {
        val survivor = insert("10.0.0.1")
        val loser = insert("10.0.0.2", UserDetails(notes = "their notes", location = "Garage"))
        val base = details(survivor)!!
        identity.mergeDevices(survivor, loser, isKnown = null, kind = MergeKind.AUTO)

        assertEquals(SaveOutcome.Saved, known.saveUserDetails(survivor, base, base.copy(customName = "Mine")))
        assertEquals(UserDetails(customName = "Mine", notes = "their notes", location = "Garage"), details(survivor))
    }

    @Test
    fun `a chain redirects to its live end and a deleted end is Gone`() = runTest {
        val c = insert("10.0.0.3")
        val b = insert("10.0.0.2")
        val a = insert("10.0.0.1")
        val base = details(a)!!
        identity.mergeDevices(b, a, isKnown = false, kind = MergeKind.USER)
        identity.mergeDevices(c, b, isKnown = false, kind = MergeKind.USER)

        assertEquals(SaveOutcome.Merged(c), known.saveUserDetails(a, base, base.copy(customName = "x")))

        known.delete(c)
        assertEquals(SaveOutcome.Gone, known.saveUserDetails(a, base, base.copy(customName = "x")))
        assertNull(known.mergedInto(a))
    }

    @Test
    fun `setKnownIf is a compare-and-set`() = runTest {
        val id = insert("10.0.0.1")

        assertEquals(1, known.setKnownIf(id, isKnown = true, expected = false))
        assertEquals(0, known.setKnownIf(id, isKnown = false, expected = false))
        assertEquals(true, known.byId(id)?.isKnown)
        assertEquals(0, known.setKnownIf(id + 99, isKnown = true, expected = false))
    }

    @Test
    fun `a user merge unions tags and notes and the log keeps the survivor's values from before`() = runTest {
        val survivor = insert("10.0.0.1", UserDetails(customName = "Mine", tags = "a", notes = "mine"))
        val loser = insert("10.0.0.2", UserDetails(customName = "Theirs", tags = "b,A", notes = "theirs"))

        identity.mergeDevices(survivor, loser, isKnown = false, kind = MergeKind.USER)

        assertEquals(UserDetails(customName = "Mine", tags = "a,b", notes = "mine\n\ntheirs"), details(survivor))
        val before = JSONObject(known.mergeLog.single().survivorUserFieldsBeforeJson)
        assertEquals("a", before.getString("tags"))
        assertEquals("mine", before.getString("notes"))
    }

    @Test
    fun `an automatic merge with user details on both sides is refused, nothing combined`() = runTest {
        val survivor = insert("10.0.0.1", UserDetails(tags = "a", notes = "mine"))
        val loser = insert("10.0.0.2", UserDetails(tags = "b", notes = "theirs"))

        assertFalse(identity.mergeDevices(survivor, loser, isKnown = null, kind = MergeKind.AUTO))
        assertEquals(UserDetails(tags = "a", notes = "mine"), details(survivor))
        assertNotNull(known.byId(loser))
    }
}
