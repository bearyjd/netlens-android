package com.ventoux.netlens.feature.dns

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.ventoux.netlens.core.data.dao.DnsHistoryDao
import com.ventoux.netlens.core.data.model.DnsHistoryEntry
import com.ventoux.netlens.feature.dns.engine.FakeDnsResolver
import com.ventoux.netlens.feature.dns.model.DnsError
import com.ventoux.netlens.feature.dns.model.DnsLookupUiState
import com.ventoux.netlens.feature.dns.model.DnsRecordType
import com.ventoux.netlens.feature.dns.model.DnsResult

@OptIn(ExperimentalCoroutinesApi::class)
class DnsLookupViewModelTest {

    private lateinit var fakeDnsResolver: FakeDnsResolver
    private lateinit var viewModel: DnsLookupViewModel

    private val fakeDnsHistoryDao = object : DnsHistoryDao {
        override fun getRecent(limit: Int): Flow<List<DnsHistoryEntry>> = flowOf(emptyList())
        override fun search(searchQuery: String, limit: Int): Flow<List<DnsHistoryEntry>> = flowOf(emptyList())
        override suspend fun getById(id: Long): DnsHistoryEntry? = null
        override suspend fun insert(entry: DnsHistoryEntry) {}
        override suspend fun deleteById(id: Long) {}
        override suspend fun deleteOlderThan(before: Long) {}
        override suspend fun deleteAll() {}
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeDnsResolver = FakeDnsResolver()
        viewModel = DnsLookupViewModel(fakeDnsResolver, fakeDnsHistoryDao)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default values`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals("", state.domain)
            assertEquals(setOf(DnsRecordType.A, DnsRecordType.AAAA), state.selectedTypes)
            assertEquals(emptyList<DnsResult>(), state.results)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }
    }

    @Test
    fun `onDomainChanged updates domain`() = runTest {
        viewModel.state.test {
            awaitItem() // initial state
            viewModel.onDomainChanged("example.com")
            assertEquals("example.com", awaitItem().domain)
        }
    }

    @Test
    fun `onTypeToggled adds type not in selection`() = runTest {
        viewModel.state.test {
            awaitItem() // initial state has A, AAAA
            viewModel.onTypeToggled(DnsRecordType.MX)
            val state = awaitItem()
            assertTrue(state.selectedTypes.contains(DnsRecordType.MX))
            assertTrue(state.selectedTypes.contains(DnsRecordType.A))
            assertTrue(state.selectedTypes.contains(DnsRecordType.AAAA))
        }
    }

    @Test
    fun `onTypeToggled removes type already in selection`() = runTest {
        viewModel.state.test {
            awaitItem() // initial state has A, AAAA
            viewModel.onTypeToggled(DnsRecordType.A)
            val state = awaitItem()
            assertFalse(state.selectedTypes.contains(DnsRecordType.A))
            assertTrue(state.selectedTypes.contains(DnsRecordType.AAAA))
        }
    }

    @Test
    fun `lookup with blank domain sets error`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.lookup()
            assertEquals(DnsError.EmptyDomain, awaitItem().error)
        }
    }

    @Test
    fun `lookup with empty selected types sets error`() = runTest {
        viewModel.onDomainChanged("example.com")
        // Remove both default types
        viewModel.onTypeToggled(DnsRecordType.A)
        viewModel.onTypeToggled(DnsRecordType.AAAA)

        viewModel.state.test {
            awaitItem() // current state
            viewModel.lookup()
            assertEquals(DnsError.NoTypes, awaitItem().error)
        }
    }

    @Test
    fun `lookup with success shows results`() = runTest {
        val expectedResults = listOf(
            DnsResult(DnsRecordType.A, "example.com", "93.184.216.34", 3600),
        )
        fakeDnsResolver.result = Result.success(expectedResults)
        viewModel.onDomainChanged("example.com")

        viewModel.state.test {
            awaitItem() // current state
            viewModel.lookup()
            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading)
            val finalState = awaitItem()
            assertFalse(finalState.isLoading)
            assertEquals(expectedResults, finalState.results)
            assertNull(finalState.error)
        }
    }

    @Test
    fun `lookup with failure shows error`() = runTest {
        fakeDnsResolver.result = Result.failure(RuntimeException("Network error"))
        viewModel.onDomainChanged("example.com")

        viewModel.state.test {
            awaitItem() // current state
            viewModel.lookup()
            awaitItem() // loading state
            val finalState = awaitItem()
            assertFalse(finalState.isLoading)
            assertEquals(DnsError.LookupFailed("Network error"), finalState.error)
            assertEquals(emptyList<DnsResult>(), finalState.results)
        }
    }

    @Test
    fun `lookup with failure and null message shows default error`() = runTest {
        fakeDnsResolver.result = Result.failure(RuntimeException())
        viewModel.onDomainChanged("example.com")

        viewModel.state.test {
            awaitItem() // current state
            viewModel.lookup()
            awaitItem() // loading state
            val finalState = awaitItem()
            assertEquals(DnsError.LookupFailed(null), finalState.error)
        }
    }

    @Test
    fun `lookup clears previous error and results`() = runTest {
        fakeDnsResolver.result = Result.failure(RuntimeException("fail"))
        viewModel.onDomainChanged("example.com")
        viewModel.lookup() // first lookup fails

        val successResults = listOf(
            DnsResult(DnsRecordType.A, "example.com", "93.184.216.34", 3600),
        )
        fakeDnsResolver.result = Result.success(successResults)

        viewModel.state.test {
            awaitItem() // current error state
            viewModel.lookup()
            awaitItem() // loading state
            val finalState = awaitItem()
            assertNull(finalState.error)
            assertEquals(successResults, finalState.results)
        }
    }

    @Test
    fun `lookup trims domain whitespace`() = runTest {
        val expectedResults = listOf(
            DnsResult(DnsRecordType.A, "example.com", "93.184.216.34", 3600),
        )
        fakeDnsResolver.result = Result.success(expectedResults)
        viewModel.onDomainChanged("  example.com  ")

        viewModel.state.test {
            awaitItem() // current state
            viewModel.lookup()
            awaitItem() // loading state
            val finalState = awaitItem()
            assertEquals(expectedResults, finalState.results)
            assertEquals("example.com", fakeDnsResolver.lastDomain)
        }
    }
}
