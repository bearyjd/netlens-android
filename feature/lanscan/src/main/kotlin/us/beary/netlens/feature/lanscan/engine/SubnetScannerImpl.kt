package us.beary.netlens.feature.lanscan.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import us.beary.netlens.core.oui.OuiLookup
import us.beary.netlens.feature.lanscan.model.LanDevice
import java.net.InetAddress
import javax.inject.Inject

class SubnetScannerImpl @Inject constructor(
    private val ouiLookup: OuiLookup,
    private val fingerprinter: DeviceFingerprinter,
) : SubnetScanner {

    override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow {
        val ipAddresses = calculateIpRange(subnet, prefixLength)
        val batchSize = 50
        val batches = ipAddresses.chunked(batchSize)

        for ((batchIndex, batch) in batches.withIndex()) {
            val reachableIps = pingBatch(batch)
            val arpTable = ArpTableReader.read()

            for (ip in reachableIps) {
                val mac = arpTable[ip]
                val vendor = mac?.let { ouiLookup.lookup(it) }
                val hostname = resolveHostname(ip)

                val device = LanDevice(
                    ip = ip,
                    mac = mac,
                    vendor = vendor,
                    hostname = hostname,
                    isReachable = true,
                    latencyMs = 0,
                )
                emit(fingerprinter.fingerprint(device))
            }

            // Also emit unreachable devices found in ARP table from this batch
            for (ip in batch) {
                if (ip !in reachableIps && arpTable.containsKey(ip)) {
                    val mac = arpTable[ip]
                    val vendor = mac?.let { ouiLookup.lookup(it) }

                    val device = LanDevice(
                        ip = ip,
                        mac = mac,
                        vendor = vendor,
                        hostname = null,
                        isReachable = false,
                        latencyMs = 0,
                    )
                    emit(fingerprinter.fingerprint(device))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun pingBatch(ips: List<String>): Set<String> = coroutineScope {
        val results = ips.map { ip ->
            async {
                val reachable = runCatching {
                    InetAddress.getByName(ip).isReachable(PING_TIMEOUT_MS)
                }.getOrDefault(false)
                if (reachable) ip else null
            }
        }
        results.mapNotNull { it.await() }.toSet()
    }

    private fun resolveHostname(ip: String): String? = runCatching {
        val address = InetAddress.getByName(ip)
        val hostname = address.canonicalHostName
        // InetAddress returns the IP if hostname can't be resolved
        if (hostname != ip) hostname else null
    }.getOrNull()

    companion object {
        private const val PING_TIMEOUT_MS = 200

        internal fun calculateIpRange(subnet: String, prefixLength: Int): List<String> {
            val parts = subnet.split(".")
            if (parts.size != 4) return emptyList()

            val ipInt = parts.fold(0L) { acc, part ->
                (acc shl 8) or (part.toIntOrNull()?.toLong() ?: return emptyList())
            }

            val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
            val network = ipInt and mask
            val broadcast = network or mask.inv() and 0xFFFFFFFFL
            val hostCount = (broadcast - network - 1).toInt()

            if (hostCount <= 0 || hostCount > MAX_HOSTS) return emptyList()

            return (1..hostCount).map { offset ->
                val addr = network + offset
                "${(addr shr 24) and 0xFF}.${(addr shr 16) and 0xFF}.${(addr shr 8) and 0xFF}.${addr and 0xFF}"
            }
        }

        private const val MAX_HOSTS = 1024
    }
}
