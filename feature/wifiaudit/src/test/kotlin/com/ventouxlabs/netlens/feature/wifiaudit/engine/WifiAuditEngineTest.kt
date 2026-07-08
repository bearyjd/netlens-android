package com.ventouxlabs.netlens.feature.wifiaudit.engine

import com.ventouxlabs.netlens.feature.wifiaudit.model.AuditSeverity
import com.ventouxlabs.netlens.feature.wifiaudit.model.ConnectedNetworkInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WifiAuditEngineTest {

    private fun network(
        ssid: String = "HomeWifi",
        bssid: String = "aa:bb:cc:dd:ee:ff",
        rssi: Int = -50,
        frequency: Int = 5180,
        security: String = "WPA2",
        capabilities: String = "[WPA2-PSK-CCMP][ESS]",
        linkSpeedMbps: Int = 300,
        ipAddress: String? = "192.168.1.50",
    ) = ConnectedNetworkInfo(ssid, bssid, rssi, frequency, security, capabilities, linkSpeedMbps, ipAddress)

    @Test
    fun `WPA3 encryption passes`() {
        val findings = WifiAuditEngine.audit(network(security = "WPA3"))
        val encryption = findings.first { it.id == "encryption" }
        assertEquals(AuditSeverity.Pass, encryption.severity)
    }

    @Test
    fun `WPA2 encryption is informational`() {
        val findings = WifiAuditEngine.audit(network(security = "WPA2"))
        val encryption = findings.first { it.id == "encryption" }
        assertEquals(AuditSeverity.Info, encryption.severity)
    }

    @Test
    fun `WPA-only encryption is a warning`() {
        val findings = WifiAuditEngine.audit(network(security = "WPA"))
        val encryption = findings.first { it.id == "encryption" }
        assertEquals(AuditSeverity.Warning, encryption.severity)
    }

    @Test
    fun `WEP encryption is critical`() {
        val findings = WifiAuditEngine.audit(network(security = "WEP"))
        val encryption = findings.first { it.id == "encryption" }
        assertEquals(AuditSeverity.Critical, encryption.severity)
    }

    @Test
    fun `open network is critical`() {
        val findings = WifiAuditEngine.audit(network(security = "Open"))
        val encryption = findings.first { it.id == "encryption" }
        assertEquals(AuditSeverity.Critical, encryption.severity)
    }

    @Test
    fun `strong signal passes`() {
        val findings = WifiAuditEngine.audit(network(rssi = -40))
        val signal = findings.first { it.id == "signal" }
        assertEquals(AuditSeverity.Pass, signal.severity)
    }

    @Test
    fun `moderate signal is informational`() {
        val findings = WifiAuditEngine.audit(network(rssi = -70))
        val signal = findings.first { it.id == "signal" }
        assertEquals(AuditSeverity.Info, signal.severity)
    }

    @Test
    fun `weak signal is a warning`() {
        val findings = WifiAuditEngine.audit(network(rssi = -80))
        val signal = findings.first { it.id == "signal" }
        assertEquals(AuditSeverity.Warning, signal.severity)
    }

    @Test
    fun `blank ssid flags hidden network`() {
        val findings = WifiAuditEngine.audit(network(ssid = ""))
        assertTrue(findings.any { it.id == "hidden_ssid" })
    }

    @Test
    fun `non-blank ssid does not flag hidden network`() {
        val findings = WifiAuditEngine.audit(network(ssid = "HomeWifi"))
        assertTrue(findings.none { it.id == "hidden_ssid" })
    }

    @Test
    fun `WPS capability is flagged`() {
        val findings = WifiAuditEngine.audit(network(capabilities = "[WPA2-PSK-CCMP][WPS][ESS]"))
        assertTrue(findings.any { it.id == "wps" })
    }

    @Test
    fun `no WPS capability is not flagged`() {
        val findings = WifiAuditEngine.audit(network(capabilities = "[WPA2-PSK-CCMP][ESS]"))
        assertTrue(findings.none { it.id == "wps" })
    }

    @Test
    fun `TKIP-only is a warning`() {
        val findings = WifiAuditEngine.audit(network(capabilities = "[WPA-PSK-TKIP][ESS]"))
        val tkip = findings.first { it.id == "tkip" }
        assertEquals(AuditSeverity.Warning, tkip.severity)
    }

    @Test
    fun `mixed TKIP and CCMP is informational`() {
        val findings = WifiAuditEngine.audit(network(capabilities = "[WPA2-PSK-TKIP+CCMP][ESS]"))
        val tkip = findings.first { it.id == "tkip" }
        assertEquals(AuditSeverity.Info, tkip.severity)
    }

    @Test
    fun `no TKIP is not flagged`() {
        val findings = WifiAuditEngine.audit(network(capabilities = "[WPA2-PSK-CCMP][ESS]"))
        assertTrue(findings.none { it.id == "tkip" })
    }

    @Test
    fun `2point4GHz band is flagged`() {
        val findings = WifiAuditEngine.audit(network(frequency = 2437))
        assertTrue(findings.any { it.id == "band" })
    }

    @Test
    fun `5GHz band is not flagged`() {
        val findings = WifiAuditEngine.audit(network(frequency = 5180))
        assertTrue(findings.none { it.id == "band" })
    }

    @Test
    fun `low link speed is a warning`() {
        val findings = WifiAuditEngine.audit(network(linkSpeedMbps = 12))
        assertTrue(findings.any { it.id == "link_speed" })
    }

    @Test
    fun `high link speed is not flagged`() {
        val findings = WifiAuditEngine.audit(network(linkSpeedMbps = 300))
        assertTrue(findings.none { it.id == "link_speed" })
    }

    @Test
    fun `unknown link speed of zero is not flagged`() {
        val findings = WifiAuditEngine.audit(network(linkSpeedMbps = 0))
        assertTrue(findings.none { it.id == "link_speed" })
    }

    @Test
    fun `findings are sorted by severity, most severe first`() {
        val findings = WifiAuditEngine.audit(
            network(security = "WEP", capabilities = "[WPA-PSK-TKIP][WPS][ESS]", frequency = 2437, linkSpeedMbps = 10),
        )
        val severities = findings.map { it.severity.ordinal }
        assertEquals(severities.sorted(), severities)
    }
}
