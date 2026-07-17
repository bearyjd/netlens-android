package com.ventouxlabs.netlens.feature.devices

import app.cash.turbine.test
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {

    private lateinit var knownDao: FakeKnownDeviceDao
    private lateinit var watchedDao: FakeWatchedNetworkDao
    private lateinit var identity: FakeNetworkIdentity
    private lateinit var viewModel: DevicesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        knownDao = FakeKnownDeviceDao()
        watchedDao = FakeWatchedNetworkDao()
        identity = FakeNetworkIdentity()
        viewModel = DevicesViewModel(knownDao, watchedDao, identity)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `search filters by display name`() = runTest {
        knownDao.seed(KnownDeviceEntity(macAddress = "M1", hostname = "printer", ip = "192.168.1.2", vendor = null))
        knownDao.seed(KnownDeviceEntity(macAddress = "M2", hostname = "laptop", ip = "192.168.1.3", vendor = null))
        viewModel.setSearchQuery("print")
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(1, state.devices.size)
            assertEquals("printer", state.devices.first().hostname)
        }
    }

    @Test
    fun `rename persists a trimmed custom name`() = runTest {
        knownDao.seed(KnownDeviceEntity(id = 5, macAddress = "M1", hostname = "h", ip = "192.168.1.2", vendor = null))
        viewModel.rename(5, "  Living Room TV  ")
        assertEquals("Living Room TV", knownDao.byId(5)?.customName)
    }

    @Test
    fun `rename with blank clears the custom name`() = runTest {
        knownDao.seed(KnownDeviceEntity(id = 5, macAddress = "M1", hostname = "h", ip = "192.168.1.2", vendor = null, customName = "Old"))
        viewModel.rename(5, "   ")
        assertNull(knownDao.byId(5)?.customName)
    }

    @Test
    fun `toggleKnown flips the flag`() = runTest {
        knownDao.seed(KnownDeviceEntity(id = 7, macAddress = "M1", hostname = "h", ip = "192.168.1.2", vendor = null, isKnown = false))
        viewModel.toggleKnown(7)
        assertTrue(knownDao.byId(7)?.isKnown == true)
    }

    @Test
    fun `watchCurrentNetwork captures gateway identity into a watched network`() = runTest {
        identity.gatewayMac = "AA:BB:CC:DD:EE:FF"
        identity.subnet = "192.168.1.0/24"
        identity.ssid = "HomeWiFi"
        viewModel.watchCurrentNetwork()
        val watched = watchedDao.getByGatewayMac("AA:BB:CC:DD:EE:FF")
        assertEquals("HomeWiFi", watched?.displayName)
        assertEquals("192.168.1.0/24", watched?.subnet)
    }

    @Test
    fun `watchCurrentNetwork is a no-op when gateway is unresolvable`() = runTest {
        identity.gatewayMac = null
        viewModel.watchCurrentNetwork()
        assertTrue(watchedDao.networks.isEmpty())
    }
}
