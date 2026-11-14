package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind
import com.ventouxlabs.netlens.core.scan.model.identity.Observation
import com.ventouxlabs.netlens.core.scan.model.identity.ObservationSource
import com.ventouxlabs.netlens.core.scan.model.identity.ObservedField
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Hostile LAN strings and unbounded identifier sets must be bounded before they can be stored or shown. */
class ResolverHostileInputTest {
    private val h = IdentityHarness()

    @Test
    fun `display strings are flattened and capped`() = runTest {
        val device = LanDevice(
            ip = "10.0.0.5", hostname = "evil\nrow\u0000 two", vendor = "V\t".repeat(300), model = "M".repeat(500),
            deviceType = "printer\r\nFORGED", osGuess = "os\u0085x",
            observations = listOf(Observation(ObservationSource.SSDP, ObservedField.MODEL, "m\nx", 10, "d\ry")),
        )

        val d = h.resolve(device).devices.single().device

        assertEquals("evil row two", d.hostname)
        assertTrue(d.vendor!!.length <= 256)
        assertFalse(d.vendor!!.contains('\t'))
        assertEquals(80, d.model!!.length)
        assertEquals("printer FORGED", d.deviceType)
        assertEquals("os x", d.osGuess)
        assertEquals("m x", d.observations.single().value)
        assertEquals("d y", d.observations.single().detail)
    }

    @Test
    fun `identifier values that are over-long, blank or carry control characters are dropped, not truncated`() = runTest {
        val ids = setOf(
            udn("uuid:" + "a".repeat(300)),
            udn("uuid:ok"),
            udn("uuid:\nforged"),
            udn("   "),
        )

        val owned = h.resolve(LanDevice(ip = "10.0.0.5", identifiers = ids)).devices.single().ownedIdentifiers

        assertEquals(setOf("uuid:ok"), owned.map { it.value }.toSet())
    }

    @Test
    fun `an identifier of exactly 256 characters is kept`() = runTest {
        val value = "x".repeat(256)

        val owned = h.resolve(LanDevice(ip = "10.0.0.5", identifiers = setOf(udn(value)))).devices.single().ownedIdentifiers

        assertEquals(setOf(value), owned.map { it.value }.toSet())
    }

    @Test
    fun `identifiers per kind are capped deterministically`() = runTest {
        val flood = (1..100).map { DeviceIdentifier(IdentifierKind.MDNS_DEVICE_ID, "id-%03d".format(it), ObservationSource.MDNS) }
        val other = udn("uuid:keep")

        val owned = h.resolve(LanDevice(ip = "10.0.0.5", identifiers = (flood + other).toSet())).devices.single().ownedIdentifiers

        assertEquals(IdentityPolicy.MAX_IDENTIFIERS_PER_KIND, owned.count { it.kind == IdentifierKind.MDNS_DEVICE_ID })
        assertTrue(owned.any { it.value == "uuid:keep" })
        assertEquals(
            (1..32).map { "id-%03d".format(it) }.toSet(),
            owned.filter { it.kind == IdentifierKind.MDNS_DEVICE_ID }.map { it.value }.toSet(),
        )
    }

    @Test
    fun `duplicate kind and value from different sources collapse and proven needs every report to agree`() = runTest {
        val ids = setOf(
            udn("uuid:x", proven = true),
            DeviceIdentifier(IdentifierKind.UPNP_UDN, "uuid:x", ObservationSource.MDNS, proven = false),
        )

        val owned = h.resolve(LanDevice(ip = "10.0.0.5", identifiers = ids)).devices.single().ownedIdentifiers

        assertEquals(1, owned.size)
        assertFalse(owned.single().proven)
    }
}
