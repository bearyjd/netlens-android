package com.ventouxlabs.netlens.feature.dnsleak

import com.ventouxlabs.netlens.feature.dnsleak.engine.DnsLeakDetectorImpl
import com.ventouxlabs.netlens.feature.dnsleak.model.ResolverInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DnsLeakDetectorTest {

    @Test
    fun `classifyResolver identifies Google DNS as public`() {
        val result = DnsLeakDetectorImpl.classifyResolver("8.8.8.8")
        assertEquals("Google DNS", result.name)
        assertTrue(result.isKnownPublic)
    }

    @Test
    fun `classifyResolver identifies Cloudflare DNS as public`() {
        val result = DnsLeakDetectorImpl.classifyResolver("1.1.1.1")
        assertEquals("Cloudflare DNS", result.name)
        assertTrue(result.isKnownPublic)
    }

    @Test
    fun `classifyResolver identifies Quad9 DNS as public`() {
        val result = DnsLeakDetectorImpl.classifyResolver("9.9.9.9")
        assertEquals("Quad9 DNS", result.name)
        assertTrue(result.isKnownPublic)
    }

    @Test
    fun `classifyResolver identifies OpenDNS as public`() {
        val result = DnsLeakDetectorImpl.classifyResolver("208.67.222.222")
        assertEquals("OpenDNS", result.name)
        assertTrue(result.isKnownPublic)
    }

    @Test
    fun `classifyResolver identifies private IP as non-public`() {
        val result = DnsLeakDetectorImpl.classifyResolver("192.168.1.1")
        assertEquals("Private/Local DNS", result.name)
        assertFalse(result.isKnownPublic)
    }

    @Test
    fun `classifyResolver identifies 10-dot range as private`() {
        val result = DnsLeakDetectorImpl.classifyResolver("10.0.0.1")
        assertEquals("Private/Local DNS", result.name)
        assertFalse(result.isKnownPublic)
    }

    @Test
    fun `classifyResolver identifies 172-16 range as private`() {
        val result = DnsLeakDetectorImpl.classifyResolver("172.16.0.1")
        assertEquals("Private/Local DNS", result.name)
        assertFalse(result.isKnownPublic)
    }

    @Test
    fun `classifyResolver identifies localhost as private`() {
        val result = DnsLeakDetectorImpl.classifyResolver("127.0.0.1")
        assertEquals("Private/Local DNS", result.name)
        assertFalse(result.isKnownPublic)
    }

    @Test
    fun `classifyResolver identifies unknown public IP as ISP`() {
        val result = DnsLeakDetectorImpl.classifyResolver("203.0.113.50")
        assertFalse(result.isKnownPublic)
        assertEquals("203.0.113.50", result.ip)
    }

    @Test
    fun `classifyResolver identifies IPv6 Google DNS as public`() {
        val result = DnsLeakDetectorImpl.classifyResolver("2001:4860:4860::8888")
        assertEquals("Google DNS", result.name)
        assertTrue(result.isKnownPublic)
    }

    @Test
    fun `classifyResolver identifies IPv6 Cloudflare DNS as public`() {
        val result = DnsLeakDetectorImpl.classifyResolver("2606:4700:4700::1111")
        assertEquals("Cloudflare DNS", result.name)
        assertTrue(result.isKnownPublic)
    }

    @Test
    fun `classifyResolver identifies AdGuard DNS as public`() {
        val result = DnsLeakDetectorImpl.classifyResolver("94.140.14.14")
        assertEquals("AdGuard DNS", result.name)
        assertTrue(result.isKnownPublic)
    }
}
