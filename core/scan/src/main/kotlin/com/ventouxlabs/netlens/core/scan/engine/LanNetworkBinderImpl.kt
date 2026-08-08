package com.ventouxlabs.netlens.core.scan.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Binds the process to a Wi-Fi or Ethernet network for the duration of a scan.
 *
 * See [LanNetworkBinder] for why the binding is process-wide and what it costs.
 */
class LanNetworkBinderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LanNetworkBinder {

    override suspend fun <T> withLanNetwork(block: suspend (bound: Boolean) -> T): T {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            ?: return block(false)
        val lanNetwork = connectivityManager.findLanNetwork() ?: return block(false)

        // Restore whatever was bound before rather than unconditionally clearing to null: another
        // caller may have bound the process for its own reasons, and clobbering that would trade
        // this bug for a subtler one.
        val previous = connectivityManager.boundNetworkForProcess
        return try {
            // bindProcessToNetwork returns false if lanNetwork went invalid between
            // findLanNetwork() and here (e.g. Wi-Fi dropped) — the process stays on whatever it
            // was bound to before, so callers must not be told they're on the LAN.
            val didBind = connectivityManager.bindProcessToNetwork(lanNetwork)
            block(didBind)
        } finally {
            connectivityManager.bindProcessToNetwork(previous)
        }
    }

    /**
     * Picks a network that carries the local subnet.
     *
     * `NOT_VPN` is the load-bearing part: a VPN network can also report `TRANSPORT_WIFI` when it
     * runs over Wi-Fi, so matching on transport alone would happily select the tunnel this exists
     * to avoid.
     */
    private fun ConnectivityManager.findLanNetwork(): Network? =
        allNetworks.firstOrNull { network ->
            val capabilities = getNetworkCapabilities(network) ?: return@firstOrNull false
            val isLanTransport =
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            isLanTransport && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
}
