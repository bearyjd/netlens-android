package com.ventoux.netlens.core.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SsrfGuardTest {

    @Test
    fun `localhost returns true`() {
        assertTrue(SsrfGuard.isPrivateOrLoopback("localhost"))
    }

    @Test
    fun `localhost is case insensitive`() {
        assertTrue(SsrfGuard.isPrivateOrLoopback("LOCALHOST"))
        assertTrue(SsrfGuard.isPrivateOrLoopback("LocalHost"))
    }

    @Test
    fun `127_0_0_1 loopback returns true`() {
        assertTrue(SsrfGuard.isPrivateOrLoopback("127.0.0.1"))
    }

    @Test
    fun `10_0_0_1 RFC1918 returns true`() {
        assertTrue(SsrfGuard.isPrivateOrLoopback("10.0.0.1"))
    }

    @Test
    fun `192_168_1_1 RFC1918 returns true`() {
        assertTrue(SsrfGuard.isPrivateOrLoopback("192.168.1.1"))
    }

    @Test
    fun `172_16_0_1 RFC1918 returns true`() {
        assertTrue(SsrfGuard.isPrivateOrLoopback("172.16.0.1"))
    }

    @Test
    fun `IPv6 loopback returns true`() {
        assertTrue(SsrfGuard.isPrivateOrLoopback("::1"))
    }

    @Test
    fun `unresolvable host returns true`() {
        assertTrue(SsrfGuard.isPrivateOrLoopback("this.host.does.not.exist.invalid"))
    }

    @Test
    fun `public IP 8_8_8_8 returns false`() {
        assertFalse(SsrfGuard.isPrivateOrLoopback("8.8.8.8"))
    }

    @Test
    fun `link-local 169_254_1_1 returns true`() {
        assertTrue(SsrfGuard.isPrivateOrLoopback("169.254.1.1"))
    }
}
