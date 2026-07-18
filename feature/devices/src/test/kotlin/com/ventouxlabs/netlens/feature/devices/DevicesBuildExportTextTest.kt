package com.ventouxlabs.netlens.feature.devices

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.data.secure.KeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesBuildExportTextTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var knownDao: FakeKnownDeviceDao
    private lateinit var viewModel: DevicesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        knownDao = FakeKnownDeviceDao()
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
        val userPreferences = UserPreferencesRepository(dataStore, fakeKeyValueStore)
        viewModel = DevicesViewModel(
            knownDao,
            FakeWatchedNetworkDao(),
            FakeNetworkIdentity(),
            userPreferences,
            RecordingWatchScheduler(),
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `export lists each device with custom name precedence`() = runTest {
        knownDao.seed(
            KnownDeviceEntity(
                id = 1,
                macAddress = "AA:BB",
                hostname = "raw-host",
                ip = "192.168.1.5",
                vendor = "Acme",
                customName = "Office Printer",
            ),
        )
        val text = viewModel.buildExportText()
        assertTrue(text.contains("Office Printer"))
        assertTrue(text.contains("192.168.1.5"))
        assertTrue(text.contains("AA:BB"))
    }
}
