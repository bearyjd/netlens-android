package com.ventouxlabs.netlens.core.scan.network

import com.ventouxlabs.netlens.core.scan.identity.IdentityHarness
import com.ventouxlabs.netlens.core.scan.network.NetworkKey.Kind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkKeyCanonicalizerTest {
    private val h = IdentityHarness()
    private val c = h.fakes.networkKeys

    private fun mac(v: String) = NetworkKey(Kind.GATEWAY_MAC, v)
    private fun id(v: String) = NetworkKey(Kind.GATEWAY_ID, v)
    private fun l3(v: String) = NetworkKey(Kind.L3, v)

    @Test
    fun `with no key known the strongest becomes canonical and the other strong keys alias it`() = runTest {
        val canonical = c.canonicalize(listOf(mac("a"), id("g"), l3("x")), now = 5)
        assertEquals("gw-mac:a", canonical)
        assertEquals(mapOf("gw-mac:a" to "gw-mac:a", "gw-id:g" to "gw-mac:a"), h.aliases.all.associate { it.aliasKey to it.canonicalKey })
    }

    @Test
    fun `l3 is never registered as an alias`() = runTest {
        c.canonicalize(listOf(mac("a"), l3("x")), now = 5)
        assertTrue(h.aliases.all.none { it.aliasKey.startsWith("l3:") || it.canonicalKey.startsWith("l3:") })
    }

    @Test
    fun `a later scan that sees only the gateway id lands on the canonical key the MAC established`() = runTest {
        c.canonicalize(listOf(mac("a"), id("g")), now = 1)
        assertEquals("gw-mac:a", c.canonicalize(listOf(id("g")), now = 2))
    }

    @Test
    fun `l3 never participates in lookup`() = runTest {
        // an alias row keyed by an l3 value (impossible to register here, but seeded) must be ignored
        h.aliases.insertIfNew(com.ventouxlabs.netlens.core.data.model.NetworkKeyAliasEntity("l3:x", "gw-mac:home", 1))
        assertEquals("gw-mac:b", c.canonicalize(listOf(mac("b"), l3("x")), now = 2))
        assertEquals("gw-mac:b", h.aliases.canonicalFor("gw-mac:b"))
    }

    @Test
    fun `the hotel with the same l3 is not aliased to home`() = runTest {
        c.canonicalize(listOf(mac("a"), l3("x")), now = 1)
        val hotel = c.canonicalize(listOf(mac("b"), l3("x")), now = 2)
        assertEquals("gw-mac:b", hotel)
        assertEquals("gw-mac:b", h.aliases.canonicalFor("gw-mac:b"))
    }

    @Test
    fun `an l3-only scan uses l3 as is and registers nothing`() = runTest {
        assertEquals("l3:x", c.canonicalize(listOf(l3("x")), now = 1))
        assertTrue(h.aliases.all.isEmpty())
    }

    @Test
    fun `no keys at all falls back to the shared key`() = runTest {
        assertEquals("unknown-network", c.canonicalize(emptyList(), now = 1))
    }

    @Test
    fun `a watched gateway MAC resolves through its aliases`() = runTest {
        c.canonicalize(listOf(mac("aabbccddee00"), id("g")), now = 1)
        assertEquals("gw-mac:aabbccddee00", c.canonicalForGatewayMac("AA:BB:CC:DD:EE:00"))
        assertEquals(null, c.canonicalForGatewayMac("junk"))
    }

    @Test
    fun `a gateway id shared with another network never pulls a new gateway MAC into it`() = runTest {
        c.canonicalize(listOf(mac("a"), id("r")), now = 1)
        val hotel = c.canonicalize(listOf(mac("b"), id("r")), now = 2)
        assertEquals("gw-mac:b", hotel)
        assertEquals("gw-mac:b", h.aliases.canonicalFor("gw-mac:b") ?: "gw-mac:b") // never aliased to a
        assertEquals("gw-mac:a", h.aliases.canonicalFor("gw-id:r"))
    }

    @Test
    fun `strong keys that already map to different canonicals register nothing and pick the MAC's canonical, in either order`() = runTest {
        c.canonicalize(listOf(id("g1")), now = 1)
        c.canonicalize(listOf(mac("a")), now = 1)
        val before = h.aliases.all.toSet()

        assertEquals("gw-mac:a", c.canonicalize(listOf(mac("a"), id("g1")), now = 2))
        assertEquals("gw-mac:a", c.canonicalize(listOf(id("g1"), mac("a")), now = 3))
        assertEquals(before, h.aliases.all.toSet())
    }

    @Test
    fun `conflicting gateway ids without a MAC resolve to the smallest root, in either order, registering nothing`() = runTest {
        c.canonicalize(listOf(id("g2")), now = 1)
        c.canonicalize(listOf(id("g1")), now = 1)
        val before = h.aliases.all.toSet()

        assertEquals("gw-id:g1", c.canonicalize(listOf(id("g2"), id("g1")), now = 2))
        assertEquals("gw-id:g1", c.canonicalize(listOf(id("g1"), id("g2")), now = 3))
        assertEquals(before, h.aliases.all.toSet())
    }

    @Test
    fun `with nothing known the canonical key does not depend on key order`() = runTest {
        val fresh = IdentityHarness().fakes.networkKeys
        assertEquals("gw-id:g1", fresh.canonicalize(listOf(id("g2"), id("g1")), now = 1))
        assertEquals("gw-id:g1", IdentityHarness().fakes.networkKeys.canonicalize(listOf(id("g1"), id("g2")), now = 1))
    }

    @Test
    fun `a canonical key keeps only its 8 newest gateway id aliases`() = runTest {
        for (n in 1..12) c.canonicalize(listOf(mac("a"), id("g%02d".format(n))), now = n.toLong())

        val ids = h.aliases.all.filter { it.aliasKey.startsWith("gw-id:") }.map { it.aliasKey }.toSet()
        assertEquals((5..12).map { "gw-id:g%02d".format(it) }.toSet(), ids)
        assertEquals("gw-mac:a", h.aliases.canonicalFor("gw-mac:a"))
    }
}
