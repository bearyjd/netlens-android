package com.ventouxlabs.netlens.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [parseCapabilities] is pure and already `internal` (for [detectEncryptionType]'s pre-API-31
 * fallback) but had zero tests despite needing no Robolectric at all.
 */
class WidgetRefreshWorkerParseCapabilitiesTest {

    @Test
    fun `WPA3 or SAE resolves to WPA3`() {
        assertEquals("WPA3", parseCapabilities("[WPA3-PSK-CCMP][ESS]"))
        assertEquals("WPA3", parseCapabilities("[RSN-SAE-CCMP][ESS]"))
    }

    @Test
    fun `OWE resolves to OWE`() {
        assertEquals("OWE", parseCapabilities("[RSN-OWE-CCMP][ESS]"))
    }

    @Test
    fun `WPA2 or RSN resolves to WPA2`() {
        assertEquals("WPA2", parseCapabilities("[WPA2-PSK-CCMP][ESS]"))
        assertEquals("WPA2", parseCapabilities("[RSN-PSK-CCMP][ESS]"))
    }

    @Test
    fun `WPA resolves to WPA`() {
        assertEquals("WPA", parseCapabilities("[WPA-PSK-TKIP][ESS]"))
    }

    @Test
    fun `WEP resolves to WEP`() {
        assertEquals("WEP", parseCapabilities("[WEP][ESS]"))
    }

    @Test
    fun `no recognized token resolves to Open`() {
        assertEquals("Open", parseCapabilities("[ESS]"))
    }

    // Branch order matters: WPA3-PSK also contains "WPA", and RSN-PSK also matches the WPA2
    // branch's "RSN" check — confirms the earlier, more specific branches win.
    @Test
    fun `more specific tokens take precedence over broader ones`() {
        assertEquals("WPA3", parseCapabilities("[WPA3-PSK-CCMP][WPA2-PSK-CCMP][ESS]"))
    }
}
