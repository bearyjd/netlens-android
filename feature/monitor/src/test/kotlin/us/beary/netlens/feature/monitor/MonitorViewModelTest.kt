package us.beary.netlens.feature.monitor

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
import us.beary.netlens.core.data.model.EndpointCheck
import us.beary.netlens.core.data.model.MonitoredEndpoint
import us.beary.netlens.feature.monitor.dao.FakeEndpointDao
import us.beary.netlens.feature.monitor.engine.FakeEndpointChecker
import us.beary.netlens.feature.monitor.model.MonitorUiState

@OptIn(ExperimentalCoroutinesApi::class)
class MonitorViewModelTest {

    private lateinit var dao: FakeEndpointDao
    private lateinit var checker: FakeEndpointChecker
    private lateinit var viewModel: MonitorViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dao = FakeEndpointDao()
        checker = FakeEndpointChecker()
        viewModel = MonitorViewModel(checker, dao)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty endpoints`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(emptyList<MonitoredEndpoint>(), state.endpoints)
            assertNull(state.selectedEndpoint)
            assertEquals(emptyList<EndpointCheck>(), state.checks)
            assertFalse(state.isChecking)
            assertNull(state.error)
        }
    }

    @Test
    fun `addEndpoint adds to DAO and updates state`() = runTest {
        viewModel.state.test {
            awaitItem() // initial empty state

            viewModel.addEndpoint("Example", "https://example.com", 120)

            val updated = awaitItem()
            assertEquals(1, updated.endpoints.size)
            assertEquals("Example", updated.endpoints[0].label)
            assertEquals("https://example.com", updated.endpoints[0].url)
            assertEquals(120, updated.endpoints[0].intervalSeconds)
        }
    }

    @Test
    fun `removeEndpoint removes from DAO and updates state`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.addEndpoint("Example", "https://example.com")
            val afterAdd = awaitItem()
            val endpoint = afterAdd.endpoints[0]

            viewModel.removeEndpoint(endpoint)
            val afterRemove = awaitItem()
            assertTrue(afterRemove.endpoints.isEmpty())
        }
    }

    @Test
    fun `removeEndpoint clears selection when removing selected endpoint`() = runTest {
        viewModel.state.test {
            awaitItem() // initial empty

            viewModel.addEndpoint("Example", "https://example.com")
            val afterAdd = awaitItem()
            val endpoint = afterAdd.endpoints[0]

            viewModel.selectEndpoint(endpoint)
            awaitItem() // selected state

            viewModel.removeEndpoint(endpoint)

            // After removal, selectedEndpoint should be null and checks empty
            val events = cancelAndConsumeRemainingEvents()
            val lastState = viewModel.state.value
            assertNull(lastState.selectedEndpoint)
            assertTrue(lastState.checks.isEmpty())
            assertTrue(lastState.endpoints.isEmpty())
        }
    }

    @Test
    fun `selectEndpoint updates selectedEndpoint`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.addEndpoint("Example", "https://example.com")
            val afterAdd = awaitItem()
            val endpoint = afterAdd.endpoints[0]

            viewModel.selectEndpoint(endpoint)
            val afterSelect = awaitItem()
            assertEquals(endpoint, afterSelect.selectedEndpoint)
        }
    }

    @Test
    fun `deselectEndpoint clears selectedEndpoint and checks`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.addEndpoint("Example", "https://example.com")
            val afterAdd = awaitItem()
            val endpoint = afterAdd.endpoints[0]

            viewModel.selectEndpoint(endpoint)
            awaitItem() // selected state

            viewModel.deselectEndpoint()
            val deselected = awaitItem()
            assertNull(deselected.selectedEndpoint)
            assertTrue(deselected.checks.isEmpty())
        }
    }

    @Test
    fun `checkNow performs health check and stores result in DAO`() = runTest {
        val checkResult = EndpointCheck(
            endpointId = 0,
            statusCode = 200,
            latencyMs = 42,
            isSuccess = true,
        )
        checker.result = checkResult

        viewModel.state.test {
            awaitItem() // initial

            viewModel.addEndpoint("Example", "https://example.com")
            val afterAdd = awaitItem()
            val endpoint = afterAdd.endpoints[0]

            viewModel.selectEndpoint(endpoint)
            awaitItem() // selected

            viewModel.checkNow(endpoint)

            // isChecking transitions: true then false
            // The check result should appear via the DAO flow collection
            val events = cancelAndConsumeRemainingEvents()
            val finalState = viewModel.state.value
            assertFalse(finalState.isChecking)
            assertNull(finalState.error)
            // The check was inserted into DAO with correct endpointId
            assertEquals(1, finalState.checks.size)
            assertEquals(endpoint.id, finalState.checks[0].endpointId)
            assertEquals(200, finalState.checks[0].statusCode)
        }
    }

    @Test
    fun `checkNow sets error on failure`() = runTest {
        checker.error = RuntimeException("Connection refused")

        viewModel.state.test {
            awaitItem() // initial

            viewModel.addEndpoint("Example", "https://example.com")
            val afterAdd = awaitItem()
            val endpoint = afterAdd.endpoints[0]

            viewModel.checkNow(endpoint)

            // Consume until we get the error state
            val events = cancelAndConsumeRemainingEvents()
            val finalState = viewModel.state.value
            assertEquals("Connection refused", finalState.error)
            assertFalse(finalState.isChecking)
        }
    }

    @Test
    fun `addEndpoint with invalid URL shows error`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.addEndpoint("Bad", "not-a-url")

            val errorState = awaitItem()
            assertEquals("Invalid URL format", errorState.error)
            assertTrue(errorState.endpoints.isEmpty())
        }
    }

    @Test
    fun `addEndpoint with ftp scheme shows error`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.addEndpoint("FTP", "ftp://example.com")

            val errorState = awaitItem()
            assertEquals("URL must use http or https scheme", errorState.error)
            assertTrue(errorState.endpoints.isEmpty())
        }
    }

    @Test
    fun `addEndpoint with localhost shows error`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.addEndpoint("Local", "http://localhost/test")

            val errorState = awaitItem()
            assertEquals("Private or loopback addresses are not allowed", errorState.error)
            assertTrue(errorState.endpoints.isEmpty())
        }
    }

    @Test
    fun `dismissError clears error`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.addEndpoint("Bad", "not-a-url")
            val errorState = awaitItem()
            assertEquals("Invalid URL format", errorState.error)

            viewModel.dismissError()
            val cleared = awaitItem()
            assertNull(cleared.error)
        }
    }
}
