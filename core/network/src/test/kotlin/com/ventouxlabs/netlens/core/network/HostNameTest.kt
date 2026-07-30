package com.ventouxlabs.netlens.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HostNameTest {

    @Test
    fun `ordinary hostnames and ipv4 literals survive`() {
        listOf("nas", "nas.local", "printer-1.lan", "192.168.1.10", "example.com.").forEach {
            assertEquals(it, HostName.sanitize(it), "$it should be accepted unchanged")
        }
    }

    @Test
    fun `underscores are accepted because LAN names are full of them`() {
        // The regression this fixes: `my_nas` is illegal in public DNS but ordinary on a LAN and
        // on mDNS, and rejecting it hid a host that works.
        listOf("my_nas", "_smb._tcp.local", "media_server.home", "a_b-c_d").forEach {
            assertEquals(it, HostName.sanitize(it), "$it should be accepted unchanged")
        }
    }

    @Test
    fun `a host that would escape the authority is refused`() {
        listOf(
            "192.168.1.1@evil.example", // userinfo — a browser loads evil.example
            "192.168.1.1/../x",
            "host?q=1",
            "host#frag",
            "evil.example\\@x",
            "host with spaces",
            "",
            "   ",
        ).forEach {
            assertNull(HostName.sanitize(it), "$it should be refused")
        }
    }

    @Test
    fun `an ipv6 scope id is dropped rather than left to break the uri`() {
        assertEquals("fe80::1", HostName.sanitize("fe80::1%wlan0"))
    }

    @Test
    fun `a percent in a DNS name is refused, not silently truncated`() {
        // Dropping everything after '%' is only correct for an IPv6 scope id. Applied to a name it
        // would rewrite "nas%00.local" into a different host, "nas", and hand that back as if it
        // were what was asked for — a silent substitution is worse than a refusal.
        assertNull(HostName.sanitize("nas%00.local"))
        assertNull(HostName.sanitize("evil%2f.example"))
    }

    @Test
    fun `a name that happens to spell hex is still refused, not read as a scoped ipv6`() {
        // The guard above tests "nas", which is refused for an incidental reason: 'n' and 's' are
        // not hex digits. The scope check must key on the colon an IPv6 literal always has, or
        // every name whose first label is hex-shaped gets silently truncated to that label —
        // "cafe%evil.example" -> "cafe" is exactly the substitution this is supposed to prevent.
        listOf(
            "dead%00.local",
            "cafe%evil.example",
            "dad%wlan0",
            "1234%anything",
            "beef%25eth0",
        ).forEach {
            assertNull(HostName.sanitize(it), "$it should be refused")
        }
        // An IPv4 literal has no scope id either — dotted quads are not link-local.
        assertNull(HostName.sanitize("10.0.0.1%eth0"))
    }

    @Test
    fun `a real scoped ipv6 still survives, with and without brackets`() {
        assertEquals("fe80::1", HostName.sanitize("fe80::1%wlan0"))
        assertEquals("fe80::1", HostName.sanitize("[fe80::1%wlan0]"))
        assertEquals("[fe80::1]", HostName.toAuthority("fe80::1%eth0"))
    }

    @Test
    fun `sanitize leaves ipv6 bare and toAuthority brackets it`() {
        assertEquals("fe80::1", HostName.sanitize("fe80::1"))
        assertEquals("[fe80::1]", HostName.toAuthority("fe80::1"))
        // An already-bracketed literal must not come back double-bracketed.
        assertEquals("[fe80::1]", HostName.toAuthority("[fe80::1]"))
    }

    @Test
    fun `toAuthority leaves names and ipv4 unbracketed`() {
        assertEquals("nas.local", HostName.toAuthority("nas.local"))
        assertEquals("192.168.1.10", HostName.toAuthority("192.168.1.10"))
        assertNull(HostName.toAuthority("192.168.1.1@evil.example"))
    }
}
