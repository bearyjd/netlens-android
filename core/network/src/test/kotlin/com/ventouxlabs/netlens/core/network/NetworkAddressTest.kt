package com.ventouxlabs.netlens.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NetworkAddressTest {

    @Test
    fun `slash 24 masks to network boundary`() {
        assertEquals("192.168.1.0", calculateNetworkAddress("192.168.1.100", 24))
    }

    @Test
    fun `slash 24 with zero host`() {
        assertEquals("192.168.1.0", calculateNetworkAddress("192.168.1.0", 24))
    }

    @Test
    fun `slash 8 masks to class A boundary`() {
        assertEquals("10.0.0.0", calculateNetworkAddress("10.0.0.5", 8))
    }

    @Test
    fun `slash 16 masks to class B boundary`() {
        assertEquals("172.16.0.0", calculateNetworkAddress("172.16.5.130", 16))
    }

    @Test
    fun `slash 28 masks to non-octet boundary`() {
        assertEquals("172.16.5.128", calculateNetworkAddress("172.16.5.130", 28))
    }

    @Test
    fun `slash 32 returns same address`() {
        assertEquals("192.168.1.1", calculateNetworkAddress("192.168.1.1", 32))
    }

    @Test
    fun `slash 0 returns all zeros`() {
        assertEquals("0.0.0.0", calculateNetworkAddress("192.168.1.1", 0))
    }

    @Test
    fun `invalid ip returns input unchanged`() {
        assertEquals("not-an-ip", calculateNetworkAddress("not-an-ip", 24))
    }

    @Test
    fun `slash 20 masks correctly`() {
        assertEquals("10.0.16.0", calculateNetworkAddress("10.0.31.254", 20))
    }
}
