package com.ventouxlabs.netlens.feature.wifiaudit.engine

import com.ventouxlabs.netlens.feature.wifiaudit.model.AuditFinding
import com.ventouxlabs.netlens.feature.wifiaudit.model.AuditSeverity
import com.ventouxlabs.netlens.feature.wifiaudit.model.ConnectedNetworkInfo

object WifiAuditEngine {

    fun audit(info: ConnectedNetworkInfo): List<AuditFinding> = buildList {
        add(checkEncryption(info.security))
        add(checkSignalStrength(info.rssi))
        checkHiddenSsid(info.ssid)?.let { add(it) }
        checkWps(info.capabilities)?.let { add(it) }
        checkTkip(info.capabilities, info.security)?.let { add(it) }
        checkBand(info.frequency)?.let { add(it) }
        checkLinkSpeed(info.linkSpeedMbps)?.let { add(it) }
    }.sortedBy { it.severity.ordinal }

    private fun checkEncryption(security: String): AuditFinding {
        val upper = security.uppercase()
        return when {
            upper.contains("WPA3") -> AuditFinding(
                id = "encryption",
                severity = AuditSeverity.Pass,
                title = "WPA3 Encryption",
                description = "This network uses WPA3, the strongest available Wi-Fi encryption standard.",
                guidance = "No action needed.",
            )
            upper.contains("WPA2") -> AuditFinding(
                id = "encryption",
                severity = AuditSeverity.Info,
                title = "WPA2 Encryption",
                description = "This network uses WPA2. It is adequate but can be vulnerable to offline dictionary attacks.",
                guidance = "Consider upgrading your router to WPA3 if supported.",
            )
            upper.contains("WPA") -> AuditFinding(
                id = "encryption",
                severity = AuditSeverity.Warning,
                title = "Outdated WPA Encryption",
                description = "WPA is outdated and has known vulnerabilities including TKIP weaknesses.",
                guidance = "Upgrade your router firmware and switch to WPA2 or WPA3.",
            )
            upper.contains("WEP") -> AuditFinding(
                id = "encryption",
                severity = AuditSeverity.Critical,
                title = "WEP Encryption (Broken)",
                description = "WEP can be cracked in minutes with freely available tools. Your traffic is effectively unprotected.",
                guidance = "Switch to WPA2 or WPA3 immediately. If your router only supports WEP, replace it.",
            )
            else -> AuditFinding(
                id = "encryption",
                severity = AuditSeverity.Critical,
                title = "Open Network (No Encryption)",
                description = "This network has no encryption. Anyone nearby can intercept your traffic including passwords, emails, and browsing activity.",
                guidance = "Avoid transmitting sensitive data. Use a VPN, or connect to a password-protected network.",
            )
        }
    }

    private fun checkSignalStrength(rssi: Int): AuditFinding = when {
        rssi >= -50 -> AuditFinding(
            id = "signal",
            severity = AuditSeverity.Pass,
            title = "Strong Signal ($rssi dBm)",
            description = "Excellent signal strength. Connection should be stable and fast.",
            guidance = "No action needed.",
        )
        rssi >= -65 -> AuditFinding(
            id = "signal",
            severity = AuditSeverity.Pass,
            title = "Good Signal ($rssi dBm)",
            description = "Good signal strength for normal use.",
            guidance = "No action needed.",
        )
        rssi >= -75 -> AuditFinding(
            id = "signal",
            severity = AuditSeverity.Info,
            title = "Moderate Signal ($rssi dBm)",
            description = "Signal is moderate. You may experience occasional slowdowns.",
            guidance = "Move closer to the router or consider a Wi-Fi extender.",
        )
        else -> AuditFinding(
            id = "signal",
            severity = AuditSeverity.Warning,
            title = "Weak Signal ($rssi dBm)",
            description = "Weak signal increases packet loss and latency. Distant or spoofed access points often appear with low signal.",
            guidance = "Move closer to the access point. Verify you are connected to the intended network.",
        )
    }

    private fun checkHiddenSsid(ssid: String): AuditFinding? {
        if (ssid.isNotBlank()) return null
        return AuditFinding(
            id = "hidden_ssid",
            severity = AuditSeverity.Warning,
            title = "Hidden Network Name",
            description = "This network does not broadcast its SSID. Hidden networks are not more secure — devices actively probe for them, which can leak the network name.",
            guidance = "Hiding the SSID provides no real security. Rely on strong encryption (WPA3/WPA2) instead.",
        )
    }

    private fun checkWps(capabilities: String): AuditFinding? {
        if (!capabilities.uppercase().contains("WPS")) return null
        return AuditFinding(
            id = "wps",
            severity = AuditSeverity.Warning,
            title = "WPS Enabled",
            description = "Wi-Fi Protected Setup (WPS) has a known brute-force vulnerability that can expose your network password.",
            guidance = "Disable WPS in your router settings. Use a strong password with WPA2/WPA3 instead.",
        )
    }

    private fun checkTkip(capabilities: String, security: String): AuditFinding? {
        val caps = capabilities.uppercase()
        if (!caps.contains("TKIP")) return null
        if (caps.contains("CCMP") || caps.contains("AES")) {
            return AuditFinding(
                id = "tkip",
                severity = AuditSeverity.Info,
                title = "TKIP Available (Mixed Mode)",
                description = "The router supports both TKIP and AES/CCMP. TKIP is a legacy cipher with known weaknesses.",
                guidance = "Configure your router to use AES/CCMP only and disable TKIP.",
            )
        }
        return AuditFinding(
            id = "tkip",
            severity = AuditSeverity.Warning,
            title = "TKIP Only (Weak Cipher)",
            description = "This network uses only TKIP, a deprecated cipher vulnerable to certain attacks.",
            guidance = "Switch your router to AES/CCMP mode. Most devices made after 2010 support it.",
        )
    }

    private fun checkBand(frequency: Int): AuditFinding? {
        if (frequency >= 5000) return null
        return AuditFinding(
            id = "band",
            severity = AuditSeverity.Info,
            title = "2.4 GHz Band",
            description = "You are on the 2.4 GHz band, which is more congested and susceptible to interference from neighboring networks and devices.",
            guidance = "Connect to the 5 GHz band if your router offers it for better speed and less interference.",
        )
    }

    private fun checkLinkSpeed(linkSpeedMbps: Int): AuditFinding? {
        if (linkSpeedMbps <= 0 || linkSpeedMbps >= 54) return null
        return AuditFinding(
            id = "link_speed",
            severity = AuditSeverity.Warning,
            title = "Low Link Speed ($linkSpeedMbps Mbps)",
            description = "The negotiated link speed is very low, which may indicate an old access point or heavy interference.",
            guidance = "Check for sources of interference. Consider upgrading to a newer router.",
        )
    }
}
