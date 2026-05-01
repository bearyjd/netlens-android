package com.ventoux.netlens.feature.speedtest

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.ventoux.netlens.core.data.dao.SpeedTestHistoryDao
import com.ventoux.netlens.core.data.model.SpeedTestHistoryEntry
import com.ventoux.netlens.feature.speedtest.engine.SpeedTestEngine
import com.ventoux.netlens.feature.speedtest.model.SpeedProgress
import com.ventoux.netlens.feature.speedtest.model.SpeedTestPhase
import com.ventoux.netlens.feature.speedtest.model.SpeedTestUiState

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedTestViewModelTest {

    private lateinit var fakeEngine: FakeSpeedTestEngine
    private lateinit var fakeHistoryDao: FakeSpeedTestHistoryDao
    private lateinit var viewModel: SpeedTestViewModel

    private class FakeSpeedTestEngine : SpeedTestEngine {
        var latencyResult: Long = 25L
        var latencyError: Throwable? = null
        var downloadProgress: List<SpeedProgress> = listOf(
            SpeedProgress(
                bytesTransferred = 25_000_000L,
                elapsedMs = 1000L,
                speedMbps = 200f,
                phase = SpeedTestPhase.DOWNLOAD,
            ),
        )
        var downloadError: Throwable? = null
        var uploadProgress: List<SpeedProgress> = listOf(
            SpeedProgress(
                bytesTransferred = 10_000_000L,
                elapsedMs = 1000L,
                speedMbps = 80f,
                phase = SpeedTestPhase.UPLOAD,
            ),
        )
        var uploadError: Throwable? = null

        override fun measureDownload(): Flow<SpeedProgress> {
            downloadError?.let { return flow { throw it } }
            return flowOf(*downloadProgress.toTypedArray())
        }

        override fun measureUpload(): Flow<SpeedProgress> {
            uploadError?.let { return flow { throw it } }
            return flowOf(*uploadProgress.toTypedArray())
        }

        override suspend fun measureLatency(): Long {
            latencyError?.let { throw it }
            return latencyResult
        }
    }

    private class FakeSpeedTestHistoryDao : SpeedTestHistoryDao {
        val inserted = mutableListOf<SpeedTestHistoryEntry>()

        override fun getRecent(limit: Int): Flow<List<SpeedTestHistoryEntry>> = flowOf(emptyList())
        override fun search(query: String, limit: Int): Flow<List<SpeedTestHistoryEntry>> = flowOf(emptyList())
        override suspend fun getById(id: Long): SpeedTestHistoryEntry? = null
        override suspend fun insert(entry: SpeedTestHistoryEntry) { inserted.add(entry) }
        override suspend fun deleteById(id: Long) {}
        override suspend fun deleteOlderThan(before: Long) {}
        override suspend fun deleteAll() {}
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeEngine = FakeSpeedTestEngine()
        fakeHistoryDao = FakeSpeedTestHistoryDao()
        viewModel = SpeedTestViewModel(fakeEngine, fakeHistoryDao)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is IDLE`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(SpeedTestUiState(), state)
            assertEquals(SpeedTestPhase.IDLE, state.phase)
            assertFalse(state.isRunning)
            assertNull(state.error)
            assertEquals(0f, state.downloadMbps)
            assertEquals(0f, state.uploadMbps)
            assertEquals(0L, state.latencyMs)
        }
    }

    @Test
    fun `startTest transitions through phases to COMPLETE`() = runTest {
        viewModel.state.test {
            awaitItem() // initial IDLE

            viewModel.startTest()

            val finalState = expectMostRecentItem()
            assertEquals(SpeedTestPhase.COMPLETE, finalState.phase)
            assertFalse(finalState.isRunning)
            assertEquals(25L, finalState.latencyMs)
            assertEquals(200f, finalState.downloadMbps)
            assertEquals(80f, finalState.uploadMbps)
            assertNull(finalState.error)
        }
    }

    @Test
    fun `startTest saves to history on completion`() = runTest {
        viewModel.startTest()

        assertEquals(1, fakeHistoryDao.inserted.size)
        val entry = fakeHistoryDao.inserted[0]
        assertEquals(200f, entry.downloadMbps)
        assertEquals(80f, entry.uploadMbps)
        assertEquals(25L, entry.latencyMs)
        assertEquals("Cloudflare", entry.serverName)
    }

    @Test
    fun `latency error shows error and stops`() = runTest {
        fakeEngine.latencyError = RuntimeException("Connection refused")

        viewModel.state.test {
            awaitItem() // initial IDLE

            viewModel.startTest()

            val finalState = expectMostRecentItem()
            assertFalse(finalState.isRunning)
            assertEquals("Connection refused", finalState.error)
        }
    }

    @Test
    fun `download error shows error and stops`() = runTest {
        fakeEngine.downloadError = RuntimeException("Download timeout")

        viewModel.state.test {
            awaitItem() // initial IDLE

            viewModel.startTest()

            val finalState = expectMostRecentItem()
            assertFalse(finalState.isRunning)
            assertEquals("Download timeout", finalState.error)
        }
    }

    @Test
    fun `upload error shows error and stops`() = runTest {
        fakeEngine.uploadError = RuntimeException("Upload failed")

        viewModel.state.test {
            awaitItem() // initial IDLE

            viewModel.startTest()

            val finalState = expectMostRecentItem()
            assertFalse(finalState.isRunning)
            assertEquals("Upload failed", finalState.error)
        }
    }

    @Test
    fun `cancelTest stops running test`() = runTest {
        viewModel.state.test {
            awaitItem() // initial IDLE

            viewModel.startTest()
            viewModel.cancelTest()

            val finalState = expectMostRecentItem()
            assertFalse(finalState.isRunning)
        }
    }

    @Test
    fun `buildExportText formats results correctly`() = runTest {
        viewModel.startTest()

        val text = viewModel.buildExportText()
        assertTrue(text.contains("Speed Test Results:"))
        assertTrue(text.contains("Download: 200.0 Mbps"))
        assertTrue(text.contains("Upload: 80.0 Mbps"))
        assertTrue(text.contains("Latency: 25 ms"))
    }

    @Test
    fun `does not save history when no results`() = runTest {
        fakeEngine.latencyError = RuntimeException("fail")

        viewModel.startTest()

        assertTrue(fakeHistoryDao.inserted.isEmpty())
    }

    @Test
    fun `multiple download progress updates reflect latest speed`() = runTest {
        fakeEngine.downloadProgress = listOf(
            SpeedProgress(
                bytesTransferred = 5_000_000L,
                elapsedMs = 500L,
                speedMbps = 80f,
                phase = SpeedTestPhase.DOWNLOAD,
            ),
            SpeedProgress(
                bytesTransferred = 15_000_000L,
                elapsedMs = 1000L,
                speedMbps = 120f,
                phase = SpeedTestPhase.DOWNLOAD,
            ),
            SpeedProgress(
                bytesTransferred = 25_000_000L,
                elapsedMs = 1500L,
                speedMbps = 133f,
                phase = SpeedTestPhase.DOWNLOAD,
            ),
        )

        viewModel.state.test {
            awaitItem()
            viewModel.startTest()
            val finalState = expectMostRecentItem()
            assertEquals(133f, finalState.downloadMbps)
        }
    }
}
