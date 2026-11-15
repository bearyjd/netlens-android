package com.ventouxlabs.netlens.core.scan.engine

import com.ventouxlabs.netlens.core.scan.engine.DiscoveryEvent.DeviceUpdated
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryEvent.Finished
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryEvent.Progress
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryEvent.StageFailed
import com.ventouxlabs.netlens.core.scan.model.DiscoveryMethod
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.MAX_MODEL_LENGTH
import com.ventouxlabs.netlens.core.scan.model.NetBiosInfo
import com.ventouxlabs.netlens.core.scan.model.SsdpDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDiscoveryPipelineTest {

    /** Reclassifies via [reclassify] so a test controls what the "weaker re-guess" looks like. */
    private class ScriptedFingerprinter(
        var reclassify: (LanDevice) -> LanDevice = { it },
        var ssdp: Pair<String?, String?> = null to null,
        var netBiosOs: String? = null,
    ) : DeviceFingerprinter {
        override suspend fun fingerprint(device: LanDevice): LanDevice = reclassify(device)
        override fun classifyFromServices(services: List<String>): Pair<String?, String?> = null to null
        override fun classifyFromSsdp(ssdpDevice: SsdpDevice): Pair<String?, String?> = ssdp
        override fun classifyFromNetBios(info: NetBiosInfo): String? = netBiosOs
        override fun fingerprintWithPorts(device: LanDevice, openPorts: List<Int>) =
            PortFingerprint(null, null, emptyList())
    }

    private val subnet = FakeSubnetScanner()
    private val mdns = FakeLanMdnsScanner()
    private val ssdp = FakeSsdpScanner()
    private val netBios = FakeNetBiosProber()
    private val arp = FakeArpTableReader()
    private val oui = FakeOuiLookup()
    private val fingerprinter = ScriptedFingerprinter()

    private fun pipeline(
        subnetScanner: SubnetScanner = subnet,
        mdnsScanner: LanMdnsScanner = mdns,
        ssdpScanner: SsdpScanner = ssdp,
        netBiosProber: NetBiosProber = netBios,
        arpTableReader: ArpTableReader = arp,
    ) = DeviceDiscoveryPipelineImpl(
        subnetScanner, mdnsScanner, ssdpScanner, netBiosProber, arpTableReader, fingerprinter, oui,
    )

    private suspend fun events(mode: DiscoveryMode, p: DeviceDiscoveryPipeline = pipeline()) =
        p.run(DiscoveryRequest("192.168.1.0/24", mode)).toList()

    private fun List<DiscoveryEvent>.finished() = filterIsInstance<Finished>().single().devices

    // --- merge / fingerprint ordering (moved from the LanScanViewModel) -------------------------

    @Test
    fun `a strong SSDP classification is not overwritten by a weaker ping re-guess`() = runTest {
        ssdp.devices = listOf(SsdpDevice(ip = "192.168.1.5", friendlyName = "Hub"))
        fingerprinter.ssdp = "Smart Hub" to null
        subnet.devices = listOf(LanDevice(ip = "192.168.1.5", hostname = "hub"))
        fingerprinter.reclassify = {
            if (it.discoveryMethod == DiscoveryMethod.SSDP) it
            else it.copy(
                deviceType = "Computer",
                fingerprintConfidence = DeviceFingerprinterImpl.CONFIDENCE_HOSTNAME_GUESS,
            )
        }

        val device = events(DiscoveryMode.Standard).finished().single()

        assertEquals("Smart Hub", device.deviceType)
        assertEquals(DeviceFingerprinterImpl.CONFIDENCE_SSDP, device.fingerprintConfidence)
    }

    @Test
    fun `the stronger reading wins whichever engine reports first`() = runTest {
        val strong = LanDevice(
            ip = "192.168.1.6",
            deviceType = "Printer",
            fingerprintConfidence = DeviceFingerprinterImpl.CONFIDENCE_MDNS_SERVICE,
        )
        val weak = LanDevice(
            ip = "192.168.1.6",
            deviceType = "Computer",
            fingerprintConfidence = DeviceFingerprinterImpl.CONFIDENCE_HOSTNAME_GUESS,
        )
        // Ping runs before mDNS: strong arrives second.
        subnet.devices = listOf(weak)
        mdns.devices = listOf(strong)
        assertEquals("Printer", events(DiscoveryMode.Standard).finished().single().deviceType)

        // And the other way round: strong arrives first.
        subnet.devices = listOf(strong)
        mdns.devices = listOf(weak)
        assertEquals("Printer", events(DiscoveryMode.Standard).finished().single().deviceType)
    }

    @Test
    fun `a device's own weaker re-fingerprint does not downgrade it or mark it multiply discovered`() = runTest {
        subnet.devices = listOf(
            LanDevice(
                ip = "192.168.1.7",
                deviceType = "Printer",
                fingerprintConfidence = DeviceFingerprinterImpl.CONFIDENCE_MDNS_SERVICE,
                discoveryMethod = DiscoveryMethod.PING,
            ),
        )
        fingerprinter.reclassify = {
            it.copy(deviceType = "Computer", fingerprintConfidence = DeviceFingerprinterImpl.CONFIDENCE_HOSTNAME_GUESS)
        }

        val device = events(DiscoveryMode.Standard).finished().single()

        assertEquals("Printer", device.deviceType)
        assertEquals(DiscoveryMethod.PING, device.discoveryMethod)
    }

    @Test
    fun `readings from different engines merge into one multiply-discovered device`() = runTest {
        subnet.devices = listOf(LanDevice(ip = "192.168.1.8", discoveryMethod = DiscoveryMethod.PING))
        mdns.devices = listOf(LanDevice(ip = "192.168.1.8", hostname = "tv", discoveryMethod = DiscoveryMethod.MDNS))

        val device = events(DiscoveryMode.Standard).finished().single()

        assertEquals(DiscoveryMethod.MULTIPLE, device.discoveryMethod)
        assertEquals("tv", device.hostname)
    }

    @Test
    fun `on equal confidence the reading that arrived first keeps its hostname`() = runTest {
        // Ping runs before mDNS, so ping's reading is the existing one when mDNS's is merged in.
        subnet.devices = listOf(LanDevice(ip = "192.168.1.4", hostname = "first"))
        mdns.devices = listOf(LanDevice(ip = "192.168.1.4", hostname = "second", discoveryMethod = DiscoveryMethod.MDNS))

        val device = events(DiscoveryMode.Standard).finished().single()

        assertEquals("first", device.hostname)
    }

    @Test
    fun `an SSDP device is built from its description and capped model`() = runTest {
        ssdp.devices = listOf(
            SsdpDevice(
                ip = "192.168.1.9", friendlyName = "Lamp", manufacturer = "Acme",
                modelName = "M".repeat(500), deviceType = "urn:light",
            ),
        )
        fingerprinter.ssdp = "Light" to null

        val device = events(DiscoveryMode.Standard).finished().single()

        assertEquals("Lamp", device.hostname)
        assertEquals("Acme", device.vendor)
        assertEquals("Light", device.deviceType)
        assertEquals(listOf("SSDP: urn:light"), device.fingerprintEvidence)
        assertTrue(device.model!!.length <= MAX_MODEL_LENGTH)
    }

    // --- enrichment ----------------------------------------------------------------------------

    @Test
    fun `ARP fills a missing MAC and vendor but never replaces what the device already had`() = runTest {
        subnet.devices = listOf(
            LanDevice(ip = "192.168.1.10"),
            LanDevice(ip = "192.168.1.11", macAddress = "AA:AA:AA:00:00:01", vendor = "Own"),
        )
        arp.table = mapOf("192.168.1.10" to "11:22:33:44:55:66", "192.168.1.11" to "99:99:99:00:00:01")
        fingerprinter.reclassify = { it.copy(vendor = it.macAddress?.let { _ -> "FromFingerprint" }) }

        val devices = events(DiscoveryMode.Standard).finished().associateBy { it.ip }

        assertEquals("11:22:33:44:55:66", devices.getValue("192.168.1.10").macAddress)
        assertEquals("FromFingerprint", devices.getValue("192.168.1.10").vendor)
        assertEquals("AA:AA:AA:00:00:01", devices.getValue("192.168.1.11").macAddress)
        assertEquals("Own", devices.getValue("192.168.1.11").vendor)
    }

    @Test
    fun `NetBIOS names and classifies only devices with neither hostname nor OS`() = runTest {
        subnet.devices = listOf(
            LanDevice(ip = "192.168.1.20"),
            LanDevice(ip = "192.168.1.21", hostname = "known"),
        )
        netBios.results = mapOf(
            "192.168.1.20" to NetBiosInfo(name = "WINBOX"),
            "192.168.1.21" to NetBiosInfo(name = "SHOULD-NOT-APPLY"),
        )
        fingerprinter.netBiosOs = "Windows"

        val devices = events(DiscoveryMode.Standard).finished().associateBy { it.ip }

        val probed = devices.getValue("192.168.1.20")
        assertEquals("WINBOX", probed.hostname)
        assertEquals("Windows", probed.osGuess)
        assertEquals(DeviceFingerprinterImpl.CONFIDENCE_NETBIOS, probed.fingerprintConfidence)
        assertEquals(listOf("NetBIOS: WINBOX"), probed.fingerprintEvidence)
        assertEquals("known", devices.getValue("192.168.1.21").hostname)
    }

    @Test
    fun `a NetBIOS name without an OS classification adds no confidence or evidence`() = runTest {
        subnet.devices = listOf(LanDevice(ip = "192.168.1.22"))
        netBios.results = mapOf("192.168.1.22" to NetBiosInfo(name = "BOX"))

        val device = events(DiscoveryMode.Standard).finished().single()

        assertEquals("BOX", device.hostname)
        assertNull(device.osGuess)
        assertEquals(0, device.fingerprintConfidence)
        assertTrue(device.fingerprintEvidence.isEmpty())
    }

    @Test
    fun `NetBIOS probes run concurrently rather than one after another`() = runTest {
        subnet.devices = listOf(LanDevice(ip = "192.168.1.30"), LanDevice(ip = "192.168.1.31"))
        val bothStarted = CompletableDeferred<Unit>()
        var started = 0
        val prober = object : NetBiosProber {
            override suspend fun probe(ip: String): NetBiosInfo? {
                if (++started == 2) bothStarted.complete(Unit)
                bothStarted.await() // a sequential pipeline would wait here forever
                return null
            }
        }

        // Virtual time: a sequential pipeline never completes the deferred, so fail fast instead of hanging.
        withTimeout(1_000) { events(DiscoveryMode.Standard, pipeline(netBiosProber = prober)) }

        assertEquals(2, started)
    }

    // --- stage lists ---------------------------------------------------------------------------

    @Test
    fun `Standard reports discovery then ARP then NetBIOS`() = runTest {
        val progress = events(DiscoveryMode.Standard).filterIsInstance<Progress>()

        assertEquals(
            listOf(
                Progress(DiscoveryStage.Discovery, 0, 3),
                Progress(DiscoveryStage.Arp, 1, 3),
                Progress(DiscoveryStage.NetBios, 2, 3),
            ),
            progress,
        )
        assertEquals(listOf(DiscoveryStage.Discovery, DiscoveryStage.Arp, DiscoveryStage.NetBios), DiscoveryMode.Standard.stages)
    }

    @Test
    fun `Lite reports discovery then ARP and never touches mDNS SSDP or NetBIOS`() = runTest {
        var untouched = true
        val mdnsSpy = object : LanMdnsScanner {
            override fun discover(timeoutMs: Long): Flow<LanDevice> = flow { untouched = false }
        }
        val ssdpSpy = object : SsdpScanner {
            override fun discover(timeoutMs: Long): Flow<SsdpDevice> = flow { untouched = false }
        }
        val netBiosSpy = object : NetBiosProber {
            override suspend fun probe(ip: String): NetBiosInfo? { untouched = false; return null }
        }
        subnet.devices = listOf(LanDevice(ip = "192.168.1.40"))

        val all = events(DiscoveryMode.Lite, pipeline(mdnsScanner = mdnsSpy, ssdpScanner = ssdpSpy, netBiosProber = netBiosSpy))

        assertEquals(
            listOf(Progress(DiscoveryStage.Discovery, 0, 2), Progress(DiscoveryStage.Arp, 1, 2)),
            all.filterIsInstance<Progress>(),
        )
        assertEquals(listOf(DiscoveryStage.Discovery, DiscoveryStage.Arp), DiscoveryMode.Lite.stages)
        assertTrue(untouched, "Lite must not run mDNS, SSDP or NetBIOS")
    }

    @Test
    fun `Lite drops the stale ARP cache before its sweep and Standard leaves the cache to its caller`() = runTest {
        for (mode in DiscoveryMode.entries) {
            val calls = mutableListOf<String>()
            val scanner = object : SubnetScanner {
                override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow {
                    calls += "scan $subnet/$prefixLength"
                }
            }
            val arpSpy = object : ArpTableReader {
                override suspend fun getMacForIp(ip: String): String? = null
                override suspend fun getAll(): Map<String, String> = emptyMap<String, String>().also { calls += "read" }
                override fun invalidateCache() { calls += "invalidate" }
            }

            pipeline(subnetScanner = scanner, arpTableReader = arpSpy)
                .run(DiscoveryRequest(" 10.1.2.0/22 ", mode)).toList()

            val expected = when (mode) {
                DiscoveryMode.Lite -> listOf("invalidate", "scan 10.1.2.0/22", "read")
                DiscoveryMode.Standard -> listOf("scan 10.1.2.0/22", "read")
            }
            assertEquals(expected, calls, "mode $mode")
        }
    }

    @Test
    fun `the run ends with Finished carrying every device`() = runTest {
        subnet.devices = listOf(LanDevice(ip = "192.168.1.50"), LanDevice(ip = "192.168.1.51"))

        val all = events(DiscoveryMode.Standard)

        assertInstanceOf(Finished::class.java, all.last())
        assertEquals(setOf("192.168.1.50", "192.168.1.51"), all.finished().map { it.ip }.toSet())
        assertTrue(all.filterIsInstance<DeviceUpdated>().isNotEmpty())
    }

    // --- Lite sweep ----------------------------------------------------------------------------

    @Test
    fun `Lite enriches from ARP and the vendor database and keeps scan order`() = runTest {
        subnet.devices = listOf(
            LanDevice(ip = "192.168.1.60"),
            LanDevice(ip = "192.168.1.61", vendor = "Kept"),
            LanDevice(ip = "192.168.1.62", macAddress = "AA:BB:CC:00:00:01"),
        )
        arp.table = mapOf("192.168.1.60" to "11:22:33:44:55:66", "192.168.1.61" to "77:77:77:00:00:01")
        oui.table["11:22:33"] = "Acme"

        val devices = events(DiscoveryMode.Lite).finished()

        assertEquals(listOf("192.168.1.60", "192.168.1.61", "192.168.1.62"), devices.map { it.ip })
        assertEquals("11:22:33:44:55:66", devices[0].macAddress)
        assertEquals("Acme", devices[0].vendor)
        assertEquals("Kept", devices[1].vendor)
        assertEquals("AA:BB:CC:00:00:01", devices[2].macAddress)
    }

    @Test
    fun `a Lite ping failure propagates to the caller`() = runTest {
        subnet.error = IllegalStateException("socket")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { events(DiscoveryMode.Lite) }
        }
    }

    // --- failures and cancellation ---------------------------------------------------------------

    @Test
    fun `a Standard ping failure is reported and the other engines still deliver`() = runTest {
        subnet.error = IllegalStateException("socket")
        mdns.devices = listOf(LanDevice(ip = "192.168.1.70", hostname = "tv"))

        val all = events(DiscoveryMode.Standard)

        assertEquals(listOf(StageFailed(DiscoveryStage.Discovery, "socket")), all.filterIsInstance<StageFailed>())
        assertEquals(listOf("192.168.1.70"), all.finished().map { it.ip })
    }

    @Test
    fun `a ping failure without a message gets the default one`() = runTest {
        subnet.error = IllegalStateException()

        val failed = events(DiscoveryMode.Standard).filterIsInstance<StageFailed>().single()

        assertEquals("Ping sweep failed", failed.message)
    }

    @Test
    fun `mDNS and SSDP failures are swallowed without ending the run`() = runTest {
        mdns.error = IllegalStateException("mdns")
        ssdp.error = IllegalStateException("ssdp")
        subnet.devices = listOf(LanDevice(ip = "192.168.1.71"))

        val all = events(DiscoveryMode.Standard)

        assertTrue(all.filterIsInstance<StageFailed>().isEmpty())
        assertEquals(listOf("192.168.1.71"), all.finished().map { it.ip })
    }

    @Test
    fun `cancelling the collector cancels the engines mid-sweep`() = runTest {
        var sweepCancelled = false
        val hanging = object : SubnetScanner {
            override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow {
                try {
                    awaitCancellation()
                } finally {
                    sweepCancelled = true
                }
            }
        }
        val job = launch { events(DiscoveryMode.Standard, pipeline(subnetScanner = hanging)) }
        runCurrent()
        assertFalse(sweepCancelled)

        job.cancelAndJoin()

        assertTrue(sweepCancelled, "the ping sweep kept running after the scan was cancelled")
    }

    @Test
    fun `cancelling during NetBIOS enrichment cancels the probes`() = runTest {
        subnet.devices = listOf(LanDevice(ip = "192.168.1.80"))
        var probeCancelled = false
        val prober = object : NetBiosProber {
            override suspend fun probe(ip: String): NetBiosInfo? {
                try {
                    awaitCancellation()
                } finally {
                    probeCancelled = true
                }
            }
        }
        val job = launch { events(DiscoveryMode.Standard, pipeline(netBiosProber = prober)) }
        runCurrent()

        job.cancelAndJoin()

        assertTrue(probeCancelled)
    }

    @Test
    fun `an invalid CIDR is rejected rather than scanned`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { pipeline().run(DiscoveryRequest("not-a-cidr", DiscoveryMode.Standard)).toList() }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { pipeline().run(DiscoveryRequest("192.168.1.0/x", DiscoveryMode.Lite)).toList() }
        }
    }
}
