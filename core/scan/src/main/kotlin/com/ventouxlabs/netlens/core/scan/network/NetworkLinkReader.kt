package com.ventouxlabs.netlens.core.scan.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import com.ventouxlabs.netlens.core.network.calculateNetworkAddress
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

/** The IPv4 gateway and subnet (`a.b.c.0/24`) of one network. */
data class LinkInfo(val gatewayIp: String?, val subnet: String?)

/** The platform boundary of [NetworkIdentityImpl], so the choice of network is testable on the JVM. */
interface NetworkLinkReader {
    /** The network this process is bound to (the LAN, while a scan holds the binding), if any. */
    fun boundLink(): LinkInfo?

    /** The system default network: the VPN whenever one is up. */
    fun activeLink(): LinkInfo?

    fun ssid(): String?
}

@Singleton
class AndroidNetworkLinkReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkLinkReader {

    private val cm: ConnectivityManager
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun boundLink(): LinkInfo? = cm.boundNetworkForProcess?.let(::link)

    override fun activeLink(): LinkInfo? = cm.activeNetwork?.let(::link)

    private fun link(network: Network): LinkInfo? {
        val props = cm.getLinkProperties(network) ?: return null
        val route = props.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?: props.routes.firstOrNull { it.gateway is Inet4Address }
        val addr = props.linkAddresses.firstOrNull { it.address is Inet4Address }
        val subnet = addr?.address?.hostAddress?.let { "${calculateNetworkAddress(it, addr.prefixLength)}/${addr.prefixLength}" }
        return LinkInfo(route?.gateway?.hostAddress, subnet)
    }

    override fun ssid(): String? = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION") // Foreground-only; ACCESS_FINE_LOCATION already granted.
        wm.connectionInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
    } catch (_: Exception) {
        null
    }
}
