package com.ventouxlabs.netlens.feature.devices

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.ventouxlabs.netlens.core.network.calculateNetworkAddress
import com.ventouxlabs.netlens.core.scan.engine.ArpTableReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkIdentityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val arpTableReader: ArpTableReader,
) : NetworkIdentity {

    private val cm: ConnectivityManager
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override suspend fun currentGatewayMac(): String? {
        val gatewayIp = currentGatewayIp() ?: return null
        arpTableReader.invalidateCache()
        return arpTableReader.getMacForIp(gatewayIp)
    }

    override fun currentSubnet(): String? {
        val network = cm.activeNetwork ?: return null
        val link = cm.getLinkProperties(network) ?: return null
        val addr = link.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val ip = addr.address.hostAddress ?: return null
        val prefix = addr.prefixLength
        val networkAddress = calculateNetworkAddress(ip, prefix)
        return "$networkAddress/$prefix"
    }

    override fun currentSsid(): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION") // Foreground-only; ACCESS_FINE_LOCATION already granted.
            wm.connectionInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
        } catch (_: Exception) {
            null
        }
    }

    private fun currentGatewayIp(): String? {
        val network = cm.activeNetwork ?: return null
        val link = cm.getLinkProperties(network) ?: return null
        val defaultRoute = link.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?: link.routes.firstOrNull { it.gateway is Inet4Address }
        return defaultRoute?.gateway?.hostAddress
    }
}
