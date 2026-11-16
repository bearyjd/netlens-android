package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ventouxlabs.netlens.core.data.NetLensDatabase
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.MergeKind
import com.ventouxlabs.netlens.core.data.model.SaveOutcome
import com.ventouxlabs.netlens.core.data.model.UserDetailField
import com.ventouxlabs.netlens.core.data.model.UserDetails
import com.ventouxlabs.netlens.core.data.model.userDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Real-SQL tests for the edit-vs-merge guard: `saveUserDetails`, `mergedInto`, `setKnownIf`. */
@RunWith(RobolectricTestRunner::class)
class KnownDeviceGuardedSaveTest {

    private lateinit var db: NetLensDatabase
    private lateinit var known: KnownDeviceDao
    private lateinit var identity: DeviceIdentityDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NetLensDatabase::class.java).build()
        known = db.knownDeviceDao()
        identity = db.deviceIdentityDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(ip: String, firstSeen: Long = 1, details: UserDetails = UserDetails(), isKnown: Boolean = false): Long =
        identity.insertDeviceWithIdentifiers(
            KnownDeviceEntity(
                macAddress = null, hostname = null, ip = ip, vendor = null, firstSeen = firstSeen, lastSeen = 1,
                isKnown = isKnown, customName = details.customName, tags = details.tags, notes = details.notes,
                location = details.location,
            ),
            emptyList(), emptyList(), dedupeByMac = false,
        )

    private suspend fun details(id: Long) = known.getById(id)?.userDetails()

    @Test
    fun `a save writes only the changed fields`() = runBlocking {
        val id = insert("10.0.0.1", details = UserDetails(customName = "old", notes = "  "))
        val base = details(id)!!

        assertEquals(SaveOutcome.Saved, known.saveUserDetails(id, base, base.copy(customName = "new")))

        assertEquals(UserDetails(customName = "new", notes = "  "), details(id))
    }

    @Test
    fun `a save to a merged loser reports the survivor and writes nothing`() = runBlocking {
        val survivor = insert("10.0.0.1")
        val loser = insert("10.0.0.2", firstSeen = 5)
        val base = details(loser)!!
        identity.mergeDevices(survivor, loser, isKnown = false, kind = MergeKind.USER)
        val before = known.getById(survivor)

        assertEquals(SaveOutcome.Merged(survivor), known.saveUserDetails(loser, base, base.copy(notes = "typed")))

        assertEquals(before, known.getById(survivor))
    }

    @Test
    fun `a stale save after an automatic merge filled the survivor conflicts and the fill survives`() = runBlocking {
        val survivor = insert("10.0.0.1")
        val loser = insert("10.0.0.2", firstSeen = 5, details = UserDetails(notes = "their notes"))
        val base = details(survivor)!!
        identity.mergeDevices(survivor, loser, isKnown = null, kind = MergeKind.AUTO)

        val outcome = known.saveUserDetails(survivor, base, base.copy(notes = "my notes"))

        assertEquals(SaveOutcome.Conflict(setOf(UserDetailField.NOTES)), outcome)
        assertEquals("their notes", details(survivor)?.notes)
    }

    @Test
    fun `a conflict on one of two changed fields writes neither`() = runBlocking {
        val survivor = insert("10.0.0.1")
        val loser = insert("10.0.0.2", firstSeen = 5, details = UserDetails(notes = "their notes"))
        val base = details(survivor)!!
        identity.mergeDevices(survivor, loser, isKnown = null, kind = MergeKind.AUTO)

        val outcome = known.saveUserDetails(survivor, base, base.copy(customName = "Mine", notes = "my notes"))

        assertEquals(SaveOutcome.Conflict(setOf(UserDetailField.NOTES)), outcome)
        assertEquals(UserDetails(notes = "their notes"), details(survivor))
    }

    @Test
    fun `a stale save of an untouched field never overwrites a merge fill`() = runBlocking {
        val survivor = insert("10.0.0.1")
        val loser = insert("10.0.0.2", firstSeen = 5, details = UserDetails(notes = "their notes", location = "Garage"))
        val base = details(survivor)!!
        identity.mergeDevices(survivor, loser, isKnown = null, kind = MergeKind.AUTO)

        assertEquals(SaveOutcome.Saved, known.saveUserDetails(survivor, base, base.copy(customName = "Mine")))

        assertEquals(UserDetails(customName = "Mine", notes = "their notes", location = "Garage"), details(survivor))
    }

    @Test
    fun `a merge chain A to B to C redirects to C`() = runBlocking {
        val c = insert("10.0.0.3")
        val b = insert("10.0.0.2", firstSeen = 5)
        val a = insert("10.0.0.1", firstSeen = 9)
        val base = details(a)!!
        identity.mergeDevices(b, a, isKnown = false, kind = MergeKind.USER)
        identity.mergeDevices(c, b, isKnown = false, kind = MergeKind.USER)

        assertEquals(SaveOutcome.Merged(c), known.saveUserDetails(a, base, base.copy(customName = "x")))
        assertEquals(c, known.mergedInto(a))
    }

    @Test
    fun `a deleted row, or one whose survivor was deleted, is Gone`() = runBlocking {
        val lone = insert("10.0.0.9")
        val base = details(lone)!!
        known.delete(lone)
        assertEquals(SaveOutcome.Gone, known.saveUserDetails(lone, base, base.copy(customName = "x")))

        val survivor = insert("10.0.0.1")
        val loser = insert("10.0.0.2", firstSeen = 5)
        identity.mergeDevices(survivor, loser, isKnown = false, kind = MergeKind.USER)
        known.delete(survivor)
        assertEquals(SaveOutcome.Gone, known.saveUserDetails(loser, base, base.copy(customName = "x")))
        assertNull(known.mergedInto(loser))
    }

    @Test
    fun `setKnownIf writes only while the row still holds the expected value`() = runBlocking {
        val id = insert("10.0.0.1")

        assertEquals(1, known.setKnownIf(id, isKnown = true, expected = false))
        assertEquals(0, known.setKnownIf(id, isKnown = true, expected = false))
        assertEquals(true, known.getById(id)?.isKnown)
        assertEquals(0, known.setKnownIf(id + 99, isKnown = true, expected = false))
    }
}
