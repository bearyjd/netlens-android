package com.ventouxlabs.netlens.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowScanResult
import org.robolectric.shadows.ShadowWifiInfo

/**
 * Covers [detectEncryptionType]'s **pre-S branch only** — which is live code, not legacy:
 * `minSdk` is 29, so API 29/30 devices take it.
 *
 * **The API 31+ branch is not testable here, and that is a hard limit rather than an omission.**
 * It reads `WifiInfo` out of `NetworkCapabilities.transportInfo`, and Robolectric's sandboxed
 * `WifiInfo` does not implement `TransportInfo` — `setTransportInfo(wifiInfo)` throws
 * `ClassCastException` (confirmed in #158 against `NetworkCollector`, and it is the same cast
 * here). So `transportInfo` can only ever be null in a Robolectric test, and every S+ assertion
 * would reduce to "returns null", which is true with or without the logic under it. Verifying
 * that branch needs an instrumentation test on an API 31+ device.
 *
 * The same trap applies to the transport guard: asserting it at the default SDK passes whether or
 * not the guard exists, because the S+ branch returns null anyway. It is asserted at SDK 29 with
 * a fully-populated WiFi state, so removing the guard turns the test red — mutation-confirmed.
 */
@RunWith(RobolectricTestRunner::class)
class DetectEncryptionTypeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun capabilities(transport: Int): NetworkCapabilities {
        val nc = ShadowNetworkCapabilities.newInstance()
        shadowOf(nc).addTransportType(transport)
        return nc
    }

    /** Associates the shadowed [WifiManager] with [bssid] and publishes [scanResults]. */
    private fun associate(bssid: String, scanResults: List<ScanResult>) {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = Shadow.newInstanceOf(WifiInfo::class.java)
        Shadow.extract<ShadowWifiInfo>(info).setBSSID(bssid)
        shadowOf(wm).setConnectionInfo(info)
        shadowOf(wm).setScanResults(scanResults)
    }

    @Suppress("DEPRECATION") // Robolectric's only ScanResult factory
    private fun scanResult(bssid: String, capabilities: String): ScanResult =
        ShadowScanResult.newInstance("ssid-$bssid", bssid, capabilities, -50, 2412)

    // The BSSID match is what makes this the *connected* network's encryption rather than
    // whichever AP happened to be scanned first — the decoy is listed ahead of the real one.
    @Test
    @Config(sdk = [29])
    fun `pre-S resolves the connected BSSID's capabilities string`() {
        associate(
            bssid = "aa:bb:cc:dd:ee:ff",
            scanResults = listOf(
                scanResult("11:22:33:44:55:66", "[WEP]"),
                scanResult("aa:bb:cc:dd:ee:ff", "[RSN-SAE-CCMP]"),
            ),
        )

        assertEquals("WPA3", detectEncryptionType(context, capabilities(NetworkCapabilities.TRANSPORT_WIFI)))
    }

    @Test
    @Config(sdk = [29])
    fun `pre-S yields null when no scan result matches the connected BSSID`() {
        associate(
            bssid = "aa:bb:cc:dd:ee:ff",
            scanResults = listOf(scanResult("11:22:33:44:55:66", "[WPA2-PSK-CCMP]")),
        )

        assertNull(detectEncryptionType(context, capabilities(NetworkCapabilities.TRANSPORT_WIFI)))
    }

    // The caps-not-passed overload: detectEncryptionType(context) must resolve the active
    // network's capabilities itself. Same WiFi state as the two-arg tests, so a broken fallback
    // (null caps short-circuiting to null) fails here and nowhere else.
    @Test
    @Config(sdk = [29])
    fun `omitting capabilities falls back to the active network's own`() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = requireNotNull(cm.activeNetwork)
        shadowOf(cm).setNetworkCapabilities(network, capabilities(NetworkCapabilities.TRANSPORT_WIFI))
        associate(
            bssid = "aa:bb:cc:dd:ee:ff",
            scanResults = listOf(scanResult("aa:bb:cc:dd:ee:ff", "[RSN-SAE-CCMP]")),
        )

        assertEquals("WPA3", detectEncryptionType(context))
    }

    // The guard one step before the fallback: no active network at all must mean null, even
    // though WifiManager still reports the last association (it does, long after disconnect —
    // same stale-data class as the transport guard). If the fallback ever consulted WifiManager
    // without the activeNetwork check, an airplane-mode device would report its last café's
    // encryption; this WiFi state would resolve to WPA3 if that guard leaked.
    @Test
    @Config(sdk = [29])
    fun `no active network yields null despite a live WifiManager association`() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(cm).clearAllNetworks()
        associate(
            bssid = "aa:bb:cc:dd:ee:ff",
            scanResults = listOf(scanResult("aa:bb:cc:dd:ee:ff", "[RSN-SAE-CCMP]")),
        )

        assertNull(detectEncryptionType(context))
    }

    // Stale-WiFi guard: WifiManager keeps reporting the last association after the device moves to
    // cellular, so the transport check — not the WifiManager state — decides. The WiFi state here
    // is deliberately valid and would resolve to WPA3 if the guard were dropped.
    @Test
    @Config(sdk = [29])
    fun `non-wifi transport yields null despite a live WifiManager association`() {
        associate(
            bssid = "aa:bb:cc:dd:ee:ff",
            scanResults = listOf(scanResult("aa:bb:cc:dd:ee:ff", "[RSN-SAE-CCMP]")),
        )

        assertNull(detectEncryptionType(context, capabilities(NetworkCapabilities.TRANSPORT_CELLULAR)))
    }
}
