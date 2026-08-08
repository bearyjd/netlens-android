package com.ventouxlabs.netlens.core.scan.engine

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `isSafeLocationUrl` decides whether the app will make an HTTP request to a URL supplied by
 * whoever answered an M-SEARCH on the LAN. It had **no tests at all** before this file, because
 * the previous version called `InetAddress.getByName` and so could not be exercised without DNS.
 * Binding the URL to the responder made it pure, which is what makes these possible.
 *
 * The two attacks being pinned shut:
 *
 *  - **DNS rebinding.** Old code resolved to validate, then `openConnection` resolved again to
 *    connect. Any hostname is now rejected outright, so there is no second resolution to differ.
 *  - **Cross-host SSRF.** Nothing tied the URL to the responder, so `192.168.1.50` could point
 *    the app at `192.168.1.1/admin`. Now the host must be the responder itself.
 */
class SsdpLocationUrlTest {

    @Test
    fun `the responder's own address is fetchable`() {
        assertTrue(safe("http://192.168.1.50:49152/desc.xml", "192.168.1.50"))
        assertTrue(safe("https://192.168.1.50/desc.xml", "192.168.1.50"))
        assertTrue(safe("http://192.168.1.50/", "192.168.1.50"))
    }

    @Test
    fun `a different LAN host is rejected — cross-host SSRF`() {
        // The attack the old guard allowed: it was neither loopback nor link-local, so it passed.
        assertFalse(safe("http://192.168.1.1/admin", "192.168.1.50"))
        assertFalse(safe("http://10.0.0.1/", "192.168.1.50"))
        assertFalse(safe("http://192.168.1.5/desc.xml", "192.168.1.50"))
    }

    @Test
    fun `a hostname is rejected outright — no second resolution to rebind`() {
        assertFalse(safe("http://evil.example/desc.xml", "192.168.1.50"))
        assertFalse(safe("http://localhost/desc.xml", "192.168.1.50"))
        assertFalse(safe("http://router.local/desc.xml", "192.168.1.50"))
        // Even a name that would resolve to the responder: we cannot verify it without DNS,
        // and resolving is the thing being removed.
        assertFalse(safe("http://my-nas/desc.xml", "192.168.1.50"))
    }

    @Test
    fun `loopback and link-local are rejected even when they ARE the responder`() {
        // This test previously read "...unless they are the responder" and asserted only the
        // two cases below, which encoded a REGRESSION as if it were intended behaviour: the
        // rewrite had dropped the unconditional loopback/link-local rejection in favour of the
        // responder match alone. UDP source addresses are forgeable, so a match proves nothing
        // about the destination. Both halves are asserted now.
        assertFalse(safe("http://127.0.0.1/desc.xml", "192.168.1.50"))
        assertFalse(safe("http://169.254.1.1/desc.xml", "192.168.1.50"))

        // The cases the old name waved through:
        assertFalse(safe("http://127.0.0.1/desc.xml", "127.0.0.1"))
        assertFalse(safe("http://[::1]/desc.xml", "::1"))
        assertFalse(safe("http://169.254.1.1/desc.xml", "169.254.1.1"))
        assertFalse(safe("http://[fe80::1]/desc.xml", "fe80::1"))
    }

    @Test
    fun `wildcard and multicast targets are rejected even when they match`() {
        assertFalse(safe("http://0.0.0.0/desc.xml", "0.0.0.0"))
        assertFalse(safe("http://239.255.255.250/desc.xml", "239.255.255.250"))
    }

    @Test
    fun `a hostname is not parsed as a literal, so no DNS lookup can happen`() {
        // parseIpLiteral must reject before getByName, otherwise a hostname triggers a blocking
        // DNS call inside a security predicate — the thing this rewrite removed.
        assertFalse(safe("http://localhost/desc.xml", "localhost"))
        assertFalse(safe("http://example.com/desc.xml", "example.com"))
        assertFalse(safe("http://999.999.999.999/d.xml", "999.999.999.999"))
    }

    @Test
    fun `non-http schemes are rejected`() {
        for (url in listOf(
            "file:///etc/passwd",
            "ftp://192.168.1.50/x",
            "jar:http://192.168.1.50/x!/",
            "gopher://192.168.1.50/",
            "//192.168.1.50/desc.xml",
            "192.168.1.50/desc.xml",
        )) {
            assertFalse(safe(url, "192.168.1.50"), "should reject: $url")
        }
    }

    @Test
    fun `scheme matching is case-insensitive`() {
        assertTrue(safe("HTTP://192.168.1.50/d.xml", "192.168.1.50"))
        assertTrue(safe("HtTpS://192.168.1.50/d.xml", "192.168.1.50"))
    }

    @Test
    fun `malformed and empty urls are rejected without throwing`() {
        for (url in listOf("", "   ", "http://", "http:///desc.xml", "http://:80/", "h ttp://x")) {
            val result = runCatching { SsdpScannerImpl.isSafeLocationUrl(url, "192.168.1.50") }
            assertTrue(result.isSuccess, "threw on \"$url\": ${result.exceptionOrNull()}")
            assertFalse(result.getOrThrow(), "should reject: \"$url\"")
        }
    }

    @Test
    fun `userinfo cannot smuggle a different host past the check`() {
        // "http://192.168.1.50@evil.example/" has host evil.example, not the responder.
        assertFalse(safe("http://192.168.1.50@evil.example/desc.xml", "192.168.1.50"))
        assertFalse(safe("http://192.168.1.50:pass@10.0.0.1/", "192.168.1.50"))
    }

    @Test
    fun `IPv6 compares equal across brackets and zone ids`() {
        // Deliberately a unique-local address (fd00::/8), NOT fe80:: — this test used to use a
        // link-local example, which the loopback/link-local rejection now correctly refuses.
        // A test asserting a special address is fetchable is a test asserting a bug.
        assertTrue(safe("http://[fd00::1]/desc.xml", "fd00::1"))
        assertTrue(safe("http://[fd00::1]:8080/desc.xml", "fd00::1%wlan0"))
        assertFalse(safe("http://[fd00::2]/desc.xml", "fd00::1"))
    }

    private fun safe(url: String, responderIp: String) =
        SsdpScannerImpl.isSafeLocationUrl(url, responderIp)
}
