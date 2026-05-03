package com.ventoux.netlens.feature.wifiaudit.engine

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import com.ventoux.netlens.feature.wifiaudit.model.ConnectedNetworkInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface WifiInfoReader {
    fun readConnected(): ConnectedNetworkInfo?
}

class WifiInfoReaderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WifiInfoReader {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    override fun readConnected(): ConnectedNetworkInfo? {
        @Suppress("DEPRECATION")
        val wifiInfo: WifiInfo = wifiManager.connectionInfo ?: return null
        val ssid = wifiInfo.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: return null
        if (ssid == "<unknown ssid>") return null

        val security = findSecurityForBssid(wifiInfo.bssid)

        @Suppress("DEPRECATION")
        val ipInt = wifiInfo.ipAddress
        val ipAddress = if (ipInt != 0) {
            "${ipInt and 0xFF}.${ipInt shr 8 and 0xFF}.${ipInt shr 16 and 0xFF}.${ipInt shr 24 and 0xFF}"
        } else {
            null
        }

        return ConnectedNetworkInfo(
            ssid = ssid,
            bssid = wifiInfo.bssid ?: "",
            rssi = wifiInfo.rssi,
            frequency = wifiInfo.frequency,
            security = security.first,
            capabilities = security.second,
            linkSpeedMbps = wifiInfo.linkSpeed,
            ipAddress = ipAddress,
        )
    }

    private fun findSecurityForBssid(bssid: String?): Pair<String, String> {
        if (bssid == null) return "Unknown" to ""
        val result = wifiManager.scanResults
            ?.firstOrNull { it.BSSID == bssid }
            ?: return "Unknown" to ""
        val caps = result.capabilities ?: ""
        val security = when {
            caps.contains("WPA3") -> "WPA3"
            caps.contains("WPA2") -> "WPA2"
            caps.contains("WPA") -> "WPA"
            caps.contains("WEP") -> "WEP"
            else -> "Open"
        }
        return security to caps
    }
}
