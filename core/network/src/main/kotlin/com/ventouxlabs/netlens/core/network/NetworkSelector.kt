package com.ventouxlabs.netlens.core.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

internal data class VpnNetworkSnapshot(
    val hasDefaultRoute: Boolean,
)

/** Transport flags of one enumerated network, snapshotted so selection stays JVM-testable. */
internal data class TransportSnapshot(
    val isVpn: Boolean,
    val isWifi: Boolean,
    val isCellular: Boolean,
)

/**
 * Picks the physical network out of an enumeration. Precedence:
 *
 * 1. The active network itself, when it is already physical (non-VPN Wi-Fi/cellular) —
 *    the OS's own routing decision is authoritative, e.g. a user genuinely on cellular
 *    while a Wi-Fi network idles in the list must not be shown Wi-Fi data.
 * 2. Otherwise (active is a VPN, or unknown): Wi-Fi over cellular. `allNetworks` order is
 *    an OS implementation detail — with a VPN active both radios stay alive, and taking
 *    the first match renders whichever transport happened to enumerate first; on some
 *    devices that is cellular, so the widget showed cellular dBm (-114, red bars) while
 *    the user sat on strong Wi-Fi.
 */
internal fun selectPhysicalIndex(candidates: List<TransportSnapshot>, activeIndex: Int? = null): Int? {
    val eligible = candidates.withIndex()
        .filter { (_, c) -> !c.isVpn && (c.isWifi || c.isCellular) }
    if (activeIndex != null && eligible.any { it.index == activeIndex }) return activeIndex
    return (eligible.firstOrNull { (_, c) -> c.isWifi } ?: eligible.firstOrNull())?.index
}

/**
 * Returns the underlying physical network (WiFi or cellular) regardless of VPN state.
 * When a VPN is active, [ConnectivityManager.getActiveNetwork] returns the VPN interface,
 * which has no SIGNAL_STRENGTH or WiFi RSSI. Use this helper for any UI that needs to
 * reflect the physical link (signal bars, SSID, network type badge).
 */
fun getPhysicalNetwork(cm: ConnectivityManager): Network? {
    val networks = cm.allNetworks
    val snapshots = networks.map { network ->
        val caps = cm.getNetworkCapabilities(network)
        TransportSnapshot(
            isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
        )
    }
    val activeIndex = cm.activeNetwork?.let { active -> networks.indexOf(active).takeIf { it >= 0 } }
    return selectPhysicalIndex(snapshots, activeIndex)?.let { networks[it] }
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
