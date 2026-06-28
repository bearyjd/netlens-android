package com.ventouxlabs.netlens.feature.posture

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.ventouxlabs.netlens.core.data.dao.LanScanHistoryDao
import com.ventouxlabs.netlens.core.data.model.LanScanHistoryEntry
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.network.NetworkMonitor
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.feature.posture.engine.EncryptionTypeProvider
import com.ventouxlabs.netlens.feature.posture.model.PostureUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PostureViewModelTest {

    @TempDir
    lateinit var tempDir: File

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var networkMonitor: FakeNetworkMonitor
    private lateinit var encryptionProvider: FakeEncryptionTypeProvider
    private lateinit var lanScanDao: FakeLanScanHistoryDao
    private lateinit var preferences: UserPreferencesRepository
    private val createdViewModels = mutableListOf<PostureViewModel>()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        networkMonitor = FakeNetworkMonitor()
        encryptionProvider = FakeEncryptionTypeProvider()
        lanScanDao = FakeLanScanHistoryDao()
        // Bind the DataStore's internal scope to testScope.backgroundScope so its
        // async update work is structured-cancelled with the test, instead of
        // resuming on Dispatchers.Main after resetMain() ran. See issue #73.
        preferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(
                scope = testScope.backgroundScope,
                produceFile = { File(tempDir, "test_prefs.preferences_pb") },
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        // 1. Cancel each VM's viewModelScope so its observeNetwork() collector
        //    doesn't leak as UncaughtExceptionsBeforeTest.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        // 2. Cancel the test scope so DataStore's IO update work stops before
        //    the Main dispatcher is reset.
        testScope.cancel()
        // 3. Reset Main last.
        Dispatchers.resetMain()
    }

    private fun createViewModel() = PostureViewModel(
        networkMonitor = networkMonitor,
        encryptionTypeProvider = encryptionProvider,
        lanScanHistoryDao = lanScanDao,
        preferences = preferences,
    ).also { createdViewModels.add(it) }

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
        networkMonitor.vpn.value = VpnState.FullTunnel
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
        networkMonitor.vpn.value = VpnState.FullTunnel
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
        networkMonitor.vpn.value = VpnState.FullTunnel
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

    @Test
    fun `persist failure preserves Scored state`() = runTest {
        networkMonitor.online.value = true
        networkMonitor.vpn.value = VpnState.FullTunnel
        encryptionProvider.encryptionType = "WPA3"
        lanScanDao.entries.add(
            LanScanHistoryEntry(
                ssid = "Test",
                subnet = "192.168.1.0/24",
                deviceCount = 3,
                devicesJson = "[]",
            ),
        )
        val throwingPreferences = UserPreferencesRepository(ThrowingDataStore())
        val vm = PostureViewModel(
            networkMonitor = networkMonitor,
            encryptionTypeProvider = encryptionProvider,
            lanScanHistoryDao = lanScanDao,
            preferences = throwingPreferences,
        ).also { createdViewModels.add(it) }
        vm.uiState.test {
            assertEquals(PostureUiState.Loading, awaitItem())
            val scored = awaitItem()
            assertTrue(scored is PostureUiState.Scored)
            assertEquals("A", (scored as PostureUiState.Scored).score.grade)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeNetworkMonitor : NetworkMonitor {
    val online = MutableStateFlow(true)
    val vpn = MutableStateFlow<VpnState>(VpnState.None)
    override val isOnline: Flow<Boolean> = online
    override val vpnState: Flow<VpnState> = vpn
}

private class FakeEncryptionTypeProvider : EncryptionTypeProvider {
    var encryptionType: String? = "WPA2"
    var shouldThrow = false
    override fun currentEncryptionType(): String? {
        if (shouldThrow) throw RuntimeException("Test error")
        return encryptionType
    }
}

private class ThrowingDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = flowOf(emptyPreferences())
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        throw IOException("simulated DataStore write failure")
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
