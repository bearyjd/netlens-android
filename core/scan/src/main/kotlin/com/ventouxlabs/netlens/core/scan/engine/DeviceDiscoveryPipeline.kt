package com.ventouxlabs.netlens.core.scan.engine

import com.ventouxlabs.netlens.core.scan.model.LanDevice
import kotlinx.coroutines.flow.Flow

/** The phases a discovery run moves through, in the order [DiscoveryMode.stages] lists them. */
enum class DiscoveryStage { Discovery, Arp, NetBios }

enum class DiscoveryMode(val stages: List<DiscoveryStage>) {
    /** Background-watch sweep: ping + ARP + OUI. No NetBIOS/SSDP/mDNS, to bound battery cost. */
    Lite(listOf(DiscoveryStage.Discovery, DiscoveryStage.Arp)),

    /** The foreground scan: ping, mDNS and SSDP together, then ARP and NetBIOS enrichment. */
    Standard(listOf(DiscoveryStage.Discovery, DiscoveryStage.Arp, DiscoveryStage.NetBios)),
}

/** [cidr] is `address/prefix`; callers validate its range, the pipeline only splits it. */
data class DiscoveryRequest(val cidr: String, val mode: DiscoveryMode)

sealed interface DiscoveryEvent {
    /** [device] is the full merged reading for its IP and replaces any earlier one. */
    data class DeviceUpdated(val device: LanDevice) : DiscoveryEvent

    /** Sent as each stage starts: [done] stages have completed out of [total]. */
    data class Progress(val stage: DiscoveryStage, val done: Int, val total: Int) : DiscoveryEvent

    /** A stage's sweep failed but the run carries on (Standard only; Lite propagates). */
    data class StageFailed(val stage: DiscoveryStage, val message: String?) : DiscoveryEvent

    data class Finished(val devices: List<LanDevice>) : DiscoveryEvent
}

/**
 * The discovery engines behind the LAN scan and the background watch, in one place.
 *
 * Network binding ([LanNetworkBinder]) and network identity are the caller's: collect [run] inside
 * the binding so every probe leaves over the LAN. Cancelling the collector cancels every engine.
 */
interface DeviceDiscoveryPipeline {
    fun run(request: DiscoveryRequest): Flow<DiscoveryEvent>
}
