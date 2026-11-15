package com.ventouxlabs.netlens.core.scan.network

import com.ventouxlabs.netlens.core.scan.identity.lan
import com.ventouxlabs.netlens.core.scan.identity.smb
import com.ventouxlabs.netlens.core.scan.identity.udn
import com.ventouxlabs.netlens.core.scan.identity.universalMac
import com.ventouxlabs.netlens.core.scan.network.NetworkKey.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkKeysTest {
    private val gw = lan("192.168.1.1", null, udn("uuid:ROUTER-1"))

    @Test
    fun `keys come strongest first and a gateway MAC is normalised`() {
        val keys = NetworkKeys.build("AA:BB:CC:DD:EE:00", "192.168.1.1", "192.168.1.0/24", listOf(gw))
        assertEquals(listOf(Kind.GATEWAY_MAC, Kind.GATEWAY_ID, Kind.L3), keys.map { it.kind })
        assertEquals("gw-mac:aabbccddee00", keys[0].key)
        assertEquals("gw-id:upnp_udn:uuid:router-1", keys[1].key)
        assertTrue(keys[2].key.startsWith("l3:") && keys[2].key.length == 3 + 64)
    }

    @Test
    fun `without ARP or a gateway device only the l3 hint remains`() {
        val keys = NetworkKeys.build(null, "192.168.1.1", "192.168.1.0/24", emptyList())
        assertEquals(listOf(Kind.L3), keys.map { it.kind })
        assertFalse(keys.single().isStrong)
    }

    @Test
    fun `no gateway and no subnet means no keys at all`() {
        assertEquals(emptyList<NetworkKey>(), NetworkKeys.build(null, null, null, listOf(gw)))
    }

    @Test
    fun `l3 depends on both the gateway IP and the subnet`() {
        val a = NetworkKeys.build(null, "192.168.1.1", "192.168.1.0/24", emptyList()).single()
        assertEquals(a, NetworkKeys.build(null, "192.168.1.1", "192.168.1.0/24", emptyList()).single())
        assertTrue(a != NetworkKeys.build(null, "192.168.1.254", "192.168.1.0/24", emptyList()).single())
        assertTrue(a != NetworkKeys.build(null, "192.168.1.1", "192.168.1.0/25", emptyList()).single())
    }

    @Test
    fun `the gateway id comes only from the device at the gateway IP`() {
        val other = lan("192.168.1.50", null, udn("uuid:printer"))
        assertEquals(emptyList<NetworkKey>(), NetworkKeys.build(null, "192.168.1.1", null, listOf(other)))
    }

    @Test
    fun `a MAC identifier of the gateway is not a gw-id`() {
        val macOnly = lan("192.168.1.1", null, universalMac("00bbcc000001"))
        assertEquals(emptyList<NetworkKey>(), NetworkKeys.build(null, "192.168.1.1", null, listOf(macOnly)))
    }

    @Test
    fun `the gateway id is deterministic when several strong identifiers are offered`() {
        val both = lan("192.168.1.1", null, smb("guid-b"), udn("uuid:a"))
        val reordered = lan("192.168.1.1", null, udn("uuid:a"), smb("guid-b"))
        assertEquals(
            NetworkKeys.build(null, "192.168.1.1", null, listOf(both)),
            NetworkKeys.build(null, "192.168.1.1", null, listOf(reordered)),
        )
    }

    @Test
    fun `a hostile gateway identifier is flattened and clamped`() {
        val hostile = lan("192.168.1.1", null, udn("uuid:\n\u0000EVIL\tX" + "z".repeat(10_000)))
        val key = NetworkKeys.build(null, "192.168.1.1", null, listOf(hostile)).single().key
        assertTrue(key.none { it.isISOControl() })
        assertTrue(key.length <= "gw-id:upnp_udn:".length + NetworkKeys.MAX_GATEWAY_ID_LENGTH)
    }

    @Test
    fun `a gateway identifier that flattens to nothing yields no key`() {
        val blank = lan("192.168.1.1", null, udn(" \n\t "))
        assertEquals(emptyList<NetworkKey>(), NetworkKeys.build(null, "192.168.1.1", null, listOf(blank)))
    }

    @Test
    fun `a multicast or junk gateway MAC is no key`() {
        assertNull(NetworkKey.gatewayMac("01:00:5e:00:00:01"))
        assertNull(NetworkKey.gatewayMac("not a mac"))
        assertNull(NetworkKey.gatewayMac(null))
    }

    @Test
    fun `the label is the SSID when known, else the subnet, flattened and capped`() {
        assertEquals("Home", NetworkKeys.label("Home", "192.168.1.0/24"))
        assertEquals("192.168.1.0/24", NetworkKeys.label(null, "192.168.1.0/24"))
        assertEquals("a b", NetworkKeys.label("a\nb", null))
        assertEquals(NetworkKeys.MAX_LABEL_LENGTH, NetworkKeys.label("x".repeat(500), null)!!.length)
        assertNull(NetworkKeys.label(null, null))
    }
}
