package com.ventouxlabs.netlens.feature.posture.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.ventouxlabs.netlens.core.network.parseWifiCapabilities
import com.ventouxlabs.netlens.core.network.wifiSecurityTypeLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WifiSecurityProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : EncryptionTypeProvider {

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
                wifiSecurityTypeLabel(info.currentSecurityType)
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
            parseWifiCapabilities(connected.capabilities)
        } catch (_: SecurityException) {
            null
        }
    }
}
