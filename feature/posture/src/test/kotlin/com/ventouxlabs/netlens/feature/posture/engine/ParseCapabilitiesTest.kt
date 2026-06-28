package com.ventouxlabs.netlens.feature.posture.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParseCapabilitiesTest {

    @Test
    fun `WPA3-SAE capabilities returns WPA3`() {
        assertEquals("WPA3", parseCapabilities("[RSN-SAE-CCMP][ESS]"))
    }

    @Test
    fun `explicit WPA3 in capabilities returns WPA3`() {
        assertEquals("WPA3", parseCapabilities("[WPA3-PSK-CCMP][ESS]"))
    }

    @Test
    fun `mixed SAE and RSN capabilities returns WPA3`() {
        assertEquals("WPA3", parseCapabilities("[WPA2-PSK-CCMP][RSN-SAE+PSK-CCMP][ESS]"))
    }

    @Test
    fun `OWE capabilities returns OWE`() {
        assertEquals("OWE", parseCapabilities("[RSN-OWE-CCMP][ESS]"))
    }

    @Test
    fun `WPA2-PSK capabilities returns WPA2`() {
        assertEquals("WPA2", parseCapabilities("[WPA2-PSK-CCMP+TKIP][ESS]"))
    }

    @Test
    fun `RSN without SAE returns WPA2`() {
        assertEquals("WPA2", parseCapabilities("[RSN-PSK-CCMP][ESS]"))
    }

    @Test
    fun `WPA only capabilities returns WPA`() {
        assertEquals("WPA", parseCapabilities("[WPA-PSK-TKIP][ESS]"))
    }

    @Test
    fun `WEP capabilities returns WEP`() {
        assertEquals("WEP", parseCapabilities("[WEP][ESS]"))
    }

    @Test
    fun `open network returns Open`() {
        assertEquals("Open", parseCapabilities("[ESS]"))
    }

    @Test
    fun `empty string returns Open`() {
        assertEquals("Open", parseCapabilities(""))
    }

    @Test
    fun `real world complex capabilities string`() {
        assertEquals("WPA2", parseCapabilities("[WPA2-PSK-CCMP+TKIP][RSN-PSK-CCMP+TKIP][ESS][WPS]"))
    }

    @Test
    fun `WPA3 takes priority over WPA2 in mixed mode`() {
        assertEquals("WPA3", parseCapabilities("[WPA2-PSK-CCMP][SAE-CCMP][ESS]"))
    }
}
