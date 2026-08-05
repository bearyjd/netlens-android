package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepositoryImpl
import com.ventouxlabs.netlens.core.scan.engine.FakeArpTableReader

import com.ventouxlabs.netlens.core.scan.engine.FakeLanMdnsScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeNetBiosProber
import com.ventouxlabs.netlens.core.scan.engine.FakePortScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeSsdpScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeSubnetScanner
import com.ventouxlabs.netlens.feature.lanscan.engine.FakeScanLocationProvider
import com.ventouxlabs.netlens.feature.lanscan.model.LocationStatus
import com.ventouxlabs.netlens.feature.lanscan.model.ScanCoordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Location tagging for a scan.
 *
 * The behaviour that matters is what happens when there is **no** fix — the common case indoors,
 * after a reboot, with permission denied, or with location services off. None of it could be
 * tested before: the read lived as a private `Context` extension inside `LanScanScreen.kt`, and
 * this repo has no Robolectric or instrumentation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanScanLocationTest {

    private lateinit var locationProvider: FakeScanLocationProvider
    private lateinit var viewModel: LanScanViewModel

    private val amsterdam = ScanCoordinates(52.36760, 4.90410)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        locationProvider = FakeScanLocationProvider()
        viewModel = LanScanViewModel(
            subnetScanner = FakeSubnetScanner(),
            mdnsScanner = FakeLanMdnsScanner(),
            fingerprinter = FakeDeviceFingerprinter(),
            portScanner = FakePortScanner(),
            ssdpScanner = FakeSsdpScanner(),
            netBiosProber = FakeNetBiosProber(),
            arpTableReader = FakeArpTableReader(),
            networkInterfaceProvider = FakeNetworkInterfaceProvider(),
            lanScanHistoryDao = FakeLanScanHistoryDao(),
            knownDeviceDao = FakeKnownDeviceDao(),
            deviceInventoryRepository = DeviceInventoryRepositoryImpl(
                FakeKnownDeviceDao(),
                FakeNewDeviceNotifier(),
            ),
            scanLocationProvider = locationProvider,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `capture stores the fix and reports FOUND`() = runTest {
        locationProvider.result = amsterdam

        viewModel.captureLocation()

        assertEquals(LocationStatus.FOUND, viewModel.uiState.value.locationStatus)
        assertEquals(amsterdam, viewModel.uiState.value.capturedLocation)
    }

    @Test
    fun `no fix reports UNAVAILABLE rather than an error`() = runTest {
        locationProvider.result = null

        viewModel.captureLocation()

        assertEquals(LocationStatus.UNAVAILABLE, viewModel.uiState.value.locationStatus)
        assertNull(viewModel.uiState.value.capturedLocation)
        // Not an error: `error` is the scan's failure channel and must stay clean, or a missing
        // fix would look like a failed scan.
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a throwing provider degrades to UNAVAILABLE instead of escaping the scope`() = runTest {
        // The interface says it never throws. If an implementation breaks that contract, an
        // optional feature must not take down the ViewModel.
        locationProvider.error = SecurityException("permission revoked mid-read")

        viewModel.captureLocation()

        assertEquals(LocationStatus.UNAVAILABLE, viewModel.uiState.value.locationStatus)
        assertNull(viewModel.uiState.value.capturedLocation)
    }

    @Test
    fun `clear returns to IDLE`() = runTest {
        locationProvider.result = amsterdam
        viewModel.captureLocation()

        viewModel.clearCapturedLocation()

        assertEquals(LocationStatus.IDLE, viewModel.uiState.value.locationStatus)
        assertNull(viewModel.uiState.value.capturedLocation)
    }

    @Test
    fun `a captured fix beats manual entry`() = runTest {
        viewModel.onManualLatitudeChanged("10.0")
        viewModel.onManualLongitudeChanged("20.0")
        locationProvider.result = amsterdam
        viewModel.captureLocation()

        assertEquals(amsterdam, viewModel.resolveScanCoordinates())
    }

    @Test
    fun `manual entry is used when nothing was captured`() = runTest {
        viewModel.onManualLatitudeChanged("10.5")
        viewModel.onManualLongitudeChanged("-20.25")

        assertEquals(ScanCoordinates(10.5, -20.25), viewModel.resolveScanCoordinates())
    }

    @Test
    fun `out-of-range manual entry contributes nothing`() = runTest {
        // Tagging a scan with a bad position is worse than tagging it with none.
        viewModel.onManualLatitudeChanged("91.0")
        viewModel.onManualLongitudeChanged("20.0")

        assertNull(viewModel.resolveScanCoordinates())
    }

    @Test
    fun `a half-typed manual entry contributes nothing`() = runTest {
        viewModel.onManualLatitudeChanged("52.3")

        assertNull(viewModel.resolveScanCoordinates())
    }

    @Test
    fun `no location at all resolves to null`() = runTest {
        assertNull(viewModel.resolveScanCoordinates())
    }

    @Test
    fun `capture is ignored while one is already in flight`() = runTest {
        locationProvider.result = amsterdam
        viewModel.captureLocation()
        val afterFirst = locationProvider.callCount

        viewModel.captureLocation()

        // The guard is on CAPTURING; once FOUND, a second capture is a legitimate refresh.
        assertEquals(afterFirst + 1, locationProvider.callCount)
    }
}
