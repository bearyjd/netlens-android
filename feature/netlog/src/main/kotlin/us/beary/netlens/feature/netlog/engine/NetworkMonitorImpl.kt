package us.beary.netlens.feature.netlog.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import us.beary.netlens.core.data.model.NetworkEvent
import javax.inject.Inject

class NetworkMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    override fun observeNetworkChanges(): Flow<NetworkEvent> = callbackFlow {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val transportType = resolveTransportType(capabilities)
                val isVpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                val details = buildNetworkDetails(capabilities)

                trySend(
                    NetworkEvent(
                        eventType = EVENT_CONNECTED,
                        transportType = transportType,
                        networkDetails = details,
                        isVpn = isVpn,
                    ),
                )
            }

            override fun onLost(network: Network) {
                trySend(
                    NetworkEvent(
                        eventType = EVENT_DISCONNECTED,
                        transportType = TRANSPORT_UNKNOWN,
                        networkDetails = "",
                        isVpn = false,
                    ),
                )
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                val transportType = resolveTransportType(networkCapabilities)
                val isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                val details = buildNetworkDetails(networkCapabilities)

                trySend(
                    NetworkEvent(
                        eventType = EVENT_CHANGED,
                        transportType = transportType,
                        networkDetails = details,
                        isVpn = isVpn,
                    ),
                )
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    private fun resolveTransportType(capabilities: NetworkCapabilities?): String =
        when {
            capabilities == null -> TRANSPORT_UNKNOWN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> TRANSPORT_VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TRANSPORT_WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TRANSPORT_CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TRANSPORT_ETHERNET
            else -> TRANSPORT_UNKNOWN
        }

    private fun buildNetworkDetails(capabilities: NetworkCapabilities?): String {
        if (capabilities == null) return ""
        val parts = mutableListOf<String>()
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            parts.add("Internet")
        }
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            parts.add("Validated")
        }
        val downMbps = capabilities.linkDownstreamBandwidthKbps / KBPS_PER_MBPS
        if (downMbps > 0) {
            parts.add("${downMbps}Mbps down")
        }
        return parts.joinToString(", ")
    }

    private companion object {
        const val EVENT_CONNECTED = "CONNECTED"
        const val EVENT_DISCONNECTED = "DISCONNECTED"
        const val EVENT_CHANGED = "CHANGED"
        const val TRANSPORT_WIFI = "WIFI"
        const val TRANSPORT_CELLULAR = "CELLULAR"
        const val TRANSPORT_VPN = "VPN"
        const val TRANSPORT_ETHERNET = "ETHERNET"
        const val TRANSPORT_UNKNOWN = "UNKNOWN"
        const val KBPS_PER_MBPS = 1_000
    }
}
