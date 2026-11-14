package com.ventouxlabs.netlens.core.scan.model.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdentifierKindTest {

    @Test
    fun `only MAC_LOCAL is medium strength and network scoped`() {
        assertEquals(Strength.MEDIUM, IdentifierKind.MAC_LOCAL.strength)
        assertEquals(Scope.NETWORK, IdentifierKind.MAC_LOCAL.scope)
        val others = IdentifierKind.entries - IdentifierKind.MAC_LOCAL
        assertTrue(others.all { it.strength == Strength.STRONG && it.scope == Scope.GLOBAL })
    }

    @Test
    fun `the plan's ten kinds exist`() {
        assertEquals(10, IdentifierKind.entries.size)
    }

    @Test
    fun `an identifier defaults to unproven and a LanDevice defaults to no identity data`() {
        val id = DeviceIdentifier(IdentifierKind.UPNP_UDN, "uuid:x", ObservationSource.SSDP)
        assertFalse(id.proven)

        val device = com.ventouxlabs.netlens.core.scan.model.LanDevice(ip = "10.0.0.1")
        assertTrue(device.identifiers.isEmpty())
        assertTrue(device.observations.isEmpty())
        assertTrue(device.additionalIps.isEmpty())
    }

    @Test
    fun `an observation carries source field value weight and detail`() {
        val obs = Observation(ObservationSource.PORTS, ObservedField.DEVICE_TYPE, "printer", 70, "port 631 (IPP)")
        assertEquals(70, obs.weight)
        assertEquals("port 631 (IPP)", obs.detail)
    }
}
