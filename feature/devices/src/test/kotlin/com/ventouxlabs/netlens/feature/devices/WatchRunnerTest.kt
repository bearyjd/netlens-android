package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import com.ventouxlabs.netlens.core.scan.model.LanDevice
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
    ) = WatchRunner(identity, watchedDao, subnetScanner, arp, oui, repo) to repo

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
        val oui = FakeOuiLookup().apply { vendors = mapOf("11:22:33:44:55:66" to "Acme") }
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
}
