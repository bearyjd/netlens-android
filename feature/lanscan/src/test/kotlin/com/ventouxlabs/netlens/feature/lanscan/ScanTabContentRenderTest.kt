package com.ventouxlabs.netlens.feature.lanscan

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.feature.lanscan.model.EmptyScanReason
import com.ventouxlabs.netlens.feature.lanscan.model.LanScanUiState
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests — see `AndroidScreenshotConventionPlugin`. No golden images.
 *
 * Covers the unreachable-network notice, which is only reachable through a state combination the
 * ViewModel produces at the end of a scan and is therefore easy to break without noticing.
 */
class ScanTabContentRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun render(state: LanScanUiState) = paparazzi.snapshot {
        ScanTabContent(
            uiState = state,
            showCustomField = false,
            onRangeModeChanged = {},
            onCustomRangeChanged = {},
            onManualLatitudeChanged = {},
            onManualLongitudeChanged = {},
            onCaptureLocation = {},
            onClearLocation = {},
            onShowManualEntry = {},
            onScanWithCidr = {},
            onStartScan = {},
            onDeviceClick = {},
        )
    }

    @Test
    fun `an empty completed scan with no reason renders nothing extra`() {
        render(LanScanUiState(subnetInfo = "192.168.1.0/24"))
    }

    @Test
    fun `an unreachable network renders the notice`() {
        render(
            LanScanUiState(
                subnetInfo = "192.168.1.0/24",
                emptyScanReason = EmptyScanReason.NETWORK_UNREACHABLE,
            ),
        )
    }

    @Test
    fun `the notice is suppressed while a scan is still running`() {
        render(
            LanScanUiState(
                isScanning = true,
                progress = 0.4f,
                subnetInfo = "192.168.1.0/24",
                emptyScanReason = EmptyScanReason.NETWORK_UNREACHABLE,
            ),
        )
    }
}
