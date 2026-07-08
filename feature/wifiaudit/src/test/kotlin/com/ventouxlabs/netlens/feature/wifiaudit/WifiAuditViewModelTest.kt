package com.ventouxlabs.netlens.feature.wifiaudit

import app.cash.turbine.test
import com.ventouxlabs.netlens.core.data.model.NetworkEventType
import com.ventouxlabs.netlens.feature.wifiaudit.engine.FakeWifiInfoReader
import com.ventouxlabs.netlens.feature.wifiaudit.model.ConnectedNetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WifiAuditViewModelTest {

    private lateinit var fakeReader: FakeWifiInfoReader
    private lateinit var fakeDao: FakeNetworkEventDao

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeReader = FakeWifiInfoReader()
        fakeDao = FakeNetworkEventDao()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init with no connection reports a not-connected error`() = runTest {
        fakeReader.connectedResult = null
        val viewModel = WifiAuditViewModel(fakeReader, fakeDao)

        viewModel.state.test {
            var s = awaitItem()
            while (s.isAuditing) s = awaitItem()
            assertNotNull(s.error)
            assertTrue(s.findings.isEmpty())
        }
        assertTrue(fakeDao.inserted.isEmpty())
    }

    @Test
    fun `runAudit with a connected network populates findings and logs an event`() = runTest {
        fakeReader.connectedResult = testNetwork()
        val viewModel = WifiAuditViewModel(fakeReader, fakeDao)

        viewModel.state.test {
            var s = awaitItem()
            while (s.isAuditing) s = awaitItem()
            assertEquals("HomeWifi", s.ssid)
            assertTrue(s.findings.isNotEmpty())
            assertNull(s.error)
        }
        assertEquals(1, fakeDao.inserted.size)
        assertEquals(NetworkEventType.SECURITY_AUDIT, fakeDao.inserted[0].eventType)
    }

    @Test
    fun `runAudit when the reader throws surfaces an error`() = runTest {
        fakeReader.error = RuntimeException("permission denied")
        val viewModel = WifiAuditViewModel(fakeReader, fakeDao)

        viewModel.state.test {
            var s = awaitItem()
            while (s.isAuditing) s = awaitItem()
            assertNotNull(s.error)
            assertTrue(s.findings.isEmpty())
        }
    }

    @Test
    fun `dismissFinding adds the id to dismissedIds`() = runTest {
        fakeReader.connectedResult = testNetwork()
        val viewModel = WifiAuditViewModel(fakeReader, fakeDao)

        viewModel.state.test {
            var s = awaitItem()
            while (s.isAuditing) s = awaitItem()

            val findingId = s.findings.first().id
            viewModel.dismissFinding(findingId)
            val updated = awaitItem()
            assertTrue(updated.dismissedIds.contains(findingId))
        }
    }

    @Test
    fun `clearError resets the error state`() = runTest {
        fakeReader.connectedResult = null
        val viewModel = WifiAuditViewModel(fakeReader, fakeDao)

        viewModel.state.test {
            var s = awaitItem()
            while (s.isAuditing) s = awaitItem()
            assertNotNull(s.error)

            viewModel.clearError()
            val cleared = awaitItem()
            assertNull(cleared.error)
        }
    }

    @Test
    fun `runAudit called again clears prior findings and dismissals before re-auditing`() = runTest {
        fakeReader.connectedResult = testNetwork()
        val viewModel = WifiAuditViewModel(fakeReader, fakeDao)

        viewModel.state.test {
            var s = awaitItem()
            while (s.isAuditing) s = awaitItem()
            val findingId = s.findings.first().id
            viewModel.dismissFinding(findingId)
            awaitItem()

            viewModel.runAudit()
            var refreshing = awaitItem()
            assertTrue(refreshing.isAuditing)
            assertTrue(refreshing.dismissedIds.isEmpty())

            var done = awaitItem()
            while (done.isAuditing) done = awaitItem()
            assertTrue(done.findings.isNotEmpty())
        }
    }

    private fun testNetwork() = ConnectedNetworkInfo(
        ssid = "HomeWifi",
        bssid = "aa:bb:cc:dd:ee:ff",
        rssi = -50,
        frequency = 5180,
        security = "WPA2",
        capabilities = "[WPA2-PSK-CCMP][ESS]",
        linkSpeedMbps = 300,
        ipAddress = "192.168.1.50",
    )
}
