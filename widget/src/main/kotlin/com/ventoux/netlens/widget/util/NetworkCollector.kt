package com.ventoux.netlens.widget.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet6Address

data class CollectedNetworkData(
    val localIp: String = "",
    val isVpn: Boolean = false,
    val vpnInterfaceName: String = "",
    val rssi: Int = -1000,
    val rssiLevel: Int = -1,
    val linkSpeedMbps: Int = -1,
    val cellGeneration: String = "",
    val hasIpv6: Boolean = false,
    val isMetered: Boolean = false,
    val isCaptivePortal: Boolean = false,
    val hasPrivateDns: Boolean = false,
    val dnsServers: List<String> = emptyList(),
)

object NetworkCollector {

    private val VPN_INTERFACE_PREFIXES = listOf("tun", "wg", "ppp", "ipsec")

    suspend fun collect(context: Context): CollectedNetworkData = withContext(Dispatchers.IO) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return@withContext CollectedNetworkData()
            val caps = cm.getNetworkCapabilities(network) ?: return@withContext CollectedNetworkData()
            val linkProps = cm.getLinkProperties(network)

            val localIp = linkProps?.linkAddresses
                ?.firstOrNull { addr ->
                    val ia = addr.address
                    ia != null && !ia.isLoopbackAddress && ia.address.size == 4
                }
                ?.address?.hostAddress.orEmpty()

            val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val vpnInterfaceName = if (isVpn) {
                linkProps?.interfaceName?.takeIf { iface ->
                    VPN_INTERFACE_PREFIXES.any { prefix -> iface.startsWith(prefix) }
                }.orEmpty()
            } else {
                ""
            }

            val rssi: Int
            val rssiLevel: Int
            val linkSpeedMbps: Int
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    caps.transportInfo as? WifiInfo
                } else {
                    @Suppress("DEPRECATION")
                    (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo
                }
                rssi = wifiInfo?.rssi ?: -1000
                rssiLevel = if (rssi > -1000) {
                    @Suppress("DEPRECATION")
                    WifiManager.calculateSignalLevel(rssi, 5)
                } else {
                    -1
                }
                linkSpeedMbps = wifiInfo?.linkSpeed ?: -1
            } else {
                rssi = -1000
                rssiLevel = -1
                linkSpeedMbps = -1
            }

            val cellGeneration = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                detectCellGeneration(context)
            } else {
                ""
            }

            val hasIpv6 = linkProps?.linkAddresses?.any { addr ->
                val ia = addr.address
                ia is Inet6Address && !ia.isLinkLocalAddress && !ia.isLoopbackAddress
            } ?: false

            val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

            val isCaptivePortal = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            val hasPrivateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                linkProps?.isPrivateDnsActive == true ||
                    linkProps?.privateDnsServerName != null
            } else {
                false
            }

            val dnsServers = linkProps?.dnsServers
                ?.mapNotNull { it.hostAddress }
                ?: emptyList()

            CollectedNetworkData(
                localIp = localIp,
                isVpn = isVpn,
                vpnInterfaceName = vpnInterfaceName,
                rssi = rssi,
                rssiLevel = rssiLevel,
                linkSpeedMbps = linkSpeedMbps,
                cellGeneration = cellGeneration,
                hasIpv6 = hasIpv6,
                isMetered = isMetered,
                isCaptivePortal = isCaptivePortal,
                hasPrivateDns = hasPrivateDns,
                dnsServers = dnsServers,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            CollectedNetworkData()
        }
    }

    private fun detectCellGeneration(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return ""
            @Suppress("DEPRECATION")
            when (tm.networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA -> "3G+"
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> ""
            }
        } catch (_: SecurityException) {
            ""
        } catch (_: Exception) {
            ""
        }
    }
}
