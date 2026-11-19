package com.ventouxlabs.netlens.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WifiSecurityTest {

    @Test
    fun `SAE or WPA3 resolves to WPA3`() {
        assertEquals("WPA3", parseWifiCapabilities("[RSN-SAE-CCMP][ESS]"))
        assertEquals("WPA3", parseWifiCapabilities("[WPA3-PSK-CCMP][ESS]"))
        assertEquals("WPA3", parseWifiCapabilities("[WPA3-SAE]"))
    }

    @Test
    fun `PSK plus SAE is transition mode not WPA3`() {
        assertEquals("WPA2/WPA3", parseWifiCapabilities("[WPA2-PSK-CCMP][RSN-SAE+PSK-CCMP][ESS]"))
        assertEquals("WPA2/WPA3", parseWifiCapabilities("[WPA2-PSK-CCMP][SAE-CCMP][ESS]"))
    }

    @Test
    fun `WPA2 and RSN resolve to WPA2`() {
        assertEquals("WPA2", parseWifiCapabilities("[WPA2-PSK-CCMP+TKIP][ESS]"))
        assertEquals("WPA2", parseWifiCapabilities("[RSN-PSK-CCMP][ESS]"))
    }

    @Test
    fun `legacy WPA WEP OWE and open`() {
        assertEquals("WPA", parseWifiCapabilities("[WPA-PSK-TKIP][ESS]"))
        assertEquals("WEP", parseWifiCapabilities("[WEP][ESS]"))
        assertEquals("OWE", parseWifiCapabilities("[RSN-OWE-CCMP][ESS]"))
        assertEquals("Open", parseWifiCapabilities("[ESS]"))
        assertEquals("Open", parseWifiCapabilities(""))
    }

    @Test
    fun `WPA2 EAP is WPA2 enterprise`() {
        assertEquals("WPA2-Enterprise", parseWifiCapabilities("[WPA2-EAP/SHA1-CCMP][RSN-EAP/SHA1-CCMP][ESS]"))
        assertEquals("WPA2-Enterprise", parseWifiCapabilities("[RSN-EAP-CCMP][ESS]"))
    }

    @Test
    fun `WPA1 EAP is not labelled WPA2 enterprise`() {
        assertEquals("WPA-Enterprise", parseWifiCapabilities("[WPA-EAP-TKIP][ESS]"))
    }

    @Test
    fun `mixed WPA1 and WPA2 EAP is WPA2 enterprise`() {
        assertEquals("WPA2-Enterprise", parseWifiCapabilities("[WPA-EAP-TKIP][RSN-EAP-CCMP][ESS]"))
    }

    @Test
    fun `Suite B is WPA3 enterprise`() {
        assertEquals("WPA3-Enterprise", parseWifiCapabilities("[RSN-EAP_SUITE_B_192-GCMP256][ESS]"))
    }

    @Test
    fun `security type ints map to labels including 192-bit enterprise`() {
        assertEquals("Open", wifiSecurityTypeLabel(0))
        assertEquals("WEP", wifiSecurityTypeLabel(1))
        assertEquals("WPA2", wifiSecurityTypeLabel(2))
        assertEquals("WPA2-Enterprise", wifiSecurityTypeLabel(3))
        assertEquals("WPA3", wifiSecurityTypeLabel(4))
        assertEquals("WPA3-Enterprise", wifiSecurityTypeLabel(5)) // EAP_WPA3_ENTERPRISE_192_BIT
        assertEquals("OWE", wifiSecurityTypeLabel(6))
        assertEquals("WPA3-Enterprise", wifiSecurityTypeLabel(9)) // EAP_WPA3_ENTERPRISE
        assertEquals("Unknown", wifiSecurityTypeLabel(99))
    }
}
