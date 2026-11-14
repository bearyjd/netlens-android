package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.data.model.NetworkKeyAliasEntity
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Steps 2, 3 and 5: candidate lookup, legacy fallback, network scoping and the kill switch. */
class ResolverLookupTest {
    private val h = IdentityHarness()

    @Test
    fun `a device with an unseen MAC is New and owns the normalised MAC identifier`() = runTest {
        val r = h.resolve(lan("10.0.0.5", MAC1)).devices.single()

        assertEquals(Resolution.New, r.resolution)
        assertEquals(setOf(IdentifierKind.MAC_UNIVERSAL to MAC1_ID), r.ownedIdentifiers.map { it.kind to it.value }.toSet())
    }

    @Test
    fun `a re-seen MAC resolves to its existing row`() = runTest {
        h.seedRow(101, "10.0.0.5", ids = listOf(universalMac(MAC1_ID)), mac = MAC1)

        val r = h.resolve(lan("10.0.0.99", MAC1)).devices.single()

        assertEquals(Resolution.Existing(101), r.resolution)
    }

    @Test
    fun `a device without any identifier matches the mac-less row at its IP and is New otherwise`() = runTest {
        h.seedRow(101, "10.0.0.7")

        assertEquals(Resolution.Existing(101), h.resolve(LanDevice(ip = "10.0.0.7")).devices.single().resolution)
        assertEquals(Resolution.New, h.resolve(LanDevice(ip = "10.0.0.8")).devices.single().resolution)
    }

    @Test
    fun `a mac-less row upgrades in place when the same IP later shows a MAC`() = runTest {
        h.seedRow(101, "10.0.0.7")

        val r = h.resolve(lan("10.0.0.7", MAC1)).devices.single()

        assertEquals(Resolution.Existing(101), r.resolution)
    }

    @Test
    fun `a device carrying a non-MAC identifier does not take the IP fallback`() = runTest {
        h.seedRow(101, "10.0.0.7")

        val r = h.resolve(lan("10.0.0.7", null, udn("uuid:x"))).devices.single()

        assertEquals(Resolution.New, r.resolution)
    }

    @Test
    fun `a strong identifier matches across IPs and networks`() = runTest {
        h.seedRow(101, "10.0.0.5", ids = listOf(udn("uuid:x")))

        val r = h.resolve(lan("192.168.9.9", null, udn("uuid:x")), network = "other-net").devices.single()

        assertEquals(Resolution.Existing(101), r.resolution)
    }

    @Test
    fun `legacy MAC_LOCAL with an empty scopeKey matches on any network and is rewritten to the current key`() = runTest {
        h.seedRow(101, "10.0.0.5", ids = listOf(localMac(MAC_LOCAL_ID)), scopeKey = "")

        val first = h.resolve(lan("10.0.0.5", MAC_LOCAL_RAW)).devices.single()

        assertEquals(Resolution.Existing(101), first.resolution)
        assertEquals(101L, h.identifiers.find("MAC_LOCAL", MAC_LOCAL_ID, NET)?.deviceId)
        assertNull(h.identifiers.find("MAC_LOCAL", MAC_LOCAL_ID, ""))
        // Same network again: found by its rewritten key, still one row.
        assertEquals(Resolution.Existing(101), h.resolve(lan("10.0.0.6", MAC_LOCAL_RAW)).devices.single().resolution)
        assertEquals(1, h.known.allDevices.size)
    }

    @Test
    fun `a MAC_LOCAL already scoped to another network is a different device here`() = runTest {
        h.seedRow(101, "10.0.0.5", ids = listOf(localMac(MAC_LOCAL_ID)), mac = MAC_LOCAL_RAW, scopeKey = "net-work")

        assertEquals(Resolution.New, h.resolve(lan("10.0.0.5", MAC_LOCAL_RAW)).devices.single().resolution)
    }

    @Test
    fun `network aliases resolve to the canonical key before scoping`() = runTest {
        h.aliases.insertIfNew(NetworkKeyAliasEntity("alias-key", "canon-key", 1))
        h.seedRow(101, "10.0.0.5", ids = listOf(localMac(MAC_LOCAL_ID)), scopeKey = "canon-key")

        val r = h.resolve(lan("10.0.0.5", MAC_LOCAL_RAW), network = "alias-key").devices.single()

        assertEquals(Resolution.Existing(101), r.resolution)
    }

    @Test
    fun `an alias cycle terminates and resolves deterministically`() = runTest {
        h.aliases.insertIfNew(NetworkKeyAliasEntity("a", "b", 1))
        h.aliases.insertIfNew(NetworkKeyAliasEntity("b", "a", 1))

        assertEquals(h.resolver.canonicalKey("a"), h.resolver.canonicalKey("b"))
    }

    @Test
    fun `a self alias terminates`() = runTest {
        h.aliases.insertIfNew(NetworkKeyAliasEntity("a", "a", 1))

        assertEquals("a", h.resolver.canonicalKey("a"))
    }

    @Test
    fun `an alias chain longer than the hop cap stops at the cap`() = runTest {
        (0 until 20).forEach { h.aliases.insertIfNew(NetworkKeyAliasEntity("k$it", "k${it + 1}", 1)) }

        assertEquals("k${DeviceIdentityResolver.MAX_ALIAS_HOPS}", h.resolver.canonicalKey("k0"))
    }

    @Test
    fun `kill switch off behaves like the MAC-or-IP lookup`() = runTest {
        h.seedRow(101, "10.0.0.5", ids = listOf(udn("uuid:x")))
        h.seedRow(102, "10.0.0.6", ids = listOf(universalMac(MAC1_ID)), mac = MAC1)
        h.seedRow(103, "10.0.0.7")
        val off = ResolveOptions(autoMerge = false)

        // A strong non-MAC identifier alone no longer matches.
        assertEquals(Resolution.New, h.resolve(lan("10.0.0.50", null, udn("uuid:x")), options = off).devices.single().resolution)
        // MAC and IP-without-MAC still do.
        assertEquals(Resolution.Existing(102), h.resolve(lan("10.0.0.51", MAC1), options = off).devices.single().resolution)
        assertEquals(Resolution.Existing(103), h.resolve(LanDevice(ip = "10.0.0.7"), options = off).devices.single().resolution)
    }

    @Test
    fun `kill switch off never merges rows or suggests`() = runTest {
        h.seedRow(101, "10.0.0.5", ids = listOf(udn("uuid:x")))
        h.seedRow(102, "10.0.0.6", ids = listOf(universalMac(MAC1_ID)), mac = MAC1)

        val result = h.resolve(lan("10.0.0.9", MAC1, udn("uuid:x")), options = ResolveOptions(autoMerge = false))

        assertEquals(Resolution.Existing(102), result.devices.single().resolution)
        assertEquals(2, h.known.allDevices.size)
        assertTrue(result.suggestions.isEmpty())
    }
}
