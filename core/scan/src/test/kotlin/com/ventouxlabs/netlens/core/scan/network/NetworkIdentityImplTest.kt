package com.ventouxlabs.netlens.core.scan.network

import com.ventouxlabs.netlens.core.scan.engine.FakeArpTableReader
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NetworkIdentityImplTest {
    private class FakeLinks(var bound: LinkInfo? = null, var active: LinkInfo? = null) : NetworkLinkReader {
        override fun boundLink() = bound
        override fun activeLink() = active
        override fun ssid(): String? = null
    }

    private val lan = LinkInfo("192.168.1.1", "192.168.1.0/24")
    private val vpn = LinkInfo("10.8.0.1", "10.8.0.0/24")

    @Test
    fun `the bound LAN network wins over an active VPN`() = runTest {
        val arp = FakeArpTableReader().apply { table = mapOf("192.168.1.1" to "AA:BB:CC:DD:EE:00", "10.8.0.1" to "02:00:00:00:00:01") }
        val identity = NetworkIdentityImpl(FakeLinks(bound = lan, active = vpn), arp)

        assertEquals("192.168.1.1", identity.currentGatewayIp())
        assertEquals("192.168.1.0/24", identity.currentSubnet())
        assertEquals("AA:BB:CC:DD:EE:00", identity.currentGatewayMac())
    }

    @Test
    fun `with nothing bound the active network is used`() = runTest {
        val identity = NetworkIdentityImpl(FakeLinks(bound = null, active = lan), FakeArpTableReader())
        assertEquals("192.168.1.1", identity.currentGatewayIp())
        assertEquals("192.168.1.0/24", identity.currentSubnet())
    }

    @Test
    fun `no network at all gives no gateway`() = runTest {
        val identity = NetworkIdentityImpl(FakeLinks(), FakeArpTableReader())
        assertNull(identity.currentGatewayIp())
        assertNull(identity.currentGatewayMac())
        assertEquals(emptyList<NetworkKey>(), identity.currentNetworkKeys())
    }

    @Test
    fun `snapshots compare ignoring a MAC the ARP table had not filled in yet`() {
        val early = NetworkSnapshot(null, "192.168.1.1", "192.168.1.0/24")
        val late = NetworkSnapshot("aa:bb:cc:dd:ee:00", "192.168.1.1", "192.168.1.0/24")
        assertEquals(true, early.sameNetworkAs(late))
        assertEquals(false, late.sameNetworkAs(late.copy(gatewayMac = "aa:bb:cc:dd:ee:01")))
        assertEquals(false, late.sameNetworkAs(late.copy(subnet = "10.0.0.0/24")))
        assertEquals(false, late.sameNetworkAs(late.copy(gatewayIp = "192.168.1.254")))
    }
}
