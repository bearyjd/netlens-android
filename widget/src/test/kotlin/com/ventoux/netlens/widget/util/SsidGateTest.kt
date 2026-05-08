package com.ventoux.netlens.widget.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SsidGateTest {

    @Test
    fun `returns ssid when on wifi`() {
        assertEquals("Home", gateSsidForTransport(isWifiTransport = true, rawSsid = "Home"))
    }

    @Test
    fun `clears stale ssid when on cellular`() {
        // Regression: WifiManager.connectionInfo caches the last associated SSID
        // after switching to cellular. The widget must drop that cached value so
        // the network status surface does not display a network the device is not
        // actually on.
        assertNull(gateSsidForTransport(isWifiTransport = false, rawSsid = "Home"))
    }

    @Test
    fun `null when on wifi but ssid not yet known`() {
        assertNull(gateSsidForTransport(isWifiTransport = true, rawSsid = null))
    }

    @Test
    fun `null when offline`() {
        assertNull(gateSsidForTransport(isWifiTransport = false, rawSsid = null))
    }
}
