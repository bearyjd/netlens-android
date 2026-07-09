package com.ventouxlabs.netlens.feature.widgetsettings

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.data.secure.KeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSettingsViewModelTest {

    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var viewModel: WidgetSettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeDataStore = FakeDataStore()
        userPreferencesRepository = UserPreferencesRepository(fakeDataStore, FakeKeyValueStore())
        viewModel = WidgetSettingsViewModel(Application(), userPreferencesRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ipInfoConsent defaults to false`() = runTest {
        viewModel.ipInfoConsent.test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `setIpInfoConsent true updates state and underlying repository`() = runTest {
        viewModel.ipInfoConsent.test {
            assertEquals(false, awaitItem())
            viewModel.setIpInfoConsent(true)
            assertEquals(true, awaitItem())
        }
        userPreferencesRepository.ipInfoConsentGranted.test {
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `setIpInfoConsent false reverts a granted consent`() = runTest {
        viewModel.setIpInfoConsent(true)

        viewModel.ipInfoConsent.test {
            assertEquals(true, awaitItem())
            viewModel.setIpInfoConsent(false)
            assertEquals(false, awaitItem())
        }
    }
}

private class FakeKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]?.takeIf { it.isNotBlank() }
    override fun putString(key: String, value: String?) {
        if (value.isNullOrBlank()) map.remove(key) else map[key] = value
    }
}

private class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
