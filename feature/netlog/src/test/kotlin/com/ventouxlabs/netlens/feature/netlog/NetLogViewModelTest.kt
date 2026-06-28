package com.ventouxlabs.netlens.feature.netlog

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
import com.ventouxlabs.netlens.core.data.model.NetworkEvent
import com.ventouxlabs.netlens.feature.netlog.dao.FakeNetworkEventDao
import com.ventouxlabs.netlens.feature.netlog.engine.FakeNetworkMonitor
import com.ventouxlabs.netlens.feature.netlog.model.NetLogUiState

@OptIn(ExperimentalCoroutinesApi::class)
class NetLogViewModelTest {

    private lateinit var dao: FakeNetworkEventDao
    private lateinit var monitor: FakeNetworkMonitor
    private lateinit var viewModel: NetLogViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dao = FakeNetworkEventDao()
        monitor = FakeNetworkMonitor()
        viewModel = NetLogViewModel(monitor, dao)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has defaults and empty events`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(emptyList<NetworkEvent>(), state.events)
            assertFalse(state.isMonitoring)
            assertFalse(state.showClearConfirmation)
            assertNull(state.error)
        }
    }

    @Test
    fun `init block loads events from DAO`() = runTest {
        // Pre-populate DAO before creating ViewModel
        val event = NetworkEvent(
            id = 0,
            timestamp = 1000L,
            eventType = "CONNECTED",
            transportType = "WIFI",
            networkDetails = "SSID=TestNet",
            isVpn = false,
        )
        dao.insert(event)

        // Create new ViewModel that will collect from pre-populated DAO
        val freshViewModel = NetLogViewModel(monitor, dao)

        freshViewModel.state.test {
            val state = awaitItem()
            assertEquals(1, state.events.size)
            assertEquals("CONNECTED", state.events[0].eventType)
        }
    }

    @Test
    fun `startMonitoring sets isMonitoring to true`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.startMonitoring()

            val monitoring = awaitItem()
            assertTrue(monitoring.isMonitoring)
        }
    }

    @Test
    fun `startMonitoring inserts emitted events into DAO`() = runTest {
        val event = NetworkEvent(
            id = 0,
            timestamp = 2000L,
            eventType = "LOST",
            transportType = "CELLULAR",
            networkDetails = "carrier=Test",
            isVpn = false,
        )

        viewModel.state.test {
            awaitItem() // initial

            viewModel.startMonitoring()
            val monitoring = awaitItem()
            assertTrue(monitoring.isMonitoring)

            monitor.channel.send(event)

            val withEvent = awaitItem()
            assertEquals(1, withEvent.events.size)
            assertEquals("LOST", withEvent.events[0].eventType)
        }
    }

    @Test
    fun `startMonitoring is idempotent when already monitoring`() = runTest {
        viewModel.startMonitoring()

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.isMonitoring)

            // Calling again should not change state or create a second job
            viewModel.startMonitoring()

            // No new emission expected since it returns early
            expectNoEvents()
        }
    }

    @Test
    fun `stopMonitoring clears isMonitoring`() = runTest {
        viewModel.startMonitoring()

        viewModel.state.test {
            val monitoring = awaitItem()
            assertTrue(monitoring.isMonitoring)

            viewModel.stopMonitoring()

            val stopped = awaitItem()
            assertFalse(stopped.isMonitoring)
        }
    }

    @Test
    fun `showClearConfirmation sets flag to true`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.showClearConfirmation()

            val updated = awaitItem()
            assertTrue(updated.showClearConfirmation)
        }
    }

    @Test
    fun `hideClearConfirmation sets flag to false`() = runTest {
        viewModel.showClearConfirmation()

        viewModel.state.test {
            val current = awaitItem()
            assertTrue(current.showClearConfirmation)

            viewModel.hideClearConfirmation()

            val hidden = awaitItem()
            assertFalse(hidden.showClearConfirmation)
        }
    }

    @Test
    fun `clearHistory deletes all events from DAO and hides confirmation`() = runTest {
        // Insert some events first
        dao.insert(
            NetworkEvent(
                timestamp = 1000L,
                eventType = "CONNECTED",
                transportType = "WIFI",
                networkDetails = "test",
            ),
        )
        dao.insert(
            NetworkEvent(
                timestamp = 2000L,
                eventType = "LOST",
                transportType = "CELLULAR",
                networkDetails = "test2",
            ),
        )

        // Create fresh ViewModel to pick up the pre-populated events
        val freshViewModel = NetLogViewModel(monitor, dao)

        freshViewModel.state.test {
            val initial = awaitItem()
            assertEquals(2, initial.events.size)

            freshViewModel.showClearConfirmation()
            awaitItem() // showClearConfirmation = true

            freshViewModel.clearHistory()

            // clearHistory calls hideClearConfirmation and deleteAll
            // We should end up with empty events and showClearConfirmation = false
            val events = cancelAndConsumeRemainingEvents()
            val finalState = freshViewModel.state.value
            assertTrue(finalState.events.isEmpty())
            assertFalse(finalState.showClearConfirmation)
        }
    }

    @Test
    fun `clearError clears error`() = runTest {
        viewModel.startMonitoring()

        viewModel.state.test {
            val monitoring = awaitItem()
            assertTrue(monitoring.isMonitoring)

            monitor.channel.close(RuntimeException("Monitoring error"))

            val errorState = awaitItem()
            assertEquals("Monitoring error", errorState.error)
            assertFalse(errorState.isMonitoring)

            viewModel.clearError()

            val cleared = awaitItem()
            assertNull(cleared.error)
        }
    }
}
