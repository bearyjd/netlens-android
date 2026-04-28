package us.beary.netlens.core.network

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
            val label = when {
                isVpn -> "VPN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Network"
            }
            NetworkInterfaceInfo(
                ip = ip,
                prefixLength = linkAddr.prefixLength,
                interfaceName = lp.interfaceName ?: "",
                label = label,
                isVpn = isVpn,
            )
        }

    override fun getActiveNetworkInterface(): NetworkInterfaceInfo? {
        val activeNetwork = connectivityManager.activeNetwork
        val lp = connectivityManager.getLinkProperties(activeNetwork) ?: return null
        val linkAddr = lp.linkAddresses.firstOrNull { it.isIpv4() } ?: return null
        val ip = linkAddr.address.hostAddress ?: return null
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isVpn = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)?.not() ?: false
        val label = when {
            isVpn -> "VPN"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Network"
        }
        return NetworkInterfaceInfo(
            ip = ip,
            prefixLength = linkAddr.prefixLength,
            interfaceName = lp.interfaceName ?: "",
            label = label,
            isVpn = isVpn,
        )
    }
}

private fun LinkAddress.isIpv4(): Boolean = address.address.size == 4
