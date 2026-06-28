package com.ventouxlabs.netlens.feature.ipinfo

import app.cash.turbine.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import com.ventouxlabs.netlens.core.data.dao.IpInfoHistoryDao
import com.ventouxlabs.netlens.core.data.model.IpInfoHistoryEntry
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.data.secure.KeyValueStore
import com.ventouxlabs.netlens.feature.ipinfo.data.FakeIpInfoRepository
import com.ventouxlabs.netlens.feature.ipinfo.data.ReputationClient
import com.ventouxlabs.netlens.feature.ipinfo.model.IpInfoResponse
import com.ventouxlabs.netlens.feature.ipinfo.model.IpInfoUiState

@OptIn(ExperimentalCoroutinesApi::class)
class IpInfoViewModelTest {

    private lateinit var fakeRepository: FakeIpInfoRepository
    private lateinit var viewModel: IpInfoViewModel
    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    // Never invoked by these tests: fetchReputation() returns early while the
    // AbuseIPDB API key is blank, so the MockEngine handler is never reached.
    private val reputationClient = ReputationClient(HttpClient(MockEngine { respond("") }))

    private val fakeIpInfoHistoryDao = object : IpInfoHistoryDao {
        override fun getRecent(limit: Int): Flow<List<IpInfoHistoryEntry>> = flowOf(emptyList())
        override fun search(query: String, limit: Int): Flow<List<IpInfoHistoryEntry>> = flowOf(emptyList())
        override suspend fun getById(id: Long): IpInfoHistoryEntry? = null
        override suspend fun insert(entry: IpInfoHistoryEntry) {}
        override suspend fun deleteById(id: Long) {}
        override suspend fun deleteOlderThan(before: Long) {}
        override suspend fun deleteAll() {}
    }

    private val testIpData = IpInfoResponse(
        ip = "203.0.113.1",
        hostname = "example.com",
        city = "San Francisco",
        region = "California",
        country = "US",
        loc = "37.7749,-122.4194",
        org = "AS13335 Cloudflare, Inc.",
        postal = "94107",
        timezone = "America/Los_Angeles",
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeRepository = FakeIpInfoRepository()
        fakeDataStore = FakeDataStore()
        userPreferencesRepository = UserPreferencesRepository(fakeDataStore, FakeKeyValueStore())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init shows ConsentRequired when consent not granted`() = runTest {
        // consent not granted (default false)
        viewModel = IpInfoViewModel(fakeRepository, reputationClient, fakeIpInfoHistoryDao, userPreferencesRepository)

        viewModel.uiState.test {
            assertEquals(IpInfoUiState.ConsentRequired, awaitItem())
        }
    }

    @Test
    fun `grantConsent transitions from ConsentRequired to Success`() = runTest {
        fakeRepository.result = Result.success(testIpData)
        viewModel = IpInfoViewModel(fakeRepository, reputationClient, fakeIpInfoHistoryDao, userPreferencesRepository)

        viewModel.uiState.test {
            assertEquals(IpInfoUiState.ConsentRequired, awaitItem())
            viewModel.grantConsent()
            assertEquals(IpInfoUiState.Success(testIpData), awaitItem())
        }
    }

    @Test
    fun `init auto-loads data and shows Success when consent already granted`() = runTest {
        fakeDataStore.setConsent(true)
        fakeRepository.result = Result.success(testIpData)
        viewModel = IpInfoViewModel(fakeRepository, reputationClient, fakeIpInfoHistoryDao, userPreferencesRepository)

        viewModel.uiState.test {
            assertEquals(IpInfoUiState.Success(testIpData), awaitItem())
        }
    }

    @Test
    fun `init shows Loading while fetching when consent granted`() = runTest {
        fakeDataStore.setConsent(true)
        fakeRepository.result = Result.success(testIpData)
        fakeRepository.enableSuspend()
        viewModel = IpInfoViewModel(fakeRepository, reputationClient, fakeIpInfoHistoryDao, userPreferencesRepository)

        viewModel.uiState.test {
            assertEquals(IpInfoUiState.Loading, awaitItem())
            fakeRepository.resume()
            assertEquals(IpInfoUiState.Success(testIpData), awaitItem())
        }
    }

    @Test
    fun `init auto-loads data and shows Error on failure when consent granted`() = runTest {
        fakeDataStore.setConsent(true)
        fakeRepository.result = Result.failure(RuntimeException("Network error"))
        viewModel = IpInfoViewModel(fakeRepository, reputationClient, fakeIpInfoHistoryDao, userPreferencesRepository)

        viewModel.uiState.test {
            assertEquals(IpInfoUiState.Error("Network error"), awaitItem())
        }
    }

    @Test
    fun `refresh with success shows Success state`() = runTest {
        fakeDataStore.setConsent(true)
        fakeRepository.result = Result.success(testIpData)
        viewModel = IpInfoViewModel(fakeRepository, reputationClient, fakeIpInfoHistoryDao, userPreferencesRepository)

        val updatedData = testIpData.copy(ip = "198.51.100.1", city = "New York")
        fakeRepository.result = Result.success(updatedData)

        viewModel.uiState.test {
            awaitItem() // current state from init
            viewModel.refresh()
            assertEquals(IpInfoUiState.Success(updatedData), awaitItem())
        }
    }

    @Test
    fun `refresh with failure shows Error state`() = runTest {
        fakeDataStore.setConsent(true)
        fakeRepository.result = Result.success(testIpData)
        viewModel = IpInfoViewModel(fakeRepository, reputationClient, fakeIpInfoHistoryDao, userPreferencesRepository)

        fakeRepository.result = Result.failure(RuntimeException("Timeout"))

        viewModel.uiState.test {
            awaitItem() // current Success state from init
            viewModel.refresh()
            assertEquals(IpInfoUiState.Error("Timeout"), awaitItem())
        }
    }

    @Test
    fun `refresh with failure and null message shows default error`() = runTest {
        fakeDataStore.setConsent(true)
        fakeRepository.result = Result.success(testIpData)
        viewModel = IpInfoViewModel(fakeRepository, reputationClient, fakeIpInfoHistoryDao, userPreferencesRepository)

        fakeRepository.result = Result.failure(RuntimeException())

        viewModel.uiState.test {
            awaitItem() // current Success state from init
            viewModel.refresh()
            assertEquals(IpInfoUiState.Error("Unknown error"), awaitItem())
        }
    }

    @Test
    fun `refresh after error recovers to Success`() = runTest {
        fakeDataStore.setConsent(true)
        fakeRepository.result = Result.failure(RuntimeException("fail"))
        viewModel = IpInfoViewModel(fakeRepository, reputationClient, fakeIpInfoHistoryDao, userPreferencesRepository)

        fakeRepository.result = Result.success(testIpData)

        viewModel.uiState.test {
            awaitItem() // current Error state from init
            viewModel.refresh()
            assertEquals(IpInfoUiState.Success(testIpData), awaitItem())
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

private val IPINFO_CONSENT_KEY = booleanPreferencesKey("ipinfo_consent_granted")

private class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }

    fun setConsent(value: Boolean) {
        val mutable = state.value.toMutablePreferences()
        mutable[IPINFO_CONSENT_KEY] = value
        state.value = mutable
    }
}
