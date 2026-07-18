package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.oui.OuiLookup
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository
import com.ventouxlabs.netlens.core.scan.engine.ArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.SubnetScanner
import kotlinx.coroutines.flow.toList
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
    private val subnetScanner: SubnetScanner,
    private val arpTableReader: ArpTableReader,
    private val ouiLookup: OuiLookup,
    private val deviceInventoryRepository: DeviceInventoryRepository,
) {
    suspend fun run(): WatchOutcome {
        val gatewayMac = networkIdentity.currentGatewayMac() ?: return WatchOutcome.NoGateway
        val watched = watchedNetworkDao.getByGatewayMac(gatewayMac)
        if (watched == null || !watched.watchEnabled) return WatchOutcome.NotWatched

        val subnetCidr = networkIdentity.currentSubnet() ?: watched.subnet
        val (subnet, prefix) = parseCidr(subnetCidr) ?: return WatchOutcome.NotWatched

        arpTableReader.invalidateCache()
        val reachable = subnetScanner.scan(subnet, prefix).toList()
        val arp = arpTableReader.getAll()
        val enriched = reachable.map { device ->
            val mac = device.macAddress ?: arp[device.ip]
            val vendor = mac?.let { ouiLookup.lookup(it) } ?: device.vendor
            device.copy(macAddress = mac, vendor = vendor)
        }
        deviceInventoryRepository.persistScan(enriched, networkId = watched.id)
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
