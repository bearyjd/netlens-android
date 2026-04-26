package com.ventoux.netlens.feature.posture.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WifiSecurityProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : EncryptionTypeProvider {

    fun currentSsid(): String? {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        @Suppress("DEPRECATION")
        val info: WifiInfo = wm.connectionInfo ?: return null
        val ssid = info.ssid?.removeSurrounding("\"")
        if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") return null
        return ssid
    }

    override fun currentEncryptionType(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return securityTypeApi31()
        }
        return securityTypeLegacy()
    }

    private fun securityTypeApi31(): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val network = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(network) ?: return null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

            val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            @Suppress("DEPRECATION")
            val info: WifiInfo = wm.connectionInfo ?: return null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (info.currentSecurityType) {
                    WifiInfo.SECURITY_TYPE_OPEN -> "Open"
                    WifiInfo.SECURITY_TYPE_WEP -> "WEP"
                    WifiInfo.SECURITY_TYPE_PSK -> "WPA2"
                    WifiInfo.SECURITY_TYPE_SAE -> "WPA3"
                    WifiInfo.SECURITY_TYPE_EAP -> "WPA2-Enterprise"
                    WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3-Enterprise"
                    WifiInfo.SECURITY_TYPE_OWE -> "OWE"
                    WifiInfo.SECURITY_TYPE_WAPI_PSK -> "WAPI"
                    WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI-Cert"
                    else -> "Unknown"
                }
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun securityTypeLegacy(): String? {
        return try {
            val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val network = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(network) ?: return null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

            val scanResults = wm.scanResults ?: return null
            val info = wm.connectionInfo ?: return null
            val connected = scanResults.find { it.BSSID == info.bssid } ?: return null

            when {
                connected.capabilities.contains("WPA3") || connected.capabilities.contains("SAE") -> "WPA3"
                connected.capabilities.contains("OWE") -> "OWE"
                connected.capabilities.contains("WPA2") || connected.capabilities.contains("RSN") -> "WPA2"
                connected.capabilities.contains("WPA") -> "WPA"
                connected.capabilities.contains("WEP") -> "WEP"
                else -> "Open"
            }
        } catch (_: SecurityException) {
            null
        }
    }
}
