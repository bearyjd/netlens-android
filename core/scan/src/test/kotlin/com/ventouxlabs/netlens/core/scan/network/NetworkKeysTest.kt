package com.ventouxlabs.netlens.core.scan.network

import com.ventouxlabs.netlens.core.scan.identity.lan
import com.ventouxlabs.netlens.core.scan.identity.universalMac
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind
import com.ventouxlabs.netlens.core.scan.model.identity.ObservationSource
import com.ventouxlabs.netlens.core.scan.network.NetworkKey.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkKeysTest {
    private fun udn(value: String, proven: Boolean = true) = DeviceIdentifier(IdentifierKind.UPNP_UDN, value, ObservationSource.SSDP, proven)
    private fun smb(value: String, proven: Boolean = true) = DeviceIdentifier(IdentifierKind.SMB_SERVER_GUID, value, ObservationSource.SMB, proven)
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
    fun `different spellings of one gateway MAC give one key`() {
        val a = NetworkKeys.build("AA:BB:CC:DD:EE:00", null, null, emptyList())
        assertEquals(a, NetworkKeys.build("aa-bb-cc-dd-ee-00", null, null, emptyList()))
        assertEquals("gw-mac:aabbccddee00", a.single().key)
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
    fun `an unproven identifier is never a gw-id`() {
        // a UDN is announced, not authenticated: any host can claim another router's
        val spoofed = lan("192.168.1.1", null, udn("uuid:router-1", proven = false))
        assertEquals(emptyList<NetworkKey>(), NetworkKeys.build(null, "192.168.1.1", null, listOf(spoofed)))
    }

    @Test
    fun `a device merely listing the gateway IP as an extra address does not beat the device at that IP`() {
        val impostor = LanDevice(ip = "192.168.1.66", additionalIps = setOf("192.168.1.1"), identifiers = setOf(udn("uuid:impostor")))
        val real = lan("192.168.1.1", null, udn("uuid:real"))
        val key = NetworkKeys.build(null, "192.168.1.1", null, listOf(impostor, real)).single().key
        assertEquals("gw-id:upnp_udn:uuid:real", key)
    }

    @Test
    fun `an additional address is used only when no device sits at the gateway IP`() {
        val multiHomed = LanDevice(ip = "192.168.1.66", additionalIps = setOf("192.168.1.1"), identifiers = setOf(udn("uuid:multi")))
        assertEquals("gw-id:upnp_udn:uuid:multi", NetworkKeys.build(null, "192.168.1.1", null, listOf(multiHomed)).single().key)
    }

    @Test
    fun `a MAC identifier of the gateway is not a gw-id`() {
        val macOnly = lan("192.168.1.1", null, universalMac("00bbcc000001").copy(proven = true))
        assertEquals(emptyList<NetworkKey>(), NetworkKeys.build(null, "192.168.1.1", null, listOf(macOnly)))
    }

    @Test
    fun `the gateway id is the lowest kind first whatever the offering order`() {
        val one = lan("192.168.1.1", null, smb("guid-b"), udn("uuid:a"))
        val two = lan("192.168.1.1", null, udn("uuid:a"), smb("guid-b"))
        assertEquals("gw-id:upnp_udn:uuid:a", NetworkKeys.build(null, "192.168.1.1", null, listOf(one)).single().key)
        assertEquals("gw-id:upnp_udn:uuid:a", NetworkKeys.build(null, "192.168.1.1", null, listOf(two)).single().key)
    }

    @Test
    fun `a hostile gateway identifier is flattened and clamped`() {
        val hostile = lan("192.168.1.1", null, udn("uuid:\n\u0000EVIL\tX" + "z".repeat(10_000)))
        val key = NetworkKeys.build(null, "192.168.1.1", null, listOf(hostile)).single().key
        assertTrue(key.none { it.isISOControl() })
        assertEquals("gw-id:upnp_udn:".length + NetworkKeys.MAX_GATEWAY_ID_LENGTH, key.length)
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
    fun `the label is the SSID when known, else the subnet, flattened`() {
        assertEquals("Home", NetworkKeys.label("Home", "192.168.1.0/24"))
        assertEquals("192.168.1.0/24", NetworkKeys.label(null, "192.168.1.0/24"))
        assertEquals("a b", NetworkKeys.label("a\nb", null))
        assertNull(NetworkKeys.label(null, null))
    }

    @Test
    fun `the label is clamped by code point without splitting a surrogate pair`() {
        val label = NetworkKeys.label("😀".repeat(500), null)!!
        assertEquals(NetworkKeys.MAX_LABEL_LENGTH, label.codePointCount(0, label.length))
        assertTrue(label.none { Character.isSurrogate(it) } || label.length == 2 * NetworkKeys.MAX_LABEL_LENGTH)
        assertEquals("a".repeat(NetworkKeys.MAX_LABEL_LENGTH), NetworkKeys.label("a".repeat(500), null))
    }

    @Test
    fun `bidi and invisible format characters are stripped from the label`() {
        val hostile = "Ho‮me​‏⁦Wi⁩Fi‪"
        assertEquals("HomeWiFi", NetworkKeys.label(hostile, null))
        assertNull(NetworkKeys.label("​‮", null))
    }
}
