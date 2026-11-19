package com.ventouxlabs.netlens.core.network

import android.net.wifi.WifiInfo

/**
 * Shared Wi-Fi security classification so the posture score, Wi-Fi audit and home-screen
 * widget label the same network identically. The returned strings are the contract consumed by
 * the scoring and trust logic; keep them stable.
 */

/** Pre-API 31 path: classify a scan result's `capabilities` string such as `[RSN-PSK-CCMP][ESS]`. */
fun parseWifiCapabilities(capabilities: String): String {
    val hasSae = capabilities.contains("SAE")
    val hasPsk = capabilities.contains("PSK")
    return when {
        capabilities.contains("SUITE_B") -> "WPA3-Enterprise"
        capabilities.contains("EAP") && !hasPsk ->
            if (capabilities.contains("WPA2") || capabilities.contains("RSN")) {
                "WPA2-Enterprise"
            } else {
                // Legacy WPA1 enterprise ([WPA-EAP-TKIP]) is not WPA2.
                "WPA-Enterprise"
            }
        hasSae && hasPsk -> "WPA2/WPA3"
        hasSae || capabilities.contains("WPA3") -> "WPA3"
        capabilities.contains("OWE") -> "OWE"
        capabilities.contains("WPA2") || capabilities.contains("RSN") -> "WPA2"
        capabilities.contains("WPA") -> "WPA"
        capabilities.contains("WEP") -> "WEP"
        else -> "Open"
    }
}

/** API 31+ path: map [WifiInfo.getCurrentSecurityType] to the same labels. */
@Suppress("InlinedApi") // Constants are inlined at compile time; unknown values fall to "Unknown".
fun wifiSecurityTypeLabel(securityType: Int): String = when (securityType) {
    WifiInfo.SECURITY_TYPE_OPEN -> "Open"
    WifiInfo.SECURITY_TYPE_WEP -> "WEP"
    WifiInfo.SECURITY_TYPE_PSK -> "WPA2"
    WifiInfo.SECURITY_TYPE_SAE -> "WPA3"
    WifiInfo.SECURITY_TYPE_EAP -> "WPA2-Enterprise"
    WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
    WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT,
    -> "WPA3-Enterprise"
    WifiInfo.SECURITY_TYPE_OWE -> "OWE"
    WifiInfo.SECURITY_TYPE_WAPI_PSK -> "WAPI"
    WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI-Cert"
    else -> "Unknown"
}
