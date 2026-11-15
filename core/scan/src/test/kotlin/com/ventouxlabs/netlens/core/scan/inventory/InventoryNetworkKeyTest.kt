package com.ventouxlabs.netlens.core.scan.inventory

import com.ventouxlabs.netlens.core.scan.RecordingNewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.identity.IdentityHarness
import com.ventouxlabs.netlens.core.scan.identity.MAC1
import com.ventouxlabs.netlens.core.scan.identity.MAC_LOCAL_RAW
import com.ventouxlabs.netlens.core.scan.identity.lan
import com.ventouxlabs.netlens.core.scan.network.NetworkContext
import com.ventouxlabs.netlens.core.scan.network.NetworkKey
import com.ventouxlabs.netlens.core.scan.network.NetworkKey.Kind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** persistScan stores the canonical network key and a label, and registers aliases for strong keys. */
class InventoryNetworkKeyTest {
    private val h = IdentityHarness()
    private val repo = h.fakes.repository(RecordingNewDeviceNotifier())

    private fun net(label: String?, vararg keys: NetworkKey) = NetworkContext(keys.toList(), label)
    private val homeMac = NetworkKey(Kind.GATEWAY_MAC, "a")
    private val homeId = NetworkKey(Kind.GATEWAY_ID, "g")
    private val l3 = NetworkKey(Kind.L3, "x")

    @Test
    fun `a sighting stores the canonical key and the label`() = runTest {
        repo.persistScan(listOf(lan("10.0.0.5", MAC1)), networkId = null, network = net("Home", homeMac, homeId, l3))

        val s = h.sightings.getForDevice(1).single()
        assertEquals("gw-mac:a", s.networkKey)
        assertEquals("Home", s.networkLabel)
    }

    @Test
    fun `a later scan that only sees the gateway id lands on the same canonical key`() = runTest {
        repo.persistScan(listOf(lan("10.0.0.5", MAC1)), networkId = null, network = net("Home", homeMac, homeId))
        repo.persistScan(listOf(lan("10.0.0.5", MAC1)), networkId = null, network = net("192.168.1.0/24", homeId))

        val s = h.sightings.getForDevice(1).single()
        assertEquals("gw-mac:a", s.networkKey)
        assertEquals("192.168.1.0/24", s.networkLabel)
    }

    @Test
    fun `a NETWORK-scoped identifier is stored under the canonical key`() = runTest {
        repo.persistScan(listOf(lan("10.0.0.5", MAC_LOCAL_RAW)), networkId = null, network = net("Home", homeMac, homeId))

        assertEquals(listOf("gw-mac:a"), h.identifiers.getForDevice(1).map { it.scopeKey })
    }

    @Test
    fun `with no keys every scan shares the fallback key`() = runTest {
        repo.persistScan(listOf(lan("10.0.0.5", MAC1)), networkId = null)
        assertEquals("unknown-network", h.sightings.getForDevice(1).single().networkKey)
    }

    @Test
    fun `the hotel scan sharing only the l3 hint is stored under its own key`() = runTest {
        repo.persistScan(listOf(lan("10.0.0.5", MAC1)), networkId = null, network = net("Home", homeMac, l3))
        repo.persistScan(listOf(lan("10.0.0.5", MAC1)), networkId = null, network = net("Hotel", NetworkKey(Kind.GATEWAY_MAC, "b"), l3))

        assertEquals(setOf("gw-mac:a", "gw-mac:b"), h.sightings.getForDevice(1).map { it.networkKey }.toSet())
        assertTrue(h.aliases.all.all { it.aliasKey == it.canonicalKey }) // neither network is aliased to the other
    }
}
