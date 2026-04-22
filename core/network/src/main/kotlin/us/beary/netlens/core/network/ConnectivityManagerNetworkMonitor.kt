package us.beary.netlens.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import javax.inject.Inject

class ConnectivityManagerNetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : NetworkMonitor {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val currentNetwork = connectivityManager.activeNetwork
        val currentCaps = connectivityManager.getNetworkCapabilities(currentNetwork)
        trySend(currentCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)

        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.conflate()

    override val isVpnActive: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                val vpnActive = !capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_VPN,
                )
                trySend(vpnActive)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val currentNetwork = connectivityManager.activeNetwork
        val currentCaps = connectivityManager.getNetworkCapabilities(currentNetwork)
        val initialVpn = currentCaps?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_NOT_VPN,
        )?.not() ?: false
        trySend(initialVpn)

        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.conflate()
}
