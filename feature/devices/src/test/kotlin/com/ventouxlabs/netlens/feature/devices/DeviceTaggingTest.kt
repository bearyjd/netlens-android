package com.ventouxlabs.netlens.feature.devices

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.data.secure.KeyValueStore
import com.ventouxlabs.netlens.feature.devices.model.DeviceDetailsEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class DeviceTaggingTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var knownDao: FakeKnownDeviceDao
    private lateinit var viewModel: DevicesViewModel

    private val viewModelStore = ViewModelStore()
    private lateinit var dataStoreScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        knownDao = FakeKnownDeviceDao()
        // Cancelled in tearDown: a DataStore collector left running here resumes on a later
        // test's Main dispatcher and fails there instead, as an unexplained flake.
        dataStoreScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempDir, "tagging_prefs.preferences_pb") },
        )
        val keyValueStore = object : KeyValueStore {
            private val map = mutableMapOf<String, String>()
            override fun getString(key: String): String? = map[key]?.takeIf { it.isNotBlank() }
            override fun putString(key: String, value: String?) {
                if (value.isNullOrBlank()) map.remove(key) else map[key] = value
            }
        }
        viewModel = DevicesViewModel(
            knownDeviceDao = knownDao,
            watchedNetworkDao = FakeWatchedNetworkDao(),
            networkIdentity = FakeNetworkIdentity(),
            userPreferences = UserPreferencesRepository(dataStore, keyValueStore),
            watchScheduler = RecordingWatchScheduler(),
            defaultDispatcher = UnconfinedTestDispatcher(),
        )
        viewModelStore.put("devices", viewModel)
    }

    @AfterEach
    fun tearDown() {
        viewModelStore.clear()
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    private fun seed(
        id: Long,
        hostname: String? = null,
        ip: String = "192.168.1.$id",
        tags: String? = null,
        location: String? = null,
    ) {
        knownDao.seed(
            KnownDeviceEntity(
                id = id,
                macAddress = "M$id",
                hostname = hostname,
                ip = ip,
                vendor = null,
                tags = tags,
                location = location,
            ),
        )
    }

    @Test
    fun `saveDetails normalises and persists every user field`() = runTest {
        seed(1, hostname = "printer")

        viewModel.saveDetails(
            1,
            DeviceDetailsEdit(
                customName = "  Office Printer  ",
                tagsInput = "printer,  Paper , printer",
                location = "  Study ",
                notes = " Toner low ",
            ),
        )

        val saved = knownDao.byId(1)!!
        assertEquals("Office Printer", saved.customName)
        // Duplicate tag dropped case-insensitively, whitespace trimmed.
        assertEquals("printer,Paper", saved.tags)
        assertEquals("Study", saved.location)
        assertEquals("Toner low", saved.notes)
    }

    @Test
    fun `saveDetails with empty fields clears them rather than storing blanks`() = runTest {
        seed(1, tags = "printer", location = "Study")

        viewModel.saveDetails(1, DeviceDetailsEdit())

        val saved = knownDao.byId(1)!!
        assertNull(saved.customName)
        assertNull(saved.tags)
        assertNull(saved.location)
        assertNull(saved.notes)
    }

    @Test
    fun `saveDetails leaves scan-derived columns untouched`() = runTest {
        seed(1, hostname = "printer", ip = "192.168.1.7")

        viewModel.saveDetails(1, DeviceDetailsEdit(customName = "Office Printer"))

        val saved = knownDao.byId(1)!!
        assertEquals("printer", saved.hostname)
        assertEquals("192.168.1.7", saved.ip)
        assertEquals("M1", saved.macAddress)
    }

    @Test
    fun `addTag appends without disturbing the other details`() = runTest {
        seed(1, tags = "printer", location = "Study")
        knownDao.seed(
            KnownDeviceEntity(id = 2, macAddress = "M2", hostname = null, ip = "1.1.1.1", vendor = null),
        )

        viewModel.addTag(1, "paper")

        val saved = knownDao.byId(1)!!
        assertEquals("printer,paper", saved.tags)
        assertEquals("Study", saved.location)
    }

    @Test
    fun `addTag is a no-op when the device already carries the tag`() = runTest {
        seed(1, tags = "Printer")

        viewModel.addTag(1, "printer")

        assertEquals("Printer", knownDao.byId(1)!!.tags)
    }

    @Test
    fun `available tags come from the whole inventory`() = runTest {
        seed(1, tags = "printer,iot")
        seed(2, tags = "camera")

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(listOf("camera", "iot", "printer"), state.availableTags)
        }
    }

    @Test
    fun `tag filter narrows the list and toggles back off`() = runTest {
        seed(1, hostname = "printer", tags = "printer")
        seed(2, hostname = "camera", tags = "camera")

        viewModel.toggleTagFilter("printer")
        viewModel.uiState.test {
            val filtered = expectMostRecentItem()
            assertEquals(1, filtered.devices.size)
            assertEquals("printer", filtered.devices.first().hostname)
            assertTrue(filtered.activeTags.contains("printer"))
        }

        viewModel.toggleTagFilter("printer")
        viewModel.uiState.test {
            val unfiltered = expectMostRecentItem()
            assertEquals(2, unfiltered.devices.size)
            assertTrue(unfiltered.activeTags.isEmpty())
        }
    }

    @Test
    fun `a filter tag that no longer exists is dropped instead of emptying the list`() = runTest {
        seed(1, hostname = "printer", tags = "printer")
        seed(2, hostname = "camera", tags = "camera")
        viewModel.toggleTagFilter("printer")

        // The only device carrying "printer" loses the tag.
        viewModel.saveDetails(1, DeviceDetailsEdit())

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.activeTags.isEmpty())
            assertEquals(2, state.devices.size)
        }
    }

    @Test
    fun `search reaches tags, notes and location`() = runTest {
        seed(1, hostname = "nas", tags = "storage")
        seed(2, hostname = "printer", location = "Study")

        viewModel.setSearchQuery("storage")
        viewModel.uiState.test {
            assertEquals(1, expectMostRecentItem().devices.size)
        }

        viewModel.setSearchQuery("study")
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(1, state.devices.size)
            assertEquals("printer", state.devices.first().hostname)
        }
    }

    @Test
    fun `export includes tags, location and notes`() = runTest {
        seed(1, hostname = "printer", tags = "printer,paper", location = "Study")
        viewModel.saveDetails(
            1,
            DeviceDetailsEdit(
                customName = "Office Printer",
                tagsInput = "printer, paper",
                location = "Study",
                notes = "Toner low",
            ),
        )

        val text = viewModel.buildExportText()
        assertTrue(text.contains("Office Printer"))
        assertTrue(text.contains("Location: Study"))
        assertTrue(text.contains("Tags: printer, paper"))
        assertTrue(text.contains("Note: Toner low"))
    }
}
