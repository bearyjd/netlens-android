package com.ventouxlabs.netlens.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ventouxlabs.netlens.core.data.NetLensDatabase
import com.ventouxlabs.netlens.core.data.model.HistoryDetailData
import com.ventouxlabs.netlens.core.data.model.SpeedTestHistoryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Real-Room tests: Clear All and retention must cover speed-test results too. */
@RunWith(RobolectricTestRunner::class)
class HistoryRepositorySpeedTestTest {

    private lateinit var db: NetLensDatabase
    private lateinit var repo: HistoryRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NetLensDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = HistoryRepositoryImpl(
            database = db,
            pingDao = db.pingHistoryDao(),
            lanScanDao = db.lanScanHistoryDao(),
            portScanDao = db.portScanHistoryDao(),
            dnsDao = db.dnsHistoryDao(),
            whoisDao = db.whoisHistoryDao(),
            ipInfoDao = db.ipInfoHistoryDao(),
            tracerouteDao = db.tracerouteHistoryDao(),
            tlsDao = db.tlsHistoryDao(),
            httpTesterDao = db.httpTesterHistoryDao(),
            mdnsDao = db.mdnsHistoryDao(),
            wolHistoryDao = db.wolHistoryDao(),
            speedTestDao = db.speedTestHistoryDao(),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun entry(timestamp: Long) = SpeedTestHistoryEntry(
        timestamp = timestamp, downloadMbps = 100f, uploadMbps = 20f, latencyMs = 10, serverName = "srv",
    )

    private suspend fun count() = db.speedTestHistoryDao().getRecent(100).first().size

    @Test
    fun `clearAll deletes speed-test records`() = runBlocking {
        db.speedTestHistoryDao().insert(entry(System.currentTimeMillis()))
        repo.clearAll()
        assertEquals(0, count())
    }

    @Test
    fun `clearOlderThan deletes only old speed-test records`() = runBlocking {
        val now = System.currentTimeMillis()
        db.speedTestHistoryDao().insert(entry(now - 40L * 24 * 60 * 60 * 1000))
        db.speedTestHistoryDao().insert(entry(now))
        repo.clearOlderThan(30)
        assertEquals(1, count())
    }

    @Test
    fun `allRecent includes speed tests`() = runBlocking {
        db.speedTestHistoryDao().insert(entry(System.currentTimeMillis()))
        assertEquals(1, repo.allRecent(50).first().speedTests.size)
    }

    @Test
    fun `searchAll matches speed tests by server name`() = runBlocking {
        db.speedTestHistoryDao().insert(entry(System.currentTimeMillis()))
        assertEquals(1, repo.searchAll("sr").first().speedTests.size)
        assertTrue(repo.searchAll("nomatch").first().speedTests.isEmpty())
    }

    @Test
    fun `getEntry SpeedTest returns detail or null`() = runBlocking {
        db.speedTestHistoryDao().insert(entry(System.currentTimeMillis()))
        val id = db.speedTestHistoryDao().getRecent(1).first().single().id
        assertTrue(repo.getEntry("SpeedTest", id) is HistoryDetailData.SpeedTest)
        assertNull(repo.getEntry("SpeedTest", id + 1000))
    }
}
