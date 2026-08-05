package com.ventouxlabs.netlens.feature.lanscan

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.feature.lanscan.model.LanScanUiState
import com.ventouxlabs.netlens.feature.lanscan.model.LocationStatus
import com.ventouxlabs.netlens.feature.lanscan.model.ScanCoordinates
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests — see `AndroidScreenshotConventionPlugin`. No golden images.
 *
 * This section has four branches and a conditional manual-entry row, and none of the rest of
 * `LanScanContent` is render-tested, so without this the whole location UI would be verified
 * only by "it compiles". Every state is rendered, including the two that are easy to forget:
 * FOUND with a null `capturedLocation` (contradictory but representable), and UNAVAILABLE with
 * the manual fields expanded.
 */
class ScanLocationSectionRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun render(state: LanScanUiState) = paparazzi.snapshot {
        ScanLocationSection(
            uiState = state,
            onCaptureLocation = {},
            onClearLocation = {},
            onShowManualEntry = {},
            onManualLatitudeChanged = {},
            onManualLongitudeChanged = {},
        )
    }

    @Test
    fun `idle offers the capture button`() {
        render(LanScanUiState(locationStatus = LocationStatus.IDLE))
    }

    @Test
    fun `capturing renders a disabled button`() {
        render(LanScanUiState(locationStatus = LocationStatus.CAPTURING))
    }

    @Test
    fun `a found fix renders its coordinates`() {
        render(
            LanScanUiState(
                locationStatus = LocationStatus.FOUND,
                capturedLocation = ScanCoordinates(52.36760, 4.90410),
            ),
        )
    }

    @Test
    fun `a found fix with negative coordinates renders`() {
        // Southern and western hemispheres: the minus sign is part of the formatted width.
        render(
            LanScanUiState(
                locationStatus = LocationStatus.FOUND,
                capturedLocation = ScanCoordinates(-33.86880, -151.20930),
            ),
        )
    }

    @Test
    fun `FOUND with no coordinates does not crash`() {
        // The state class allows it even though the ViewModel never produces it; rendering a
        // null fix must not throw.
        render(LanScanUiState(locationStatus = LocationStatus.FOUND, capturedLocation = null))
    }

    @Test
    fun `unavailable offers manual entry`() {
        render(LanScanUiState(locationStatus = LocationStatus.UNAVAILABLE))
    }

    @Test
    fun `unavailable with manual entry expanded renders both fields`() {
        render(
            LanScanUiState(
                locationStatus = LocationStatus.UNAVAILABLE,
                showManualLocationEntry = true,
                manualLatitude = "52.3676",
                manualLongitude = "4.9041",
            ),
        )
    }
}
