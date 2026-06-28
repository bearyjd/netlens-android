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
}
