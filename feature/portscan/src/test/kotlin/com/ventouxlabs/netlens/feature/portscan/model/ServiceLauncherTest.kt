package com.ventouxlabs.netlens.feature.portscan.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ServiceLauncherTest {

    private val host = "192.168.1.42"

    @Test
    fun `http on the default port omits it from the url`() {
        val launch = ServiceLauncher.forPort(host, 80)
        assertEquals("http://192.168.1.42", launch?.uri)
        assertEquals(ServiceLaunchKind.WEB, launch?.kind)
    }

    @Test
    fun `https on the default port omits it from the url`() {
        assertEquals("https://192.168.1.42", ServiceLauncher.forPort(host, 443)?.uri)
    }

    @Test
    fun `non-standard web ports are kept in the url`() {
        assertEquals("http://192.168.1.42:8080", ServiceLauncher.forPort(host, 8080)?.uri)
        assertEquals("https://192.168.1.42:8443", ServiceLauncher.forPort(host, 8443)?.uri)
    }

    @Test
    fun `remote-access ports map to their own schemes`() {
        assertEquals("ssh://192.168.1.42", ServiceLauncher.forPort(host, 22)?.uri)
        assertEquals(ServiceLaunchKind.REMOTE, ServiceLauncher.forPort(host, 22)?.kind)
        assertEquals("vnc://192.168.1.42", ServiceLauncher.forPort(host, 5900)?.uri)
        assertEquals("rdp://192.168.1.42", ServiceLauncher.forPort(host, 3389)?.uri)
    }

    @Test
    fun `file-sharing ports map to file schemes`() {
        assertEquals("smb://192.168.1.42", ServiceLauncher.forPort(host, 445)?.uri)
        assertEquals(ServiceLaunchKind.FILES, ServiceLauncher.forPort(host, 445)?.kind)
        assertEquals("ftp://192.168.1.42", ServiceLauncher.forPort(host, 21)?.uri)
    }

    @Test
    fun `ports with nothing to hand off to are not launchable`() {
        // A database or a cache has no URI scheme a phone can open — offering a browser here
        // would just produce a blank tab.
        assertNull(ServiceLauncher.forPort(host, 3306))
        assertNull(ServiceLauncher.forPort(host, 5432))
        assertNull(ServiceLauncher.forPort(host, 11211))
        assertNull(ServiceLauncher.forPort(host, 53))
    }

    @Test
    fun `ipv6 literals are bracketed in the authority`() {
        val launch = ServiceLauncher.forPort("fe80::1", 8080)
        assertEquals("http://[fe80::1]:8080", launch?.uri)
        assertEquals("ssh://[fe80::1]", ServiceLauncher.forPort("fe80::1", 22)?.uri)
    }

    @Test
    fun `printer admin port serves plain http on the same port`() {
        assertEquals("http://192.168.1.42:631", ServiceLauncher.forPort(host, 631)?.uri)
    }

    @Test
    fun `a host that would escape the authority is refused`() {
        // A device on the LAN chooses its own mDNS/NetBIOS name, and a DNS lookup result can be
        // routed into the port scanner — so these are attacker-supplied strings, not typos.
        // Each of these would otherwise send the user somewhere other than the scanned host.
        listOf(
            "192.168.1.1@evil.example",  // userinfo — browser loads evil.example
            "evil.example/x#",           // path + fragment swallows the ":8080"
            "evil.example?a=b",          // query
            "evil.example\\@x",          // backslash, which some parsers fold to '/'
            "192.168.1.1 evil.example",  // whitespace
            "javascript:alert(1)",       // scheme-ish input
            "",
            "   ",
        ).forEach { hostile ->
            assertNull(ServiceLauncher.forPort(hostile, 8080), "should refuse: $hostile")
            assertNull(ServiceLauncher.forPort(hostile, 22), "should refuse: $hostile")
        }
    }

    @Test
    fun `ordinary hostnames and ipv4 literals are still accepted`() {
        assertEquals("http://nas.local:8080", ServiceLauncher.forPort("nas.local", 8080)?.uri)
        assertEquals("http://NAS-01:8080", ServiceLauncher.forPort("NAS-01", 8080)?.uri)
        assertEquals("http://host.example.com.:8080", ServiceLauncher.forPort("host.example.com.", 8080)?.uri)
        assertEquals("http://192.168.1.42:8080", ServiceLauncher.forPort(host, 8080)?.uri)
    }

    @Test
    fun `an ipv6 scope id is dropped rather than left to break the uri`() {
        // "%wlan0" is an invalid percent-escape in an authority, and the scope means nothing to
        // whichever app handles the intent.
        assertEquals("ssh://[fe80::1]", ServiceLauncher.forPort("fe80::1%wlan0", 22)?.uri)
    }

    @Test
    fun `an already-bracketed ipv6 literal is not double-bracketed`() {
        assertEquals("http://[fe80::1]:8080", ServiceLauncher.forPort("[fe80::1]", 8080)?.uri)
    }
}
