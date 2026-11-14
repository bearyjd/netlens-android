package com.ventouxlabs.netlens.core.scan.model.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class MacAddressesTest {

    @ParameterizedTest
    @CsvSource(
        "AA:BB:CC:DD:EE:01, aabbccddee01",
        "aa-bb-cc-dd-ee-01, aabbccddee01",
        "aabb.ccdd.ee01, aabbccddee01",
        "AABBCCDDEE01, aabbccddee01",
        "'  aa:bb:cc:dd:ee:01 ', aabbccddee01",
    )
    fun `normalises every common spelling to lowercase bare hex`(raw: String, expected: String) {
        assertEquals(expected, MacAddresses.normalize(raw))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "", " ", "00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff", "FF-FF-FF-FF-FF-FF", "aa:bb:cc:dd:ee",
            "aa:bb:cc:dd:ee:01:02", "gg:bb:cc:dd:ee:01", "aa:bb:cc:dd:ee:0", "not a mac", "aa:bb:cc:dd:ee:01\n",
        ],
    )
    fun `rejects junk, all-zero and broadcast`(raw: String) {
        assertNull(MacAddresses.normalize(raw))
    }

    @Test
    fun `null and absurdly long input are rejected`() {
        assertNull(MacAddresses.normalize(null))
        assertNull(MacAddresses.normalize("a".repeat(10_000)))
    }

    /** Must agree with MIGRATION_18_19's CASE over the second nibble: 2,3,6,7,A,B,E,F are local. */
    @ParameterizedTest
    @CsvSource(
        "0,false", "1,false", "2,true", "3,true", "4,false", "5,false", "6,true", "7,true",
        "8,false", "9,false", "a,true", "b,true", "c,false", "d,false", "e,true", "f,true",
    )
    fun `U-L bit matches the migration backfill rule for every second nibble`(nibble: String, local: Boolean) {
        val mac = "a${nibble}bbccddee01"
        assertEquals(local, MacAddresses.isLocallyAdministered(mac))
        val expectedKind = if (local) IdentifierKind.MAC_LOCAL else IdentifierKind.MAC_UNIVERSAL
        assertEquals(expectedKind, MacAddresses.kindOf(mac))
        val sqlRule = nibble.uppercase() in setOf("2", "3", "6", "7", "A", "B", "E", "F")
        assertEquals(sqlRule, local)
    }

    @Test
    fun `multicast is the low bit of the first octet`() {
        assertTrue(MacAddresses.isMulticast("01005e000001"))
        assertFalse(MacAddresses.isMulticast("00005e000001"))
        assertFalse(MacAddresses.isMulticast("aabbccddee00"))
    }

    @Test
    fun `EUI-64 to MAC flips the U-L bit and strips ff-fe`() {
        assertEquals("001122334455", MacAddresses.fromEui64("0211:22ff:fe33:4455"))
        assertEquals("021122334455", MacAddresses.fromEui64("0011:22ff:fe33:4455"))
        assertEquals("001122334455", MacAddresses.fromEui64("021122fffe334455"))
    }

    @Test
    fun `EUI-64 round-trips through the MAC`() {
        val mac = "a4b1c2d3e4f5"
        val eui = MacAddresses.toEui64(mac)

        assertEquals("a6b1c2fffed3e4f5", eui)
        assertEquals(mac, MacAddresses.fromEui64(eui))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "0211:22aa:bb33:4455", "0211:22ff:fe33", "zz11:22ff:fe33:4455"])
    fun `EUI-64 input that is not a modified EUI-64 is rejected`(raw: String) {
        assertNull(MacAddresses.fromEui64(raw))
    }
}
