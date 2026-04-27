package com.ventoux.netlens.feature.posture

import app.cash.turbine.test
import com.ventoux.netlens.core.data.dao.LanScanHistoryDao
import com.ventoux.netlens.core.data.model.LanScanHistoryEntry
import com.ventoux.netlens.core.network.NetworkMonitor
import com.ventoux.netlens.feature.posture.engine.EncryptionTypeProvider
import com.ventoux.netlens.feature.posture.model.PostureUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var networkMonitor: FakeNetworkMonitor
    private lateinit var encryptionProvider: FakeEncryptionTypeProvider
    private lateinit var lanScanDao: FakeLanScanHistoryDao

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        networkMonitor = FakeNetworkMonitor()
        encryptionProvider = FakeEncryptionTypeProvider()
        lanScanDao = FakeLanScanHistoryDao()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = PostureViewModel(
        networkMonitor = networkMonitor,
        encryptionTypeProvider = encryptionProvider,
        lanScanHistoryDao = lanScanDao,
    )

    @Test
    fun `initial state is Loading`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            assertEquals(PostureUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits Disconnected when offline`() = runTest {
        networkMonitor.online.value = false
        val vm = createViewModel()
        vm.uiState.test {
            assertEquals(PostureUiState.Loading, awaitItem())
            assertEquals(PostureUiState.Disconnected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits Scored with grade A for WPA3 VPN and few devices`() = runTest {
        networkMonitor.online.value = true
        networkMonitor.vpn.value = true
        encryptionProvider.encryptionType = "WPA3"
        lanScanDao.entries.add(
            LanScanHistoryEntry(
                ssid = "Test",
                subnet = "192.168.1.0/24",
                deviceCount = 3,
                devicesJson = "[]",
            ),
        )
        val vm = createViewModel()
        vm.uiState.test {
            assertEquals(PostureUiState.Loading, awaitItem())
            val scored = awaitItem()
            assertTrue(scored is PostureUiState.Scored)
            assertEquals("A", (scored as PostureUiState.Scored).score.grade)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh recalculates score`() = runTest {
        networkMonitor.online.value = true
        networkMonitor.vpn.value = true
        encryptionProvider.encryptionType = "WPA2"
        lanScanDao.entries.add(
            LanScanHistoryEntry(
                ssid = "Test",
                subnet = "192.168.1.0/24",
                deviceCount = 3,
                devicesJson = "[]",
            ),
        )
        val vm = createViewModel()
        vm.uiState.test {
            assertEquals(PostureUiState.Loading, awaitItem())
            val initial = awaitItem()
            assertTrue(initial is PostureUiState.Scored)
            encryptionProvider.encryptionType = "WPA3"
            vm.refresh()
            val refreshed = awaitItem()
            assertTrue(refreshed is PostureUiState.Scored)
            assertEquals("A", (refreshed as PostureUiState.Scored).score.grade)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh while offline emits Disconnected`() = runTest {
        networkMonitor.online.value = true
        val vm = createViewModel()
        vm.uiState.test {
            assertEquals(PostureUiState.Loading, awaitItem())
            awaitItem() // initial scored
            networkMonitor.online.value = false
            assertEquals(PostureUiState.Disconnected, awaitItem())
            vm.refresh()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `online with no scan history excludes device count factor`() = runTest {
        networkMonitor.online.value = true
        networkMonitor.vpn.value = true
        encryptionProvider.encryptionType = "WPA3"
        val vm = createViewModel()
        vm.uiState.test {
            assertEquals(PostureUiState.Loading, awaitItem())
            val scored = awaitItem()
            assertTrue(scored is PostureUiState.Scored)
            val score = (scored as PostureUiState.Scored).score
            assertEquals(2, score.factors.size)
            assertEquals("A", score.grade)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error in recalculate emits Error state`() = runTest {
        networkMonitor.online.value = true
        encryptionProvider.shouldThrow = true
        val vm = createViewModel()
        vm.uiState.test {
            assertEquals(PostureUiState.Loading, awaitItem())
            val error = awaitItem()
            assertTrue(error is PostureUiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeNetworkMonitor : NetworkMonitor {
    val online = MutableStateFlow(true)
    val vpn = MutableStateFlow(false)
    override val isOnline: Flow<Boolean> = online
    override val isVpnActive: Flow<Boolean> = vpn
}

private class FakeEncryptionTypeProvider : EncryptionTypeProvider {
    var encryptionType: String? = "WPA2"
    var shouldThrow = false
    override fun currentEncryptionType(): String? {
        if (shouldThrow) throw RuntimeException("Test error")
        return encryptionType
    }
}

private class FakeLanScanHistoryDao : LanScanHistoryDao {
    val entries = mutableListOf<LanScanHistoryEntry>()
    override fun getRecent(limit: Int): Flow<List<LanScanHistoryEntry>> = flowOf(entries.toList())
    override fun search(query: String, limit: Int): Flow<List<LanScanHistoryEntry>> =
        flowOf(entries.filter { it.subnet?.contains(query) == true })
    override suspend fun getById(id: Long): LanScanHistoryEntry? = entries.find { it.id == id }
    override suspend fun insert(entry: LanScanHistoryEntry) { entries.add(entry) }
    override suspend fun deleteById(id: Long) { entries.removeAll { it.id == id } }
    override suspend fun deleteOlderThan(before: Long) { entries.removeAll { it.timestamp < before } }
    override suspend fun deleteAll() { entries.clear() }
}
