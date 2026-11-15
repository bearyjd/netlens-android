package com.ventouxlabs.netlens.core.scan.engine

import android.util.Log
import com.ventouxlabs.netlens.core.oui.OuiLookup
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryEvent.DeviceUpdated
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryEvent.Finished
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryEvent.Progress
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryEvent.StageFailed
import com.ventouxlabs.netlens.core.scan.model.DiscoveryMethod
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.MAX_MODEL_LENGTH
import com.ventouxlabs.netlens.core.scan.model.SsdpDevice
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class DeviceDiscoveryPipelineImpl @Inject constructor(
    private val subnetScanner: SubnetScanner,
    private val mdnsScanner: LanMdnsScanner,
    private val ssdpScanner: SsdpScanner,
    private val netBiosProber: NetBiosProber,
    private val arpTableReader: ArpTableReader,
    private val fingerprinter: DeviceFingerprinter,
    private val ouiLookup: OuiLookup,
) : DeviceDiscoveryPipeline {

    override fun run(request: DiscoveryRequest): Flow<DiscoveryEvent> = channelFlow {
        val parts = request.cidr.trim().split("/")
        require(parts.size == 2) { "Invalid CIDR: ${request.cidr}" }
        val prefixLength = requireNotNull(parts[1].toIntOrNull()) { "Invalid CIDR: ${request.cidr}" }
        arpTableReader.invalidateCache()
        when (request.mode) {
            DiscoveryMode.Lite -> runLite(parts[0], prefixLength)
            DiscoveryMode.Standard -> runStandard(parts[0], prefixLength)
        }
    }

    private suspend fun ProducerScope<DiscoveryEvent>.progress(mode: DiscoveryMode, stage: DiscoveryStage) {
        send(Progress(stage, mode.stages.indexOf(stage), mode.stages.size))
    }

    private suspend fun ProducerScope<DiscoveryEvent>.runLite(subnet: String, prefixLength: Int) {
        progress(DiscoveryMode.Lite, DiscoveryStage.Discovery)
        val reachable = subnetScanner.scan(subnet, prefixLength).toList()
        progress(DiscoveryMode.Lite, DiscoveryStage.Arp)
        val arp = arpTableReader.getAll()
        val enriched = reachable.map { device ->
            val mac = device.macAddress ?: arp[device.ip]
            val vendor = mac?.let { ouiLookup.lookup(it) } ?: device.vendor
            device.copy(macAddress = mac, vendor = vendor)
        }
        enriched.forEach { send(DeviceUpdated(it)) }
        send(Finished(enriched))
    }

    private suspend fun ProducerScope<DiscoveryEvent>.runStandard(subnet: String, prefixLength: Int) {
        val discovered = LinkedHashMap<String, LanDevice>()
        val lock = Mutex()

        // Sent under the lock so two concurrent updates of one IP reach the collector in the order
        // they were applied: the collector replaces by IP, so a reordered pair would leave it stale.
        suspend fun store(ip: String, update: (LanDevice?) -> LanDevice) {
            lock.withLock {
                val device = update(discovered[ip])
                discovered[device.ip] = device
                send(DeviceUpdated(device))
            }
        }

        suspend fun merge(device: LanDevice) {
            // fingerprint() re-derives deviceType/osGuess from hostname/services alone, so a
            // device that already arrived with a real classification (e.g. SSDP, confidence
            // CONFIDENCE_SSDP) must not have that silently overwritten by a weaker re-guess —
            // keep whichever of the two is actually stronger.
            val reclassified = fingerprinter.fingerprint(device)
            val fingerprinted = strongerFingerprint(device, reclassified)
            store(fingerprinted.ip) { existing ->
                if (existing != null) strongerFingerprint(existing, fingerprinted) else fingerprinted
            }
        }

        progress(DiscoveryMode.Standard, DiscoveryStage.Discovery)
        coroutineScope {
            launch {
                subnetScanner.scan(subnet, prefixLength)
                    .catch { e -> send(StageFailed(DiscoveryStage.Discovery, e.message ?: "Ping sweep failed")) }
                    .collect { device -> merge(device) }
            }
            launch {
                mdnsScanner.discover()
                    .catch { e -> Log.d(LOG_TAG, "mDNS discovery failed: ${e.message}") }
                    .collect { device -> merge(device) }
            }
            launch {
                ssdpScanner.discover()
                    .catch { e -> Log.d(LOG_TAG, "SSDP discovery failed: ${e.message}") }
                    .collect { ssdpDevice -> merge(ssdpDevice.toLanDevice()) }
            }
        }

        progress(DiscoveryMode.Standard, DiscoveryStage.Arp)
        val arpTable = arpTableReader.getAll()
        for (device in lock.withLock { discovered.values.toList() }) {
            val mac = device.macAddress ?: arpTable[device.ip]
            if (mac != null && device.macAddress == null) {
                val enriched = fingerprinter.fingerprint(device.copy(macAddress = mac))
                store(enriched.ip) { current ->
                    // The ip key is the same as enriched.ip, so current is the device we read above
                    // unless a late engine update landed in between; enrich whatever is there now.
                    (current ?: device).let {
                        it.copy(macAddress = enriched.macAddress, vendor = it.vendor ?: enriched.vendor)
                    }
                }
            }
        }

        progress(DiscoveryMode.Standard, DiscoveryStage.NetBios)
        coroutineScope {
            lock.withLock { discovered.values.toList() }
                .filter { it.hostname == null && it.osGuess == null }
                .forEach { device ->
                    launch {
                        val nbInfo = netBiosProber.probe(device.ip) ?: return@launch
                        val os = fingerprinter.classifyFromNetBios(nbInfo)
                        store(device.ip) { current ->
                            (current ?: device).let {
                                it.copy(
                                    hostname = it.hostname ?: nbInfo.name,
                                    osGuess = it.osGuess ?: os,
                                    fingerprintConfidence = if (os != null) {
                                        maxOf(it.fingerprintConfidence, DeviceFingerprinterImpl.CONFIDENCE_NETBIOS)
                                    } else {
                                        it.fingerprintConfidence
                                    },
                                    fingerprintEvidence = if (os != null) {
                                        (it.fingerprintEvidence + "NetBIOS: ${nbInfo.name}").distinct()
                                    } else {
                                        it.fingerprintEvidence
                                    },
                                )
                            }
                        }
                    }
                }
        }

        send(Finished(lock.withLock { discovered.values.toList() }))
    }

    private fun SsdpDevice.toLanDevice(): LanDevice {
        val (type, os) = fingerprinter.classifyFromSsdp(this)
        return LanDevice(
            ip = ip,
            hostname = friendlyName,
            isReachable = true,
            discoveryMethod = DiscoveryMethod.SSDP,
            deviceType = type,
            osGuess = os,
            vendor = manufacturer,
            model = modelName?.take(MAX_MODEL_LENGTH),
            fingerprintConfidence = if (type != null || os != null) {
                DeviceFingerprinterImpl.CONFIDENCE_SSDP
            } else {
                DeviceFingerprinterImpl.CONFIDENCE_NONE
            },
            fingerprintEvidence = listOfNotNull(deviceType?.let { "SSDP: $it" }),
        )
    }

    private companion object {
        const val LOG_TAG = "LanScan"
    }
}
