package com.ventoux.netlens.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ConnectivityManagerNetworkInterfaceProvider @Inject constructor(
    @ApplicationContext context: Context,
) : NetworkInterfaceProvider {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("MissingPermission")
    override fun getNetworkInterfaces(): List<NetworkInterfaceInfo> =
        connectivityManager.allNetworks.mapNotNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            val lp = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
            val linkAddr = lp.linkAddresses.firstOrNull { it.isIpv4() } ?: return@mapNotNull null
            val ip = linkAddr.address.hostAddress ?: return@mapNotNull null
            val isVpn = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            NetworkInterfaceInfo(
                ip = ip,
                prefixLength = linkAddr.prefixLength,
                interfaceName = lp.interfaceName ?: "",
                label = labelFor(caps, isVpn),
                isVpn = isVpn,
            )
        }

    @Suppress("MissingPermission")
    override fun getActiveNetworkInterface(): NetworkInterfaceInfo? {
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
        val lp = connectivityManager.getLinkProperties(activeNetwork) ?: return null
        val linkAddr = lp.linkAddresses.firstOrNull { it.isIpv4() } ?: return null
        val ip = linkAddr.address.hostAddress ?: return null
        val isVpn = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        return NetworkInterfaceInfo(
            ip = ip,
            prefixLength = linkAddr.prefixLength,
            interfaceName = lp.interfaceName ?: "",
            label = labelFor(caps, isVpn),
            isVpn = isVpn,
        )
    }
}

private fun labelFor(caps: NetworkCapabilities, isVpn: Boolean): String = when {
    isVpn -> "VPN"
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
    else -> "Network"
}

private fun LinkAddress.isIpv4(): Boolean = address.address.size == 4
