package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository
import com.ventouxlabs.netlens.core.scan.engine.DeviceDiscoveryPipeline
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryEvent
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryMode
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryRequest
import com.ventouxlabs.netlens.core.scan.network.NetworkContext
import com.ventouxlabs.netlens.core.scan.network.NetworkIdentity
import com.ventouxlabs.netlens.core.scan.network.NetworkKeyCanonicalizer
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface WatchOutcome {
    data object NoGateway : WatchOutcome
    data object NotWatched : WatchOutcome
    data class Swept(val deviceCount: Int) : WatchOutcome
}

/**
 * The full background-watch logic, injectable and testable without WorkManager.
 * Lite sweep only: ping sweep + ARP + OUI. No NetBIOS/SSDP/mDNS, to bound battery cost.
 */
class WatchRunner @Inject constructor(
    private val networkIdentity: NetworkIdentity,
    private val watchedNetworkDao: WatchedNetworkDao,
    private val discoveryPipeline: DeviceDiscoveryPipeline,
    private val deviceInventoryRepository: DeviceInventoryRepository,
    private val networkKeyCanonicalizer: NetworkKeyCanonicalizer,
) {
    suspend fun run(): WatchOutcome {
        val keys = networkIdentity.currentNetworkKeys()
        if (keys.isEmpty()) return WatchOutcome.NoGateway
        // Only a gateway MAC / gateway id (or an alias of one) can match a watched network: the l3
        // hint is shared by countless networks, so an l3-only match would sweep a hotel into the home inventory.
        // Unvouched keys (none strong, or strong keys that disagree) match nothing.
        val current = networkKeyCanonicalizer.currentIdentity(keys) ?: return WatchOutcome.NotWatched
        val watched = watchedNetworkDao.getAll().firstOrNull { networkKeyCanonicalizer.canonicalForGatewayMac(it.gatewayMac) == current }
        if (watched == null || !watched.watchEnabled) return WatchOutcome.NotWatched

        val subnetCidr = networkIdentity.currentSubnet() ?: watched.subnet
        val (subnet, prefix) = parseCidr(subnetCidr) ?: return WatchOutcome.NotWatched

        val enriched = discoveryPipeline.run(DiscoveryRequest("$subnet/$prefix", DiscoveryMode.Lite))
            .filterIsInstance<DiscoveryEvent.Finished>()
            .first()
            .devices
        deviceInventoryRepository.persistScan(enriched, networkId = watched.id, network = NetworkContext(keys, networkIdentity.currentNetworkLabel()))
        return WatchOutcome.Swept(enriched.size)
    }

    private fun parseCidr(cidr: String): Pair<String, Int>? {
        val parts = cidr.trim().split("/")
        if (parts.size != 2) return null
        val prefix = parts[1].toIntOrNull() ?: return null
        if (prefix < 16 || prefix > 30) return null
        val octets = parts[0].split(".")
        if (octets.size != 4 || octets.any { o -> o.toIntOrNull()?.let { it in 0..255 } != true }) return null
        return parts[0] to prefix
    }
}
