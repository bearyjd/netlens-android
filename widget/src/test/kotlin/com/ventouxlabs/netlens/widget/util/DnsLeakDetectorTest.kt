package com.ventouxlabs.netlens.widget.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DnsLeakDetectorTest {

    @Test
    fun `VPN active with private DNS is leaking`() {
        assertTrue(
            DnsLeakDetector.isLeaking(
                dnsServers = listOf("192.168.1.1"),
                isVpn = true,
                vpnInterfaceName = "tun0",
                gatewayIp = null,
            ),
        )
    }

    @Test
    fun `VPN active with public DNS is leaking`() {
        assertTrue(
            DnsLeakDetector.isLeaking(
                dnsServers = listOf("8.8.8.8"),
                isVpn = true,
                vpnInterfaceName = "wg0",
                gatewayIp = null,
            ),
        )
    }

    @Test
    fun `VPN active with gateway DNS is leaking`() {
        assertTrue(
            DnsLeakDetector.isLeaking(
                dnsServers = listOf("10.0.1.1"),
                isVpn = true,
                vpnInterfaceName = "tun0",
                gatewayIp = "10.0.1.1",
            ),
        )
    }

    @Test
    fun `VPN active with tunnel-provided DNS is not leaking`() {
        assertFalse(
            DnsLeakDetector.isLeaking(
                dnsServers = listOf("10.8.0.1"),
                isVpn = true,
                vpnInterfaceName = "tun0",
                gatewayIp = null,
            ),
        )
    }

    @Test
    fun `no VPN is never leaking`() {
        assertFalse(
            DnsLeakDetector.isLeaking(
                dnsServers = listOf("8.8.8.8"),
                isVpn = false,
                vpnInterfaceName = "",
                gatewayIp = null,
            ),
        )
    }

    @Test
    fun `empty DNS list with VPN is not leaking`() {
        assertFalse(
            DnsLeakDetector.isLeaking(
                dnsServers = emptyList(),
                isVpn = true,
                vpnInterfaceName = "tun0",
                gatewayIp = null,
            ),
        )
    }

    @Test
    fun `VPN active with empty interface name is not leaking`() {
        assertFalse(
            DnsLeakDetector.isLeaking(
                dnsServers = listOf("192.168.1.1"),
                isVpn = true,
                vpnInterfaceName = "",
                gatewayIp = null,
            ),
        )
    }

    @Test
    fun `VPN active with 172 dot 20 private DNS is leaking`() {
        assertTrue(
            DnsLeakDetector.isLeaking(
                dnsServers = listOf("172.20.0.1"),
                isVpn = true,
                vpnInterfaceName = "tun0",
                gatewayIp = null,
            ),
        )
    }

    @Test
    fun `routing mode without VPN is DIRECT`() {
        assertEquals("DIRECT", DnsLeakDetector.detectRoutingMode(isVpn = false, hasVpnDefaultRoute = false))
    }

    @Test
    fun `routing mode with VPN default route is VPN_FULL`() {
        assertEquals("VPN_FULL", DnsLeakDetector.detectRoutingMode(isVpn = true, hasVpnDefaultRoute = true))
    }

    @Test
    fun `routing mode with VPN but no default route is VPN_SPLIT`() {
        assertEquals("VPN_SPLIT", DnsLeakDetector.detectRoutingMode(isVpn = true, hasVpnDefaultRoute = false))
    }
}
