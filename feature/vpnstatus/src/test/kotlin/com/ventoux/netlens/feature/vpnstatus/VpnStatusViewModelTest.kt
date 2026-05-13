package com.ventoux.netlens.feature.vpnstatus

import app.cash.turbine.test
import com.ventoux.netlens.core.network.NetworkMonitor
import com.ventoux.netlens.core.network.VpnState
import com.ventoux.netlens.feature.vpnstatus.model.VpnStatusUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnStatusViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var networkMonitor: FakeNetworkMonitor

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        networkMonitor = FakeNetworkMonitor()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = VpnStatusViewModel(networkMonitor = networkMonitor)

    @Test
    fun `initial state has isLoading true`() = runTest {
        val vm = createViewModel()
        vm.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits isLoading false after first collect`() = runTest {
        networkMonitor.online.value = true
        networkMonitor.vpn.value = VpnState.None
        val vm = createViewModel()
        vm.uiState.test {
            assertEquals(VpnStatusUiState(isLoading = true), awaitItem())
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `vpnState FullTunnel propagates to ui state`() = runTest {
        networkMonitor.online.value = true
        networkMonitor.vpn.value = VpnState.FullTunnel
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem() // initial loading
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertTrue(loaded.isOnline)
            assertEquals(VpnState.FullTunnel, loaded.vpnState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `vpnState SplitTunnel propagates to ui state`() = runTest {
        networkMonitor.online.value = true
        networkMonitor.vpn.value = VpnState.SplitTunnel
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem() // initial loading
            val loaded = awaitItem()
            assertEquals(VpnState.SplitTunnel, loaded.vpnState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isOnline false propagates to ui state`() = runTest {
        networkMonitor.online.value = false
        networkMonitor.vpn.value = VpnState.None
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem() // initial loading
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertFalse(loaded.isOnline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `vpnState change after initial emit propagates`() = runTest {
        networkMonitor.online.value = true
        networkMonitor.vpn.value = VpnState.None
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem() // initial loading
            val first = awaitItem()
            assertEquals(VpnState.None, first.vpnState)

            networkMonitor.vpn.value = VpnState.FullTunnel
            val updated = awaitItem()
            assertEquals(VpnState.FullTunnel, updated.vpnState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `online going false after start propagates`() = runTest {
        networkMonitor.online.value = true
        networkMonitor.vpn.value = VpnState.FullTunnel
        val vm = createViewModel()
        vm.uiState.test {
            awaitItem() // initial loading
            val first = awaitItem()
            assertTrue(first.isOnline)

            networkMonitor.online.value = false
            val updated = awaitItem()
            assertFalse(updated.isOnline)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeNetworkMonitor : NetworkMonitor {
    val online = MutableStateFlow(true)
    val vpn = MutableStateFlow<VpnState>(VpnState.None)
    override val isOnline: Flow<Boolean> = online
    override val vpnState: Flow<VpnState> = vpn
}
