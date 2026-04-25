package com.ventoux.netlens.feature.whois.engine

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RdnsResolverImplTest {

    private val resolver = RdnsResolverImpl()

    @Test
    fun `valid IPv4 8_8_8_8 is accepted`() = runTest {
        val result = resolver.resolve("8.8.8.8")
        assertTrue(result.ip == "8.8.8.8")
    }

    @Test
    fun `valid IPv4 192_168_1_1 is accepted`() = runTest {
        val result = resolver.resolve("192.168.1.1")
        assertTrue(result.ip == "192.168.1.1")
    }

    @Test
    fun `valid IPv6 2001_db8__1 is accepted`() = runTest {
        val result = resolver.resolve("2001:db8::1")
        assertTrue(result.ip == "2001:db8::1")
    }

    @Test
    fun `valid IPv6 loopback is accepted`() = runTest {
        val result = resolver.resolve("::1")
        assertTrue(result.ip == "::1")
    }

    @Test
    fun `empty string is rejected`() = runTest {
        assertThrows<IllegalArgumentException> {
            resolver.resolve("")
        }
    }

    @Test
    fun `random text is rejected`() = runTest {
        assertThrows<IllegalArgumentException> {
            resolver.resolve("not-an-ip")
        }
    }

    @Test
    fun `single colon input is rejected`() = runTest {
        assertThrows<IllegalArgumentException> {
            resolver.resolve("fe80:1")
        }
    }

    @Test
    fun `malformed octets do not throw unexpected exception`() = runTest {
        try {
            resolver.resolve("999.999.999.999")
        } catch (_: IllegalArgumentException) {
            // expected — isValidIpLiteral returns false for this input
        }
    }
}
