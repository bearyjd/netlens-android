package com.ventouxlabs.netlens.feature.wifi

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
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
import com.ventouxlabs.netlens.feature.wifi.engine.WifiScanner
import com.ventouxlabs.netlens.feature.wifi.model.ConnectedWifiInfo
import com.ventouxlabs.netlens.feature.wifi.model.WifiBand
import com.ventouxlabs.netlens.feature.wifi.model.WifiNetwork

class FakeWifiScanner : WifiScanner {

    var scanResults: List<WifiNetwork> = emptyList()
    var connectedInfo: ConnectedWifiInfo? = null
    val scanFlow = MutableSharedFlow<List<WifiNetwork>>()

    override fun scan(): Flow<List<WifiNetwork>> = scanFlow

    override fun observeConnected(): Flow<ConnectedWifiInfo?> = flowOf(connectedInfo)

    suspend fun emitScanResults() {
        scanFlow.emit(scanResults)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WifiViewModelTest {

    private lateinit var fakeScanner: FakeWifiScanner
    private lateinit var viewModel: WifiViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeScanner = FakeWifiScanner()
        viewModel = WifiViewModel(fakeScanner)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty networks`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(emptyList<WifiNetwork>(), state.networks)
            assertNull(state.connectedInfo)
            assertFalse(state.isScanning)
            assertNull(state.error)
            assertEquals(WifiBand.ALL, state.selectedBand)
            assertFalse(state.permissionGranted)
        }
    }

    @Test
    fun `onPermissionResult updates permission state`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.onPermissionResult(true)
            val state = awaitItem()
            assertTrue(state.permissionGranted)
            // onPermissionResult(true) also triggers startScan(), consume that event
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `scan does not start without permission`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.startScan()
            expectNoEvents()
        }
    }

    @Test
    fun `scan populates networks`() = runTest {
        val networks = listOf(
            testNetwork("Network1", "aa:bb:cc:dd:ee:01", 2437, -45),
            testNetwork("Network2", "aa:bb:cc:dd:ee:02", 5180, -65),
        )
        fakeScanner.scanResults = networks

        viewModel.onPermissionResult(true)

        viewModel.state.test {
            // We may get intermediate states; skip to find the scanning=true state
            var found = false
            while (!found) {
                val s = awaitItem()
                if (s.isScanning) {
                    found = true
                }
            }

            fakeScanner.emitScanResults()

            // Wait for the state with networks
            var finalState = awaitItem()
            while (finalState.networks.isEmpty()) {
                finalState = awaitItem()
            }
            assertEquals(2, finalState.networks.size)
            assertFalse(finalState.isScanning)
        }
    }

    @Test
    fun `onBandSelected updates selected band`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.onBandSelected(WifiBand.FIVE_GHZ)
            assertEquals(WifiBand.FIVE_GHZ, awaitItem().selectedBand)
        }
    }

    @Test
    fun `band filter works correctly`() = runTest {
        val networks = listOf(
            testNetwork("Net2G", "aa:bb:cc:dd:ee:01", 2437, -45),
            testNetwork("Net5G", "aa:bb:cc:dd:ee:02", 5180, -65),
        )
        fakeScanner.scanResults = networks
        viewModel.onPermissionResult(true)
        fakeScanner.emitScanResults()

        // Wait for networks to populate
        viewModel.state.test {
            var s = awaitItem()
            while (s.networks.isEmpty()) {
                s = awaitItem()
            }
        }

        viewModel.onBandSelected(WifiBand.TWO_GHZ)
        val filtered = viewModel.filteredNetworks()
        assertEquals(1, filtered.size)
        assertEquals("Net2G", filtered[0].ssid)

        viewModel.onBandSelected(WifiBand.FIVE_GHZ)
        val filtered5 = viewModel.filteredNetworks()
        assertEquals(1, filtered5.size)
        assertEquals("Net5G", filtered5[0].ssid)

        viewModel.onBandSelected(WifiBand.ALL)
        val filteredAll = viewModel.filteredNetworks()
        assertEquals(2, filteredAll.size)
    }

    @Test
    fun `buildExportText formats correctly`() = runTest {
        val networks = listOf(
            testNetwork("MyWifi", "aa:bb:cc:dd:ee:01", 2437, -45),
            testNetwork("Office5G", "aa:bb:cc:dd:ee:02", 5180, -65),
        )
        fakeScanner.scanResults = networks
        fakeScanner.connectedInfo = ConnectedWifiInfo(
            ssid = "MyWifi",
            bssid = "aa:bb:cc:dd:ee:01",
            linkSpeedMbps = 144,
            frequency = 2437,
            rssi = -45,
            ipAddress = "192.168.1.100",
        )
        viewModel.onPermissionResult(true)
        fakeScanner.emitScanResults()

        viewModel.state.test {
            var s = awaitItem()
            while (s.networks.isEmpty()) {
                s = awaitItem()
            }
        }

        val text = viewModel.buildExportText()
        assertTrue(text.contains("WiFi Analyzer Results:"))
        assertTrue(text.contains("Connected: MyWifi (-45dBm, 144Mbps)"))
        assertTrue(text.contains("Networks found: 2"))
        assertTrue(text.contains("--- 2.4 GHz ---"))
        assertTrue(text.contains("MyWifi  Ch 6  -45dBm  WPA2"))
        assertTrue(text.contains("--- 5 GHz ---"))
        assertTrue(text.contains("Office5G  Ch 36  -65dBm  WPA2"))
    }

    @Test
    fun `buildExportText with no results shows zero count`() {
        val text = viewModel.buildExportText()
        assertTrue(text.contains("Networks found: 0"))
    }

    private fun testNetwork(
        ssid: String,
        bssid: String,
        frequency: Int,
        level: Int,
    ): WifiNetwork {
        val channel = com.ventouxlabs.netlens.feature.wifi.engine.ChannelCalculator.frequencyToChannel(frequency)
        return WifiNetwork(
            ssid = ssid,
            bssid = bssid,
            frequency = frequency,
            channelNumber = channel,
            channelWidth = 20,
            level = level,
            security = "WPA2",
            capabilities = "[WPA2-PSK]",
        )
    }
}
