package com.ventouxlabs.netlens.widget

import com.ventouxlabs.netlens.core.network.VpnState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetScoringTest {

    @Test
    fun `gradeFor returns A for 90 and above`() {
        assertEquals("A", gradeFor(90))
        assertEquals("A", gradeFor(100))
    }

    @Test
    fun `gradeFor returns B for 75 to 89`() {
        assertEquals("B", gradeFor(75))
        assertEquals("B", gradeFor(89))
    }

    @Test
    fun `gradeFor returns C for 60 to 74`() {
        assertEquals("C", gradeFor(60))
        assertEquals("C", gradeFor(74))
    }

    @Test
    fun `gradeFor returns D for 40 to 59`() {
        assertEquals("D", gradeFor(40))
        assertEquals("D", gradeFor(59))
    }

    @Test
    fun `gradeFor returns F for below 40`() {
        assertEquals("F", gradeFor(39))
        assertEquals("F", gradeFor(0))
    }

    @Test
    fun `encryptionScore returns 100 for WPA3`() {
        assertEquals(100, encryptionScore("WPA3"))
        assertEquals(100, encryptionScore("WPA3-Enterprise"))
        assertEquals(100, encryptionScore("wpa3"))
    }

    @Test
    fun `encryptionScore returns 80 for OWE`() {
        assertEquals(80, encryptionScore("OWE"))
        assertEquals(80, encryptionScore("ENHANCED_OPEN"))
    }

    @Test
    fun `encryptionScore returns 70 for WPA2`() {
        assertEquals(70, encryptionScore("WPA2"))
        assertEquals(70, encryptionScore("WPA2-Enterprise"))
    }

    @Test
    fun `encryptionScore returns 50 for WPA`() {
        assertEquals(50, encryptionScore("WPA"))
    }

    @Test
    fun `encryptionScore returns 20 for WEP`() {
        assertEquals(20, encryptionScore("WEP"))
    }

    @Test
    fun `encryptionScore returns 0 for null or open`() {
        assertEquals(0, encryptionScore(null))
        assertEquals(0, encryptionScore("Open"))
        assertEquals(0, encryptionScore(""))
    }

    @Test
    fun `encryptionScore returns 40 for unknown types`() {
        assertEquals(40, encryptionScore("WAPI"))
    }

    @Test
    fun `deviceCountScore returns 100 for small networks`() {
        assertEquals(100, deviceCountScore(0))
        assertEquals(100, deviceCountScore(5))
    }

    @Test
    fun `deviceCountScore returns 80 for typical home networks`() {
        assertEquals(80, deviceCountScore(6))
        assertEquals(80, deviceCountScore(15))
    }

    @Test
    fun `deviceCountScore returns 60 for medium networks`() {
        assertEquals(60, deviceCountScore(16))
        assertEquals(60, deviceCountScore(30))
    }

    @Test
    fun `deviceCountScore returns 40 for large networks`() {
        assertEquals(40, deviceCountScore(31))
        assertEquals(40, deviceCountScore(100))
    }

    @Test
    fun `isEncryptionSecure returns true for WPA3 WPA2 and OWE`() {
        assertTrue(isEncryptionSecure("WPA3"))
        assertTrue(isEncryptionSecure("WPA2"))
        assertTrue(isEncryptionSecure("OWE"))
        assertTrue(isEncryptionSecure("WPA2-Enterprise"))
    }

    @Test
    fun `isEncryptionSecure returns false for WEP and open`() {
        assertFalse(isEncryptionSecure("WEP"))
        assertFalse(isEncryptionSecure("Open"))
        assertFalse(isEncryptionSecure("WPA"))
    }

    @Test
    fun `isEncryptionSecure returns true for null (not on WiFi)`() {
        assertTrue(isEncryptionSecure(null))
    }

    @Test
    fun `parseCapabilities detects WPA3 from SAE`() {
        assertEquals("WPA3", parseCapabilities("[WPA2-PSK-CCMP][RSN-SAE-CCMP]"))
        assertEquals("WPA3", parseCapabilities("[WPA3-SAE]"))
    }

    @Test
    fun `parseCapabilities detects WPA2 from RSN`() {
        assertEquals("WPA2", parseCapabilities("[RSN-PSK-CCMP]"))
        assertEquals("WPA2", parseCapabilities("[WPA2-PSK-CCMP]"))
    }

    @Test
    fun `parseCapabilities detects WEP`() {
        assertEquals("WEP", parseCapabilities("[WEP]"))
    }

    @Test
    fun `parseCapabilities returns Open for no security`() {
        assertEquals("Open", parseCapabilities("[ESS]"))
    }

    @Test
    fun `computeWidgetScore returns grade based on weighted average`() {
        val score = computeWidgetScore(
            encryptionType = "WPA3",
            deviceCount = 3,
            vpnState = VpnState.FullTunnel,
        )
        assertEquals("A", score.grade)
        assertEquals(0xFF4CAF50.toInt(), score.colorArgb)
    }

    @Test
    fun `computeWidgetScore flags issues for weak encryption and no vpn`() {
        val score = computeWidgetScore(
            encryptionType = "Open",
            deviceCount = 50,
            vpnState = VpnState.None,
        )
        assertEquals(3, score.issueCount)
        assertEquals("Weak or no encryption", score.topIssue)
        assertEquals("encryption", score.topIssueId)
    }

    @Test
    fun `computeWidgetScore with null encryption and vpn active`() {
        val score = computeWidgetScore(
            encryptionType = null,
            deviceCount = 10,
            vpnState = VpnState.FullTunnel,
        )
        assertTrue(score.issueCount >= 1)
        assertNotEquals("vpn", score.topIssueId)
    }

    @Test
    fun `computeWidgetScore split tunnel scores between full and none`() {
        val full = computeWidgetScore(encryptionType = "WPA2", deviceCount = 10, vpnState = VpnState.FullTunnel)
        val split = computeWidgetScore(encryptionType = "WPA2", deviceCount = 10, vpnState = VpnState.SplitTunnel)
        val none = computeWidgetScore(encryptionType = "WPA2", deviceCount = 10, vpnState = VpnState.None)
        // SplitTunnel must NOT collapse to FullTunnel — that was the migrated HIGH bug.
        assertNotEquals(full.grade, split.grade)
        assertEquals("VPN is split-tunnel", split.topIssue)
        assertEquals("vpn", split.topIssueId)
        assertEquals("VPN not active", none.topIssue)
    }
}
