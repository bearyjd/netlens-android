package com.ventouxlabs.netlens.feature.devices

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.data.secure.KeyValueStore
import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var knownDao: FakeKnownDeviceDao
    private lateinit var watchedDao: FakeWatchedNetworkDao
    private lateinit var identity: FakeNetworkIdentity
    private lateinit var userPreferences: UserPreferencesRepository
    private lateinit var scheduler: RecordingWatchScheduler
    private lateinit var viewModel: DevicesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        knownDao = FakeKnownDeviceDao()
        watchedDao = FakeWatchedNetworkDao()
        identity = FakeNetworkIdentity()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        val fakeKeyValueStore = object : KeyValueStore {
            private val map = mutableMapOf<String, String>()
            override fun getString(key: String): String? = map[key]?.takeIf { it.isNotBlank() }
            override fun putString(key: String, value: String?) {
                if (value.isNullOrBlank()) map.remove(key) else map[key] = value
            }
        }
        userPreferences = UserPreferencesRepository(dataStore, fakeKeyValueStore)
        scheduler = RecordingWatchScheduler()
        viewModel = DevicesViewModel(knownDao, watchedDao, identity, userPreferences, scheduler)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `search filters by display name`() = runTest {
        knownDao.seed(KnownDeviceEntity(macAddress = "M1", hostname = "printer", ip = "192.168.1.2", vendor = null))
        knownDao.seed(KnownDeviceEntity(macAddress = "M2", hostname = "laptop", ip = "192.168.1.3", vendor = null))
        viewModel.setSearchQuery("print")
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(1, state.devices.size)
            assertEquals("printer", state.devices.first().hostname)
        }
    }

    @Test
    fun `rename persists a trimmed custom name`() = runTest {
        knownDao.seed(KnownDeviceEntity(id = 5, macAddress = "M1", hostname = "h", ip = "192.168.1.2", vendor = null))
        viewModel.rename(5, "  Living Room TV  ")
        assertEquals("Living Room TV", knownDao.byId(5)?.customName)
    }

    @Test
    fun `rename with blank clears the custom name`() = runTest {
        knownDao.seed(KnownDeviceEntity(id = 5, macAddress = "M1", hostname = "h", ip = "192.168.1.2", vendor = null, customName = "Old"))
        viewModel.rename(5, "   ")
        assertNull(knownDao.byId(5)?.customName)
    }

    @Test
    fun `toggleKnown flips the flag`() = runTest {
        knownDao.seed(KnownDeviceEntity(id = 7, macAddress = "M1", hostname = "h", ip = "192.168.1.2", vendor = null, isKnown = false))
        viewModel.toggleKnown(7)
        assertTrue(knownDao.byId(7)?.isKnown == true)
    }

    @Test
    fun `watchCurrentNetwork captures gateway identity into a watched network`() = runTest {
        identity.gatewayMac = "AA:BB:CC:DD:EE:FF"
        identity.subnet = "192.168.1.0/24"
        identity.ssid = "HomeWiFi"
        viewModel.watchCurrentNetwork()
        val watched = watchedDao.getByGatewayMac("AA:BB:CC:DD:EE:FF")
        assertEquals("HomeWiFi", watched?.displayName)
        assertEquals("192.168.1.0/24", watched?.subnet)
    }

    @Test
    fun `watchCurrentNetwork is a no-op when gateway is unresolvable`() = runTest {
        identity.gatewayMac = null
        viewModel.watchCurrentNetwork()
        assertTrue(watchedDao.networks.isEmpty())
    }

    @Test
    fun `setCadence persists the cadence`() = runTest {
        viewModel.setCadence(WatchCadence.SIX_HOURS, isPro = false)
        assertEquals(360, userPreferences.watchCadenceMinutes.first())
    }

    @Test
    fun `setMasterWatch enqueues only when pro and master enabled`() = runTest {
        viewModel.setMasterWatch(enabled = true, isPro = true)
        assertTrue(scheduler.calls.any { it.isPro && it.masterEnabled })
    }

    @Test
    fun `setMasterWatch schedules with the newly-set value, not a stale uiState read`() = runTest {
        // A dispatcher that does NOT run launched coroutines inline (unlike UnconfinedTestDispatcher
        // used by the other tests in this class) so the persist-then-schedule ordering is actually
        // exercised, the same way it would run on a real Android main-thread dispatcher.
        val (vm, standardScheduler) = buildViewModelOnStandardDispatcher()

        vm.setMasterWatch(enabled = true, isPro = true)

        // Nothing has run yet: proves the schedule call is not observable until the coroutine
        // that persists AND schedules actually executes.
        assertTrue(standardScheduler.calls.isEmpty())

        advanceUntilIdle()

        // The scheduler call must reflect the value just set (true), never a re-read of the
        // pre-update uiState (which would still report false at this point).
        assertEquals(1, standardScheduler.calls.size)
        assertTrue(standardScheduler.calls.single().masterEnabled)
    }

    @Test
    fun `setCadence schedules with the newly-set cadence, not a stale uiState read`() = runTest {
        val (vm, standardScheduler) = buildViewModelOnStandardDispatcher()

        vm.setCadence(WatchCadence.SIX_HOURS, isPro = true)
        assertTrue(standardScheduler.calls.isEmpty())

        advanceUntilIdle()

        assertEquals(1, standardScheduler.calls.size)
        assertEquals(WatchCadence.SIX_HOURS, standardScheduler.calls.single().cadence)
    }

    private fun kotlinx.coroutines.test.TestScope.buildViewModelOnStandardDispatcher():
        Pair<DevicesViewModel, RecordingWatchScheduler> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
            produceFile = { File(tempDir, "standard_prefs_${System.nanoTime()}.preferences_pb") },
        )
        val fakeKeyValueStore = object : KeyValueStore {
            private val map = mutableMapOf<String, String>()
            override fun getString(key: String): String? = map[key]?.takeIf { it.isNotBlank() }
            override fun putString(key: String, value: String?) {
                if (value.isNullOrBlank()) map.remove(key) else map[key] = value
            }
        }
        val prefs = UserPreferencesRepository(dataStore, fakeKeyValueStore)
        val standardScheduler = RecordingWatchScheduler()
        val vm = DevicesViewModel(knownDao, watchedDao, identity, prefs, standardScheduler)
        return vm to standardScheduler
    }
}
