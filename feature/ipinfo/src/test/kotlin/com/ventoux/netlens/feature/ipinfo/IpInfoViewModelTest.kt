package com.ventoux.netlens.feature.ipinfo

import app.cash.turbine.test
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.ventoux.netlens.core.data.dao.IpInfoHistoryDao
import com.ventoux.netlens.core.data.model.IpInfoHistoryEntry
import com.ventoux.netlens.feature.ipinfo.data.FakeIpInfoRepository
import com.ventoux.netlens.feature.ipinfo.model.IpApiResponse
import com.ventoux.netlens.feature.ipinfo.model.IpInfoUiState

@OptIn(ExperimentalCoroutinesApi::class)
class IpInfoViewModelTest {

    private lateinit var fakeRepository: FakeIpInfoRepository
    private lateinit var viewModel: IpInfoViewModel

    private val fakeIpInfoHistoryDao = object : IpInfoHistoryDao {
        override fun getRecent(limit: Int): Flow<List<IpInfoHistoryEntry>> = flowOf(emptyList())
        override fun search(query: String, limit: Int): Flow<List<IpInfoHistoryEntry>> = flowOf(emptyList())
        override suspend fun insert(entry: IpInfoHistoryEntry) {}
        override suspend fun deleteById(id: Long) {}
        override suspend fun deleteOlderThan(before: Long) {}
        override suspend fun deleteAll() {}
    }

    private val testIpData = IpApiResponse(
        query = "203.0.113.1",
        isp = "Example ISP",
        org = "Example Org",
        asNumber = "AS12345",
        country = "United States",
        countryCode = "US",
        regionName = "California",
        city = "San Francisco",
        lat = 37.7749,
        lon = -122.4194,
        proxy = false,
        hosting = false,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeRepository = FakeIpInfoRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init auto-loads data and shows Success on success`() = runTest {
        fakeRepository.result = Result.success(testIpData)
        viewModel = IpInfoViewModel(fakeRepository, fakeIpInfoHistoryDao)

        viewModel.uiState.test {
            assertEquals(IpInfoUiState.Success(testIpData), awaitItem())
        }
    }

    @Test
    fun `init shows Loading while fetching`() = runTest {
        fakeRepository.result = Result.success(testIpData)
        fakeRepository.enableSuspend()
        viewModel = IpInfoViewModel(fakeRepository, fakeIpInfoHistoryDao)

        viewModel.uiState.test {
            assertEquals(IpInfoUiState.Loading, awaitItem())
            fakeRepository.resume()
            assertEquals(IpInfoUiState.Success(testIpData), awaitItem())
        }
    }

    @Test
    fun `init auto-loads data and shows Error on failure`() = runTest {
        fakeRepository.result = Result.failure(RuntimeException("Network error"))
        viewModel = IpInfoViewModel(fakeRepository, fakeIpInfoHistoryDao)

        viewModel.uiState.test {
            assertEquals(IpInfoUiState.Error("Network error"), awaitItem())
        }
    }

    @Test
    fun `refresh with success shows Success state`() = runTest {
        fakeRepository.result = Result.success(testIpData)
        viewModel = IpInfoViewModel(fakeRepository, fakeIpInfoHistoryDao)

        // Change the fake to return different data
        val updatedData = testIpData.copy(query = "198.51.100.1", city = "New York")
        fakeRepository.result = Result.success(updatedData)

        viewModel.uiState.test {
            awaitItem() // current state from init
            viewModel.refresh()
            assertEquals(IpInfoUiState.Success(updatedData), awaitItem())
        }
    }

    @Test
    fun `refresh with failure shows Error state`() = runTest {
        fakeRepository.result = Result.success(testIpData)
        viewModel = IpInfoViewModel(fakeRepository, fakeIpInfoHistoryDao)

        fakeRepository.result = Result.failure(RuntimeException("Timeout"))

        viewModel.uiState.test {
            awaitItem() // current Success state from init
            viewModel.refresh()
            assertEquals(IpInfoUiState.Error("Timeout"), awaitItem())
        }
    }

    @Test
    fun `refresh with failure and null message shows default error`() = runTest {
        fakeRepository.result = Result.success(testIpData)
        viewModel = IpInfoViewModel(fakeRepository, fakeIpInfoHistoryDao)

        fakeRepository.result = Result.failure(RuntimeException())

        viewModel.uiState.test {
            awaitItem() // current Success state from init
            viewModel.refresh()
            assertEquals(IpInfoUiState.Error("Unknown error"), awaitItem())
        }
    }

    @Test
    fun `refresh after error recovers to Success`() = runTest {
        fakeRepository.result = Result.failure(RuntimeException("fail"))
        viewModel = IpInfoViewModel(fakeRepository, fakeIpInfoHistoryDao)

        fakeRepository.result = Result.success(testIpData)

        viewModel.uiState.test {
            awaitItem() // current Error state from init
            viewModel.refresh()
            assertEquals(IpInfoUiState.Success(testIpData), awaitItem())
        }
    }
}
