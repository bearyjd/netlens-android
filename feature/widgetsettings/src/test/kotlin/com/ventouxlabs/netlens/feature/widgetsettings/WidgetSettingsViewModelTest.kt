package com.ventouxlabs.netlens.feature.widgetsettings

import android.app.Application
import app.cash.turbine.test
import com.ventouxlabs.netlens.core.data.testing.FakeDataStore
import com.ventouxlabs.netlens.core.data.testing.FakeKeyValueStore
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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


