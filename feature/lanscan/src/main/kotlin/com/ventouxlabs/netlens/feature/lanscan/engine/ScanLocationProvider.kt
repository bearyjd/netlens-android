package com.ventouxlabs.netlens.feature.lanscan.engine

import com.ventouxlabs.netlens.feature.lanscan.model.ScanCoordinates

/**
 * Reads the device's current position so a scan can be geo-tagged.
 *
 * Exists as an interface purely so it can be faked. The implementation touches
 * `LocationManager` and a runtime permission, and this repo has no Robolectric and no
 * instrumentation — without a seam here, nothing about "what happens when there is no fix"
 * could be tested at all. It previously lived as a private `Context` extension inside
 * `LanScanScreen.kt`, which was untestable by construction.
 */
interface ScanLocationProvider {
    /**
     * The most recent known position, or null when there is none — permission not granted,
     * location services off, or no provider has a cached fix yet (common indoors and after a
     * reboot).
     *
     * Never throws. Location is optional metadata on a scan and must never fail or block one.
     */
    suspend fun current(): ScanCoordinates?
}
