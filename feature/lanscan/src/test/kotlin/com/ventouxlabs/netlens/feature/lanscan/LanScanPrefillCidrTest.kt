package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.scan.engine.FakeArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.FakeLanNetworkBinder
import com.ventouxlabs.netlens.core.scan.engine.FakePortScanner
import com.ventouxlabs.netlens.core.scan.network.FakeNetworkIdentity
import com.ventouxlabs.netlens.core.scan.testing.InventoryFakes
import com.ventouxlabs.netlens.feature.lanscan.engine.FakeScanLocationProvider
import com.ventouxlabs.netlens.feature.lanscan.model.ScanRangeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A CIDR arriving through the exported `netlens://feature/scan?...` deep link must only prefill
 * the range field. Sweeping an arbitrary range on another app's say-so needs the user's tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanScanPrefillCidrTest {

    private val binder = FakeLanNetworkBinder()
    private lateinit var viewModel: LanScanViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = LanScanViewModel(
            discoveryPipeline = enginePipeline(arpTableReader = FakeArpTableReader()),
            arpTableReader = FakeArpTableReader(),
            fingerprinter = FakeDeviceFingerprinter(),
            portScanner = FakePortScanner(),
            lanNetworkBinder = binder,
            networkInterfaceProvider = FakeNetworkInterfaceProvider(),
            lanScanHistoryDao = FakeLanScanHistoryDao(),
            knownDeviceDao = FakeKnownDeviceDao(),
            deviceInventoryRepository = InventoryFakes().repository(FakeNewDeviceNotifier(), autoMerge = false),
            scanLocationProvider = FakeScanLocationProvider(),
            networkIdentity = FakeNetworkIdentity(),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `prefilling a valid cidr fills the field without starting a scan`() = runTest {
        viewModel.prefillCidr("10.1.2.0/24")

        val state = viewModel.uiState.value
        assertEquals(ScanRangeMode.CUSTOM, state.rangeMode)
        assertEquals("10.1.2.0/24", state.customRange)
        assertFalse(state.isScanning)
        assertEquals(0, binder.bindCount)
    }

    @Test
    fun `prefilling an invalid cidr leaves the field untouched`() = runTest {
        val before = viewModel.uiState.value.customRange
        viewModel.prefillCidr("10.0.0.0/8")

        assertEquals(before, viewModel.uiState.value.customRange)
        assertEquals(0, binder.bindCount)
    }
}
