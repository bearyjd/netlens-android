package com.ventouxlabs.netlens.navigation

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
    fun `resolveRoute maps widget paths to their real screens`() {
        assertEquals(ToolDestination.Posture.route, resolveRoute("netlens", "feature", "posture"))
        assertEquals(ToolDestination.WifiAudit.route, resolveRoute("netlens", "feature", "wifiaudit"))
        assertEquals(ToolDestination.SpeedTest.route, resolveRoute("netlens", "feature", "speedtest"))
        assertEquals(ToolDestination.LanScan.route, resolveRoute("netlens", "feature", "scan"))
    }

    @Test
    fun `resolveRoute maps every ToolDestination route to itself`() {
        // Guards against a new NavHost destination shipping without a deep-link path.
        for (destination in ToolDestination.entries) {
            assertEquals(
                destination.route,
                resolveRoute("netlens", "feature", destination.route),
                "ToolDestination.${destination.name} (path=${destination.route}) has no deep-link path",
            )
        }
    }

    @Test
    fun `resolveRoute maps destinations that were previously unreachable`() {
        assertEquals(ToolDestination.Mdns.route, resolveRoute("netlens", "feature", "mdns"))
        assertEquals(ToolDestination.WifiAnalyzer.route, resolveRoute("netlens", "feature", "wifi"))
        assertEquals(ToolDestination.CellTower.route, resolveRoute("netlens", "feature", "celltower"))
        assertEquals(ToolDestination.IpCalc.route, resolveRoute("netlens", "feature", "ipcalc"))
        assertEquals(ToolDestination.History.route, resolveRoute("netlens", "feature", "history"))
    }

    @Test
    fun `resolveRoute drops the query for routes without a query argument`() {
        // mdns/wifi/celltower/history are declared without "?query={query}" in the NavHost.
        for (path in listOf("mdns", "wifi", "celltower", "history")) {
            assertEquals(path, resolveRoute("netlens", "feature", path, "example.com"), "path=$path")
        }
    }

    @Test
    fun `resolveRoute maps settings`() {
        assertEquals(ToolDestination.Settings.route, resolveRoute("netlens", "feature", "settings"))
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
