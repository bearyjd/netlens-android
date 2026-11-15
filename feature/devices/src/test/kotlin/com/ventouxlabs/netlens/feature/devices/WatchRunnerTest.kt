package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.scan.network.FakeNetworkIdentity
import com.ventouxlabs.netlens.core.data.model.NetworkKeyAliasEntity
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import com.ventouxlabs.netlens.core.scan.engine.FakeArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.FakeOuiLookup
import com.ventouxlabs.netlens.core.scan.engine.FakeSubnetScanner
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.network.NetworkKeyCanonicalizer
import com.ventouxlabs.netlens.core.scan.testing.InventoryFakes
import com.ventouxlabs.netlens.core.scan.network.NetworkKey.Kind
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchRunnerTest {

    private fun runner(
        identity: FakeNetworkIdentity,
        watchedDao: FakeWatchedNetworkDao,
        subnetScanner: FakeSubnetScanner = FakeSubnetScanner(),
        arp: FakeArpTableReader = FakeArpTableReader(),
        oui: FakeOuiLookup = FakeOuiLookup(),
        repo: RecordingDeviceInventoryRepository = RecordingDeviceInventoryRepository(),
        keys: NetworkKeyCanonicalizer = InventoryFakes().networkKeys,
    ) = WatchRunner(identity, watchedDao, subnetScanner, arp, oui, repo, keys) to repo

    @Test
    fun `unresolvable gateway is a no-op`() = runTest {
        val identity = FakeNetworkIdentity().apply { gatewayMac = null }
        val (r, repo) = runner(identity, FakeWatchedNetworkDao())
        assertEquals(WatchOutcome.NoGateway, r.run())
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `gateway not in watched set is a no-op`() = runTest {
        val identity = FakeNetworkIdentity().apply { gatewayMac = "AA:BB:CC:DD:EE:FF"; subnet = "192.168.1.0/24" }
        val (r, repo) = runner(identity, FakeWatchedNetworkDao())
        assertEquals(WatchOutcome.NotWatched, r.run())
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `watched network sweep persists tagged devices`() = runTest {
        val identity = FakeNetworkIdentity().apply { gatewayMac = "AA:BB:CC:DD:EE:FF"; subnet = "192.168.1.0/24" }
        val watchedDao = FakeWatchedNetworkDao().apply {
            upsertBlocking(WatchedNetworkEntity(id = 3, displayName = "Home", gatewayMac = "AA:BB:CC:DD:EE:FF", subnet = "192.168.1.0/24", watchEnabled = true))
        }
        val subnetScanner = FakeSubnetScanner().apply { devices = listOf(LanDevice(ip = "192.168.1.10")) }
        val arp = FakeArpTableReader().apply { table = mapOf("192.168.1.10" to "11:22:33:44:55:66") }
        // Keyed on the OUI prefix, which is what a real vendor database is keyed on. The local
        // copy of this fake matched the whole MAC, so it could not tell whether WatchRunner passed
        // something the real lookup would resolve — any string would do as long as the test echoed
        // it back. Now the assertion on `vendor` below actually means something.
        val oui = FakeOuiLookup().apply { table["11:22:33"] = "Acme" }
        val (r, repo) = runner(identity, watchedDao, subnetScanner, arp, oui)
        val outcome = r.run()
        assertEquals(WatchOutcome.Swept(1), outcome)
        assertEquals(1, repo.calls.size)
        assertEquals(3L, repo.calls.first().networkId)
        assertEquals("11:22:33:44:55:66", repo.calls.first().devices.first().macAddress)
        assertEquals("Acme", repo.calls.first().devices.first().vendor)
    }

    @Test
    fun `watched but disabled network is a no-op`() = runTest {
        val identity = FakeNetworkIdentity().apply { gatewayMac = "AA:BB:CC:DD:EE:FF"; subnet = "192.168.1.0/24" }
        val watchedDao = FakeWatchedNetworkDao().apply {
            upsertBlocking(WatchedNetworkEntity(id = 4, displayName = "Home", gatewayMac = "AA:BB:CC:DD:EE:FF", subnet = "192.168.1.0/24", watchEnabled = false))
        }
        val (r, repo) = runner(identity, watchedDao)
        assertEquals(WatchOutcome.NotWatched, r.run())
        assertTrue(repo.calls.isEmpty())
    }

    private val homeMac = "AA:BB:CC:DD:EE:00"
    private fun watchedHome(dao: FakeWatchedNetworkDao) = dao.upsertBlocking(
        WatchedNetworkEntity(id = 7, displayName = "Home", gatewayMac = homeMac, subnet = "192.168.1.0/24", watchEnabled = true),
    )

    @Test
    fun `hotel sharing only the l3 hint is not watched and is not aliased to home`() = runTest {
        val fakes = InventoryFakes()
        val watchedDao = FakeWatchedNetworkDao().also(::watchedHome)
        val home = FakeNetworkIdentity().apply { gatewayMac = homeMac; gatewayIp = "192.168.1.1"; subnet = "192.168.1.0/24" }
        // the foreground scan of home records its keys
        fakes.networkKeys.canonicalize(home.currentNetworkKeys(), now = 1)

        val hotel = FakeNetworkIdentity().apply { gatewayMac = "10:22:33:44:55:66"; gatewayIp = "192.168.1.1"; subnet = "192.168.1.0/24" }
        fakes.networkKeys.canonicalize(hotel.currentNetworkKeys(), now = 2)
        val (r, repo) = runner(hotel, watchedDao, keys = fakes.networkKeys)

        assertEquals(WatchOutcome.NotWatched, r.run())
        assertTrue(repo.calls.isEmpty())
        assertEquals("gw-mac:102233445566", fakes.aliases.canonicalFor("gw-mac:102233445566")) // not aliased to home
    }

    @Test
    fun `an l3-only match is never watched`() = runTest {
        val identity = FakeNetworkIdentity().apply { gatewayIp = "192.168.1.1"; subnet = "192.168.1.0/24" }
        val watchedDao = FakeWatchedNetworkDao().also(::watchedHome)
        // even if an l3 key were somehow recorded as an alias of the watched network, it must not match
        val fakes = InventoryFakes()
        val l3 = identity.currentNetworkKeys().single().key
        fakes.aliases.insertIfNew(NetworkKeyAliasEntity(l3, "gw-mac:aabbccddee00", 1))
        fakes.aliases.insertIfNew(NetworkKeyAliasEntity("gw-mac:aabbccddee00", "gw-mac:aabbccddee00", 1))
        val (r, repo) = runner(identity, watchedDao, keys = fakes.networkKeys)

        assertEquals(WatchOutcome.NotWatched, r.run())
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `a watched network is still matched once ARP is gone, after one foreground scan recorded its keys`() = runTest {
        val fakes = InventoryFakes()
        val watchedDao = FakeWatchedNetworkDao().also(::watchedHome)
        val udn = DeviceIdentifier(
            com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind.UPNP_UDN, "uuid:router",
            com.ventouxlabs.netlens.core.scan.model.identity.ObservationSource.SSDP,
        )
        // foreground, ARP readable: both strong keys are resolved after discovery
        val foreground = FakeNetworkIdentity().apply { gatewayMac = homeMac; gatewayIp = "192.168.1.1"; subnet = "192.168.1.0/24" }
        val discovered = listOf(LanDevice(ip = "192.168.1.1", identifiers = setOf(udn)))
        fakes.networkKeys.canonicalize(foreground.currentNetworkKeys(discovered), now = 1)

        // background, ARP unreadable, but the gateway still offers its UDN
        val background = FakeNetworkIdentity().apply {
            gatewayMac = null; gatewayIp = "192.168.1.1"; subnet = "192.168.1.0/24"; gatewayIdentifier = udn
        }
        val subnetScanner = FakeSubnetScanner().apply { devices = listOf(LanDevice(ip = "192.168.1.10")) }
        val (r, repo) = runner(background, watchedDao, subnetScanner, keys = fakes.networkKeys)

        assertEquals(WatchOutcome.Swept(1), r.run())
        assertEquals(7L, repo.calls.single().networkId)
        assertTrue(repo.calls.single().network.keys.any { it.kind == Kind.GATEWAY_ID })
    }
}
