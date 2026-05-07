package com.ventoux.netlens.core.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

internal data class NetworkCapsSnapshot(
    val hasVpn: Boolean,
    val hasInternet: Boolean,
    val hasValidated: Boolean,
    val hasWifiOrCellular: Boolean,
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
 * Detect the current VPN tunneling mode.
 *
 * Heuristic: if any non-VPN internet-capable network is also validated and active alongside
 * the VPN, treat it as a split tunnel. Otherwise the VPN is the only path → full tunnel.
 *
 * Known limitation: some VPN clients keep the underlying interface validated even in
 * full-tunnel mode, which can produce a false SplitTunnel reading. Acceptable trade-off
 * for security-awareness purposes.
 */
fun detectVpnState(cm: ConnectivityManager): VpnState {
    val snapshots = cm.allNetworks.mapNotNull { network ->
        val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
        NetworkCapsSnapshot(
            hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            hasValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            hasWifiOrCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        )
    }
    return detectVpnStateFromSnapshots(snapshots)
}

internal fun detectVpnStateFromSnapshots(snapshots: List<NetworkCapsSnapshot>): VpnState {
    val hasVpn = snapshots.any { it.hasVpn }
    if (!hasVpn) return VpnState.None
    val hasNonVpnInternet = snapshots.any {
        !it.hasVpn && it.hasInternet && it.hasValidated && it.hasWifiOrCellular
    }
    return if (hasNonVpnInternet) VpnState.SplitTunnel else VpnState.FullTunnel
}
