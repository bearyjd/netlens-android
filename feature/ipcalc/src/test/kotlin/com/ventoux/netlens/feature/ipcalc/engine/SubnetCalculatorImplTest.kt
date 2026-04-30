package com.ventoux.netlens.feature.ipcalc.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SubnetCalculatorImplTest {

    private val calculator = SubnetCalculatorImpl()

    @Test
    fun `slash 24 calculates correctly`() {
        val result = calculator.calculate("192.168.1.0/24")
        assertEquals("192.168.1.0", result.networkAddress)
        assertEquals("192.168.1.255", result.broadcastAddress)
        assertEquals("192.168.1.1", result.firstHost)
        assertEquals("192.168.1.254", result.lastHost)
        assertEquals(254L, result.totalHosts)
        assertEquals("255.255.255.0", result.subnetMask)
        assertEquals("0.0.0.255", result.wildcardMask)
        assertEquals("192.168.1.0/24", result.cidrNotation)
        assertEquals("C", result.ipClass)
        assertTrue(result.isBogon)
    }

    @Test
    fun `slash 32 single host`() {
        val result = calculator.calculate("10.0.0.1/32")
        assertEquals("10.0.0.1", result.networkAddress)
        assertEquals("10.0.0.1", result.broadcastAddress)
        assertEquals("10.0.0.1", result.firstHost)
        assertEquals("10.0.0.1", result.lastHost)
        assertEquals(1L, result.totalHosts)
        assertEquals("255.255.255.255", result.subnetMask)
        assertEquals("0.0.0.0", result.wildcardMask)
    }

    @Test
    fun `slash 31 point to point`() {
        val result = calculator.calculate("10.0.0.0/31")
        assertEquals("10.0.0.0", result.networkAddress)
        assertEquals("10.0.0.1", result.broadcastAddress)
        assertEquals("10.0.0.0", result.firstHost)
        assertEquals("10.0.0.1", result.lastHost)
        assertEquals(2L, result.totalHosts)
    }

    @Test
    fun `slash 0 entire internet`() {
        val result = calculator.calculate("0.0.0.0/0")
        assertEquals("0.0.0.0", result.networkAddress)
        assertEquals("255.255.255.255", result.broadcastAddress)
        assertEquals("0.0.0.1", result.firstHost)
        assertEquals("255.255.255.254", result.lastHost)
        assertEquals(4294967294L, result.totalHosts)
        assertEquals("0.0.0.0", result.subnetMask)
        assertEquals("255.255.255.255", result.wildcardMask)
    }

    @Test
    fun `slash 8 class A`() {
        val result = calculator.calculate("10.0.0.0/8")
        assertEquals("10.0.0.0", result.networkAddress)
        assertEquals("10.255.255.255", result.broadcastAddress)
        assertEquals(16777214L, result.totalHosts)
        assertEquals("255.0.0.0", result.subnetMask)
        assertEquals("A", result.ipClass)
        assertTrue(result.isBogon)
    }

    @Test
    fun `slash 16 class B private`() {
        val result = calculator.calculate("172.16.0.0/16")
        assertEquals("172.16.0.0", result.networkAddress)
        assertEquals("172.16.255.255", result.broadcastAddress)
        assertEquals(65534L, result.totalHosts)
        assertEquals("B", result.ipClass)
        assertTrue(result.isBogon)
    }

    @Test
    fun `public IP is not bogon`() {
        val result = calculator.calculate("8.8.8.0/24")
        assertEquals("A", result.ipClass)
        assertFalse(result.isBogon)
    }

    @Test
    fun `bare IP defaults to slash 32`() {
        val result = calculator.calculate("192.168.1.100")
        assertEquals("192.168.1.100", result.networkAddress)
        assertEquals("192.168.1.100/32", result.cidrNotation)
        assertEquals(1L, result.totalHosts)
    }

    @Test
    fun `IP plus mask format`() {
        val result = calculator.calculate("192.168.1.0 255.255.255.0")
        assertEquals("192.168.1.0", result.networkAddress)
        assertEquals("192.168.1.255", result.broadcastAddress)
        assertEquals(254L, result.totalHosts)
        assertEquals("192.168.1.0/24", result.cidrNotation)
    }

    @Test
    fun `IP not aligned to network boundary`() {
        val result = calculator.calculate("192.168.1.50/24")
        assertEquals("192.168.1.0", result.networkAddress)
        assertEquals("192.168.1.255", result.broadcastAddress)
        assertEquals("192.168.1.0/24", result.cidrNotation)
    }

    @Test
    fun `invalid IP throws`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculate("not.an.ip")
        }
    }

    @Test
    fun `empty input throws`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculate("")
        }
    }

    @Test
    fun `IPv6 input rejected`() {
        val ex = assertThrows<IllegalArgumentException> {
            calculator.calculate("::1")
        }
        assertTrue(ex.message?.contains("IPv6") == true)
    }

    @Test
    fun `invalid CIDR prefix throws`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculate("10.0.0.0/33")
        }
    }

    @Test
    fun `invalid octet throws`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculate("256.0.0.1/24")
        }
    }

    @Test
    fun `non-contiguous mask throws`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculate("10.0.0.0 255.0.255.0")
        }
    }

    @Test
    fun `whitespace trimmed`() {
        val result = calculator.calculate("  10.0.0.0/8  ")
        assertEquals("10.0.0.0", result.networkAddress)
    }

    @Test
    fun `multicast is bogon`() {
        val result = calculator.calculate("224.0.0.1/32")
        assertEquals("D (Multicast)", result.ipClass)
        assertTrue(result.isBogon)
    }

    @Test
    fun `class E is bogon`() {
        val result = calculator.calculate("240.0.0.1/32")
        assertEquals("E (Reserved)", result.ipClass)
        assertTrue(result.isBogon)
    }

    @Test
    fun `loopback is bogon`() {
        val result = calculator.calculate("127.0.0.1/8")
        assertTrue(result.isBogon)
    }

    @Test
    fun `link local is bogon`() {
        val result = calculator.calculate("169.254.1.0/24")
        assertTrue(result.isBogon)
    }

    @Test
    fun `CGNAT range is bogon`() {
        val result = calculator.calculate("100.64.0.0/10")
        assertTrue(result.isBogon)
    }

    @Test
    fun `supernet of bogon is not bogon`() {
        val result = calculator.calculate("0.0.0.0/0")
        assertFalse(result.isBogon)
    }

    @Test
    fun `leading zero octet rejected`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculate("192.168.01.1/24")
        }
    }

    @Test
    fun `zero octet accepted`() {
        val result = calculator.calculate("10.0.0.0/8")
        assertEquals("10.0.0.0", result.networkAddress)
    }

    @Test
    fun `multiple spaces between IP and mask accepted`() {
        val result = calculator.calculate("192.168.1.0   255.255.255.0")
        assertEquals("192.168.1.0/24", result.cidrNotation)
    }

    @Test
    fun `three tokens in space format rejected`() {
        assertThrows<IllegalArgumentException> {
            calculator.calculate("192.168.1.0 255.255.255.0 extra")
        }
    }
}
