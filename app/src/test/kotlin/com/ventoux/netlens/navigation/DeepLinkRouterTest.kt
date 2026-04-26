package com.ventoux.netlens.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeepLinkRouterTest {

    @Test
    fun `resolveRoute returns null for wrong scheme`() {
        assertNull(resolveRoute("https", "feature", "ipinfo"))
    }

    @Test
    fun `resolveRoute returns null for wrong host`() {
        assertNull(resolveRoute("netlens", "settings", "ipinfo"))
    }

    @Test
    fun `resolveRoute returns null for null path`() {
        assertNull(resolveRoute("netlens", "feature", null))
    }

    @Test
    fun `resolveRoute returns null for unknown path`() {
        assertNull(resolveRoute("netlens", "feature", "nonexistent"))
    }

    @Test
    fun `resolveRoute maps home`() {
        assertEquals("home", resolveRoute("netlens", "feature", "home"))
    }

    @Test
    fun `resolveRoute maps existing tool paths`() {
        val knownPaths = mapOf(
            "ipinfo" to ToolDestination.IpInfo.route,
            "ping" to ToolDestination.Ping.route,
            "dns" to ToolDestination.Dns.route,
            "lanscan" to ToolDestination.LanScan.route,
            "portscan" to ToolDestination.PortScan.route,
            "traceroute" to ToolDestination.Traceroute.route,
            "whois" to ToolDestination.Whois.route,
            "tls" to ToolDestination.Tls.route,
            "httptester" to ToolDestination.HttpTester.route,
            "wol" to ToolDestination.Wol.route,
            "monitor" to ToolDestination.Monitor.route,
            "netlog" to ToolDestination.NetLog.route,
            "widgetsettings" to ToolDestination.WidgetSettings.route,
        )
        for ((path, expectedRoute) in knownPaths) {
            assertEquals(expectedRoute, resolveRoute("netlens", "feature", path), "path=$path")
        }
    }

    @Test
    fun `resolveRoute maps unimplemented widget paths to fallbacks`() {
        assertEquals("home", resolveRoute("netlens", "feature", "posture"))
        assertEquals("home", resolveRoute("netlens", "feature", "wifiaudit"))
        assertEquals("home", resolveRoute("netlens", "feature", "speedtest"))
        assertEquals("home", resolveRoute("netlens", "feature", "scan"))
    }

    @Test
    fun `resolveRoute maps latency to ping`() {
        assertEquals(
            ToolDestination.Ping.route,
            resolveRoute("netlens", "feature", "latency"),
        )
    }

    @Test
    fun `resolveRoute returns null for null scheme`() {
        assertNull(resolveRoute(null, "feature", "ipinfo"))
    }

    @Test
    fun `resolveRoute returns null for null host`() {
        assertNull(resolveRoute("netlens", null, "ipinfo"))
    }
}
