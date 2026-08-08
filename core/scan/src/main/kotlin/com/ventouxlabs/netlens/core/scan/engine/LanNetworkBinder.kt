package com.ventouxlabs.netlens.core.scan.engine

/**
 * Runs a block with the process pinned to the local-network transport (Wi-Fi or Ethernet).
 *
 * **Why this exists.** Android binds an app's sockets to its *default* network, and when any VPN
 * is up that default is the VPN — including for on-device ad-blockers and private-DNS apps
 * (Blokada, NextDNS, AdGuard, RethinkDNS), which are all `VpnService` implementations. Every LAN
 * probe then goes into the tunnel. A tunnel that does not carry the local subnet drops them, and
 * because `InetAddress.isReachable` reports a dropped probe as `false` rather than raising, a
 * whole `/24` sweep completes "successfully" and finds nothing. Measured on 2026-08-08: the same
 * subnet returned 0 devices with Tailscale up and 5 with it down (issue #152).
 *
 * **Why the binding is process-wide rather than per-socket.** `InetAddress.isReachable` accepts no
 * `Network`, so its ICMP probe cannot be bound individually. Rewriting liveness as a bindable
 * TCP connect would narrow discovery to hosts with an open port — two of the five devices found
 * in that measurement answered ICMP only — so it trades one silent under-report for another.
 * `bindProcessToNetwork` is the only mechanism that covers `isReachable` *and* the SSDP, mDNS and
 * NetBIOS engines, which reach the LAN through unbound sockets of their own.
 *
 * **The cost, stated plainly:** the binding applies to the whole process for the duration of the
 * block, so any unrelated network work running concurrently (endpoint monitor, widget refresh) is
 * pinned to the LAN transport too, and will fail if it needed the VPN. A scan is bounded and
 * short, and the previous binding is always restored, but this is a real trade rather than a free
 * fix. Keep the block as small as the scan itself.
 *
 * A caution for anyone diagnosing this class of bug: `ip route get <lan-ip> uid <app-uid>` reports
 * the Wi-Fi interface even while the app's sockets are bound to the VPN. It models an *unbound*
 * socket falling through to the Wi-Fi table, not the network the app actually uses, and reading it
 * as proof that traffic bypasses the tunnel sends you the wrong way.
 */
interface LanNetworkBinder {

    /**
     * Binds the process to a non-VPN local network, runs [block], then restores the previous
     * binding — including when [block] throws.
     *
     * Falls back to running [block] unbound when no local network is available, so a scan on a
     * device with no Wi-Fi behaves exactly as it did before rather than failing outright. That
     * fallback is why [block] receives whether the binding actually happened: an unbound run whose
     * probes all came back negative proves nothing about the network, and the caller needs to be
     * able to say so rather than report an empty result as fact (issue #152).
     */
    suspend fun <T> withLanNetwork(block: suspend (bound: Boolean) -> T): T
}
