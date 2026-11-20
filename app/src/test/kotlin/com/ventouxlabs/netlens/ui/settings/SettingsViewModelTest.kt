package com.ventouxlabs.netlens.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.data.secure.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @TempDir
    lateinit var tempDir: File

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var repository: UserPreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScope.testScheduler))
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { File(tempDir, "settings_vm_prefs.preferences_pb") },
        )
        val store = object : KeyValueStore {
            private val map = mutableMapOf<String, String>()
            override fun getString(key: String): String? = map[key]
            override fun putString(key: String, value: String?) {
                if (value.isNullOrBlank()) map.remove(key) else map[key] = value
            }
        }
        repository = UserPreferencesRepository(dataStore, store)
        viewModel = SettingsViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        testScope.cancel()
    }

    @Test
    fun `valid resolver is saved and clears error`() = testScope.runTest {
        viewModel.setDnsResolver(" 1.1.1.1 ")
        assertEquals("1.1.1.1", repository.dnsResolverOverride.first())
        assertFalse(viewModel.dnsResolverError.value)
    }

    @Test
    fun `invalid resolver sets error and keeps previous value`() = testScope.runTest {
        viewModel.setDnsResolver("9.9.9.9")
        viewModel.setDnsResolver("dns.example.com")
        assertTrue(viewModel.dnsResolverError.value)
        assertEquals("9.9.9.9", repository.dnsResolverOverride.first())

        viewModel.setDnsResolver("8.8.4.4")
        assertFalse(viewModel.dnsResolverError.value)
    }

    @Test
    fun `blank resolver resets to default`() = testScope.runTest {
        viewModel.setDnsResolver("9.9.9.9")
        viewModel.setDnsResolver("   ")
        assertEquals("", repository.dnsResolverOverride.first())
        assertFalse(viewModel.dnsResolverError.value)
    }
}
