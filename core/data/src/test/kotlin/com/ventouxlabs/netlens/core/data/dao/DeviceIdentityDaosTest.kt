package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ventouxlabs.netlens.core.data.NetLensDatabase
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Real-SQL tests for the v19 identity DAOs (Robolectric, in-memory Room, foreign keys enforced). */
@RunWith(RobolectricTestRunner::class)
class DeviceIdentityDaosTest {

    private lateinit var db: NetLensDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NetLensDatabase::class.java,
        ).build()
        runBlocking {
            repeat(3) { i ->
                db.knownDeviceDao().insertIfNew(
                    KnownDeviceEntity(macAddress = "00:00:00:00:00:0$i", hostname = null, ip = "10.0.0.$i", vendor = null),
                )
            }
        }
    }

    @After
    fun tearDown() = db.close()

    private fun identifier(deviceId: Long, value: String = "v", scopeKey: String = "") = DeviceIdentifierEntity(
        deviceId = deviceId, kind = "MAC_LOCAL", value = value, scopeKey = scopeKey,
        source = "ARP", firstSeen = 1, lastSeen = 1,
    )

    @Test
    fun `identifier insertIfNew returns id then -1 for a duplicate and find returns the owner`() = runBlocking {
        val dao = db.deviceIdentifierDao()

        assertTrue(dao.insertIfNew(identifier(1)) > 0)
        assertEquals(-1L, dao.insertIfNew(identifier(2)))
        assertEquals(1L, dao.find("MAC_LOCAL", "v", "")?.deviceId)
    }

    @Test
    fun `same identifier value in a different scope inserts`() = runBlocking {
        val dao = db.deviceIdentifierDao()
        dao.insertIfNew(identifier(1, scopeKey = "netA"))

        assertTrue(dao.insertIfNew(identifier(2, scopeKey = "netB")) > 0)
    }

    @Test
    fun `rescope onto an occupied scope does not throw and leaves the row unchanged`() = runBlocking {
        val dao = db.deviceIdentifierDao()
        dao.insertIfNew(identifier(1, scopeKey = ""))
        dao.insertIfNew(identifier(2, scopeKey = "netA"))
        val legacy = dao.find("MAC_LOCAL", "v", "")!!

        val changed = dao.rescope(legacy.id, "netA")

        assertEquals(0, changed)
        assertEquals(1L, dao.find("MAC_LOCAL", "v", "")?.deviceId)
    }

    @Test
    fun `rescope to a free scope moves the row`() = runBlocking {
        val dao = db.deviceIdentifierDao()
        dao.insertIfNew(identifier(1))
        val id = dao.find("MAC_LOCAL", "v", "")!!.id

        assertEquals(1, dao.rescope(id, "netA"))
        assertNotNull(dao.find("MAC_LOCAL", "v", "netA"))
    }

    @Test
    fun `scan suggestion never downgrades a user distinct decision`() = runBlocking {
        val dao = db.deviceMergeDecisionDao()
        dao.setDistinct(1, 2, decidedAt = 5)

        val inserted = dao.suggest(DeviceMergeDecisionEntity(1, 2, "SUGGESTED", 80, "name", 9))
        val updated = dao.updateSuggestion(1, 2, 90, "mac")

        assertEquals(-1L, inserted)
        assertEquals(0, updated)
        assertEquals("DISTINCT", dao.get(1, 2)?.decision)
        assertTrue(dao.observeSuggestions().first().isEmpty())
    }

    @Test
    fun `scan suggestion inserts then refreshes score and reasons while still suggested`() = runBlocking {
        val dao = db.deviceMergeDecisionDao()
        dao.suggest(DeviceMergeDecisionEntity(1, 2, "SUGGESTED", 60, "name", 1))

        dao.updateSuggestion(1, 2, 75, "name,oui")

        val row = dao.get(1, 2)!!
        assertEquals(75, row.score)
        assertEquals("name,oui", row.reasons)
        assertEquals(1, dao.observeSuggestions().first().size)
    }

    @Test
    fun `user decision may replace a suggestion`() = runBlocking {
        val dao = db.deviceMergeDecisionDao()
        dao.suggest(DeviceMergeDecisionEntity(1, 2, "SUGGESTED", 60, null, 1))

        dao.setDistinct(1, 2, decidedAt = 9)

        assertEquals("DISTINCT", dao.get(1, 2)?.decision)
    }

    @Test
    fun `sighting upsert keeps the original firstSeen and refreshes ip and lastSeen`() = runBlocking {
        val dao = db.deviceSightingDao()
        dao.upsert(DeviceSightingEntity(1, "net", "Home", "10.0.0.1", firstSeen = 100, lastSeen = 100))

        dao.upsert(DeviceSightingEntity(1, "net", "Home2", "10.0.0.9", firstSeen = 500, lastSeen = 500))

        val row = dao.getForDevice(1).single()
        assertEquals(100L, row.firstSeen)
        assertEquals(500L, row.lastSeen)
        assertEquals("10.0.0.9", row.lastIp)
        assertEquals("Home2", row.networkLabel)
    }
}
