package com.ventouxlabs.netlens.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToolQueryTest {

    @Test
    fun `host routes pass an ordinary host through untouched`() {
        listOf("ping", "traceroute", "whois", "dns", "tls", "portscan").forEach { route ->
            assertEquals("192.168.1.10", ToolQuery.sanitize(route, "192.168.1.10"))
            assertEquals("my_nas.local", ToolQuery.sanitize(route, "my_nas.local"))
        }
    }

    @Test
    fun `host routes drop a host that would escape a uri`() {
        // A LAN device names itself, so this is attacker-controlled input arriving at another
        // tool's input field. Empty means "open the tool blank", not "navigate somewhere else".
        listOf("ping", "portscan", "tls").forEach { route ->
            assertEquals("", ToolQuery.sanitize(route, "192.168.1.1@evil.example"))
            assertEquals("", ToolQuery.sanitize(route, "192.168.1.1/../x"))
            assertEquals("", ToolQuery.sanitize(route, "host?q=1"))
        }
    }

    @Test
    fun `an ipv6 host keeps its scope stripped and stays bare for a host route`() {
        assertEquals("fe80::1", ToolQuery.sanitize("ping", "fe80::1%wlan0"))
    }

    @Test
    fun `the httptester url keeps its scheme port and path`() {
        assertEquals("http://nas.local:8080", ToolQuery.sanitize("httptester", "http://nas.local:8080"))
        assertEquals("https://example.com", ToolQuery.sanitize("httptester", "https://example.com"))
        assertEquals("http://nas.local:81/status", ToolQuery.sanitize("httptester", "http://nas.local:81/status"))
        assertEquals("http://[fe80::1]:8080", ToolQuery.sanitize("httptester", "http://[fe80::1]:8080"))
    }

    @Test
    fun `the httptester url is dropped when its host or shape is wrong`() {
        listOf(
            "http://192.168.1.1@evil.example", // userinfo
            "http://nas.local:80x",            // non-numeric port
            "ftp://nas.local",                 // not a scheme this tool speaks
            "nas.local",                       // no scheme at all
            "://nas.local",                    // empty scheme
        ).forEach {
            assertEquals("", ToolQuery.sanitize("httptester", it), "$it should be dropped")
        }
    }

    @Test
    fun `routes whose query is not a host are left alone`() {
        // LAN Scan takes a CIDR and Devices takes a free-text search; vetting either as a host
        // would break them.
        assertEquals("192.168.1.0/24", ToolQuery.sanitize("lanscan", "192.168.1.0/24"))
        assertEquals("living room tv", ToolQuery.sanitize("devices", "living room tv"))
    }
}
