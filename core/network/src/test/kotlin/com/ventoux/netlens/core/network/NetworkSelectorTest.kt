package com.ventoux.netlens.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetworkSelectorTest {

    @Test
    fun `no networks returns None`() {
        assertEquals(VpnState.None, detectVpnStateFromSnapshots(emptyList()))
    }

    @Test
    fun `only physical network returns None`() {
        val snaps = listOf(
            NetworkCapsSnapshot(hasVpn = false, hasInternet = true, hasValidated = true, hasWifiOrCellular = true),
        )
        assertEquals(VpnState.None, detectVpnStateFromSnapshots(snaps))
    }

    @Test
    fun `vpn alone returns FullTunnel`() {
        val snaps = listOf(
            NetworkCapsSnapshot(hasVpn = true, hasInternet = true, hasValidated = true, hasWifiOrCellular = false),
        )
        assertEquals(VpnState.FullTunnel, detectVpnStateFromSnapshots(snaps))
    }

    @Test
    fun `vpn plus validated wifi returns SplitTunnel`() {
        val snaps = listOf(
            NetworkCapsSnapshot(hasVpn = true, hasInternet = true, hasValidated = true, hasWifiOrCellular = false),
            NetworkCapsSnapshot(hasVpn = false, hasInternet = true, hasValidated = true, hasWifiOrCellular = true),
        )
        assertEquals(VpnState.SplitTunnel, detectVpnStateFromSnapshots(snaps))
    }

    @Test
    fun `vpn plus unvalidated wifi returns FullTunnel`() {
        val snaps = listOf(
            NetworkCapsSnapshot(hasVpn = true, hasInternet = true, hasValidated = true, hasWifiOrCellular = false),
            NetworkCapsSnapshot(hasVpn = false, hasInternet = true, hasValidated = false, hasWifiOrCellular = true),
        )
        assertEquals(VpnState.FullTunnel, detectVpnStateFromSnapshots(snaps))
    }

    @Test
    fun `vpn plus non-physical internet returns FullTunnel`() {
        val snaps = listOf(
            NetworkCapsSnapshot(hasVpn = true, hasInternet = true, hasValidated = true, hasWifiOrCellular = false),
            NetworkCapsSnapshot(hasVpn = false, hasInternet = true, hasValidated = true, hasWifiOrCellular = false),
        )
        assertEquals(VpnState.FullTunnel, detectVpnStateFromSnapshots(snaps))
    }
}
