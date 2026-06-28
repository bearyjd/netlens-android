package com.ventouxlabs.netlens.core.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

internal data class VpnNetworkSnapshot(
    val hasDefaultRoute: Boolean,
)

/**
 * Returns the underlying physical network (WiFi or cellular) regardless of VPN state.
 * When a VPN is active, [ConnectivityManager.getActiveNetwork] returns the VPN interface,
 * which has no SIGNAL_STRENGTH or WiFi RSSI. Use this helper for any UI that needs to
 * reflect the physical link (signal bars, SSID, network type badge).
 */
fun getPhysicalNetwork(cm: ConnectivityManager): Network? =
    cm.allNetworks.firstOrNull { network ->
        val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
        !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

/**
 * Detect the current VPN tunneling mode by inspecting the VPN's own routing table.
 * A full-tunnel VPN installs a default route (0.0.0.0/0 / ::/0) so all traffic exits
 * the tunnel. A split-tunnel VPN only installs specific subnet routes, leaving other
 * traffic on the underlying physical interface.
 *
 * This is more reliable than checking whether the underlying network is still validated:
 * many VPN clients keep the cellular/Wi-Fi interface validated even in full-tunnel mode,
 * which would falsely look like a split tunnel.
 */
fun detectVpnState(cm: ConnectivityManager): VpnState {
    val vpnSnapshot = cm.allNetworks.firstNotNullOfOrNull { network ->
        val caps = cm.getNetworkCapabilities(network) ?: return@firstNotNullOfOrNull null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@firstNotNullOfOrNull null
        val routes = cm.getLinkProperties(network)?.routes.orEmpty()
        VpnNetworkSnapshot(hasDefaultRoute = routes.any { it.isDefaultRoute })
    }
    return detectVpnStateFromSnapshot(vpnSnapshot)
}

internal fun detectVpnStateFromSnapshot(snapshot: VpnNetworkSnapshot?): VpnState =
    when {
        snapshot == null -> VpnState.None
        snapshot.hasDefaultRoute -> VpnState.FullTunnel
        else -> VpnState.SplitTunnel
    }
