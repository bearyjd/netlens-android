package com.ventouxlabs.netlens.core.scan.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import com.ventouxlabs.netlens.core.network.DisplayText
import com.ventouxlabs.netlens.core.scan.model.DiscoveryMethod
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import java.net.InetAddress
import javax.inject.Inject

class SubnetScannerImpl @Inject constructor() : SubnetScanner {

    override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow {
        val ipAddresses = calculateIpRange(subnet, prefixLength)
        val reachableDevices = pingSweep(ipAddresses)
        for (device in reachableDevices) {
            emit(device)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun pingSweep(ips: List<String>): List<LanDevice> = coroutineScope {
        val semaphore = Semaphore(64)
        ips.map { ip ->
            async {
                semaphore.withPermit {
                    val startTime = System.currentTimeMillis()
                    val reachable = withTimeoutOrNull(PING_TIMEOUT_MS.toLong()) {
                        runCatching { InetAddress.getByName(ip).isReachable(PING_TIMEOUT_MS) }
                            .getOrDefault(false)
                    } ?: false
                    if (reachable) {
                        val latency = System.currentTimeMillis() - startTime
                        val hostname = resolveHostname(ip)
                        LanDevice(
                            ip = ip,
                            hostname = hostname,
                            isReachable = true,
                            latencyMs = latency,
                            discoveryMethod = DiscoveryMethod.PING,
                        )
                    } else null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun resolveHostname(ip: String): String? = runCatching {
        val address = InetAddress.getByName(ip)
        sanitizeResolvedHostname(ip = ip, resolved = address.canonicalHostName)
    }.getOrNull()

    companion object {
        private const val PING_TIMEOUT_MS = 200

        /**
         * A reverse-DNS result is network-supplied (a PTR record anyone on the LAN's DNS path can
         * shape), so it is flattened at ingestion — every downstream sink (Compose, the
         * new-device notification, Room, exports) inherits the sanitised value instead of each
         * sink defending itself. A resolution that merely echoes the IP is not a name.
         */
        internal fun sanitizeResolvedHostname(ip: String, resolved: String): String? =
            if (resolved != ip) DisplayText.flatten(resolved) else null

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
