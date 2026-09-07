package com.ventouxlabs.netlens.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetworkSelectorTest {

    @Test
    fun `no vpn snapshot returns None`() {
        assertEquals(VpnState.None, detectVpnStateFromSnapshot(null))
    }

    @Test
    fun `vpn with default route returns FullTunnel`() {
        val snap = VpnNetworkSnapshot(hasDefaultRoute = true)
        assertEquals(VpnState.FullTunnel, detectVpnStateFromSnapshot(snap))
    }

    @Test
    fun `vpn without default route returns SplitTunnel`() {
        val snap = VpnNetworkSnapshot(hasDefaultRoute = false)
        assertEquals(VpnState.SplitTunnel, detectVpnStateFromSnapshot(snap))
    }

    private fun vpn() = TransportSnapshot(isVpn = true, isWifi = false, isCellular = false)
    private fun wifi() = TransportSnapshot(isVpn = false, isWifi = true, isCellular = false)
    private fun cell() = TransportSnapshot(isVpn = false, isWifi = false, isCellular = true)
    private fun none() = TransportSnapshot(isVpn = false, isWifi = false, isCellular = false)

    // Regression: with a VPN active both radios stay up, and allNetworks order is an OS
    // implementation detail — on a Pixel 9 Pro Fold cellular enumerated first, so the widget
    // rendered cellular dBm (-114, red bars) while the phone sat on strong Wi-Fi.
    @Test
    fun `wifi wins over cellular regardless of enumeration order`() {
        assertEquals(2, selectPhysicalIndex(listOf(vpn(), cell(), wifi())))
        assertEquals(1, selectPhysicalIndex(listOf(vpn(), wifi(), cell())))
    }

    @Test
    fun `cellular is selected when it is the only physical transport`() {
        assertEquals(1, selectPhysicalIndex(listOf(vpn(), cell())))
    }

    @Test
    fun `vpn-only or empty enumerations select nothing`() {
        assertEquals(null, selectPhysicalIndex(listOf(vpn(), none())))
        assertEquals(null, selectPhysicalIndex(emptyList()))
    }

    // The OS's own routing decision is authoritative: a user genuinely on cellular while
    // a Wi-Fi network idles in the list must not be shown that Wi-Fi network's data.
    @Test
    fun `an active physical network wins even over an idle wifi network`() {
        assertEquals(1, selectPhysicalIndex(listOf(wifi(), cell()), activeIndex = 1))
    }

    @Test
    fun `an active vpn does not suppress the wifi preference`() {
        assertEquals(2, selectPhysicalIndex(listOf(vpn(), cell(), wifi()), activeIndex = 0))
    }
}
