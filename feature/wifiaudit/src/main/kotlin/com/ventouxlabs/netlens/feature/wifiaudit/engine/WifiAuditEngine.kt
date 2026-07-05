package com.ventouxlabs.netlens.feature.wifiaudit.engine

import com.ventouxlabs.netlens.core.ui.UiText
import com.ventouxlabs.netlens.feature.wifiaudit.R
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
                title = UiText.Resource(R.string.wifiaudit_encryption_wpa3_title),
                description = UiText.Resource(R.string.wifiaudit_encryption_wpa3_description),
                guidance = UiText.Resource(R.string.wifiaudit_guidance_no_action),
            )
            upper.contains("WPA2") -> AuditFinding(
                id = "encryption",
                severity = AuditSeverity.Info,
                title = UiText.Resource(R.string.wifiaudit_encryption_wpa2_title),
                description = UiText.Resource(R.string.wifiaudit_encryption_wpa2_description),
                guidance = UiText.Resource(R.string.wifiaudit_encryption_wpa2_guidance),
            )
            upper.contains("WPA") -> AuditFinding(
                id = "encryption",
                severity = AuditSeverity.Warning,
                title = UiText.Resource(R.string.wifiaudit_encryption_wpa_title),
                description = UiText.Resource(R.string.wifiaudit_encryption_wpa_description),
                guidance = UiText.Resource(R.string.wifiaudit_encryption_wpa_guidance),
            )
            upper.contains("WEP") -> AuditFinding(
                id = "encryption",
                severity = AuditSeverity.Critical,
                title = UiText.Resource(R.string.wifiaudit_encryption_wep_title),
                description = UiText.Resource(R.string.wifiaudit_encryption_wep_description),
                guidance = UiText.Resource(R.string.wifiaudit_encryption_wep_guidance),
            )
            else -> AuditFinding(
                id = "encryption",
                severity = AuditSeverity.Critical,
                title = UiText.Resource(R.string.wifiaudit_encryption_open_title),
                description = UiText.Resource(R.string.wifiaudit_encryption_open_description),
                guidance = UiText.Resource(R.string.wifiaudit_encryption_open_guidance),
            )
        }
    }

    private fun checkSignalStrength(rssi: Int): AuditFinding = when {
        rssi >= -50 -> AuditFinding(
            id = "signal",
            severity = AuditSeverity.Pass,
            title = UiText.Resource(R.string.wifiaudit_signal_strong_title, listOf(rssi)),
            description = UiText.Resource(R.string.wifiaudit_signal_strong_description),
            guidance = UiText.Resource(R.string.wifiaudit_guidance_no_action),
        )
        rssi >= -65 -> AuditFinding(
            id = "signal",
            severity = AuditSeverity.Pass,
            title = UiText.Resource(R.string.wifiaudit_signal_good_title, listOf(rssi)),
            description = UiText.Resource(R.string.wifiaudit_signal_good_description),
            guidance = UiText.Resource(R.string.wifiaudit_guidance_no_action),
        )
        rssi >= -75 -> AuditFinding(
            id = "signal",
            severity = AuditSeverity.Info,
            title = UiText.Resource(R.string.wifiaudit_signal_moderate_title, listOf(rssi)),
            description = UiText.Resource(R.string.wifiaudit_signal_moderate_description),
            guidance = UiText.Resource(R.string.wifiaudit_signal_moderate_guidance),
        )
        else -> AuditFinding(
            id = "signal",
            severity = AuditSeverity.Warning,
            title = UiText.Resource(R.string.wifiaudit_signal_weak_title, listOf(rssi)),
            description = UiText.Resource(R.string.wifiaudit_signal_weak_description),
            guidance = UiText.Resource(R.string.wifiaudit_signal_weak_guidance),
        )
    }

    private fun checkHiddenSsid(ssid: String): AuditFinding? {
        if (ssid.isNotBlank()) return null
        return AuditFinding(
            id = "hidden_ssid",
            severity = AuditSeverity.Warning,
            title = UiText.Resource(R.string.wifiaudit_hidden_ssid_title),
            description = UiText.Resource(R.string.wifiaudit_hidden_ssid_description),
            guidance = UiText.Resource(R.string.wifiaudit_hidden_ssid_guidance),
        )
    }

    private fun checkWps(capabilities: String): AuditFinding? {
        if (!capabilities.uppercase().contains("WPS")) return null
        return AuditFinding(
            id = "wps",
            severity = AuditSeverity.Warning,
            title = UiText.Resource(R.string.wifiaudit_wps_title),
            description = UiText.Resource(R.string.wifiaudit_wps_description),
            guidance = UiText.Resource(R.string.wifiaudit_wps_guidance),
        )
    }

    private fun checkTkip(capabilities: String, security: String): AuditFinding? {
        val caps = capabilities.uppercase()
        if (!caps.contains("TKIP")) return null
        if (caps.contains("CCMP") || caps.contains("AES")) {
            return AuditFinding(
                id = "tkip",
                severity = AuditSeverity.Info,
                title = UiText.Resource(R.string.wifiaudit_tkip_mixed_title),
                description = UiText.Resource(R.string.wifiaudit_tkip_mixed_description),
                guidance = UiText.Resource(R.string.wifiaudit_tkip_mixed_guidance),
            )
        }
        return AuditFinding(
            id = "tkip",
            severity = AuditSeverity.Warning,
            title = UiText.Resource(R.string.wifiaudit_tkip_only_title),
            description = UiText.Resource(R.string.wifiaudit_tkip_only_description),
            guidance = UiText.Resource(R.string.wifiaudit_tkip_only_guidance),
        )
    }

    private fun checkBand(frequency: Int): AuditFinding? {
        if (frequency >= 5000) return null
        return AuditFinding(
            id = "band",
            severity = AuditSeverity.Info,
            title = UiText.Resource(R.string.wifiaudit_band_24ghz_title),
            description = UiText.Resource(R.string.wifiaudit_band_24ghz_description),
            guidance = UiText.Resource(R.string.wifiaudit_band_24ghz_guidance),
        )
    }

    private fun checkLinkSpeed(linkSpeedMbps: Int): AuditFinding? {
        if (linkSpeedMbps <= 0 || linkSpeedMbps >= 54) return null
        return AuditFinding(
            id = "link_speed",
            severity = AuditSeverity.Warning,
            title = UiText.Resource(R.string.wifiaudit_link_speed_low_title, listOf(linkSpeedMbps)),
            description = UiText.Resource(R.string.wifiaudit_link_speed_low_description),
            guidance = UiText.Resource(R.string.wifiaudit_link_speed_low_guidance),
        )
    }
}
