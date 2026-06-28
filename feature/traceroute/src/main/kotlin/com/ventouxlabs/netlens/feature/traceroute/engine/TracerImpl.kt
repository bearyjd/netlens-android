package com.ventouxlabs.netlens.feature.traceroute.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import com.ventouxlabs.netlens.feature.traceroute.model.TracerouteHop
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class TracerImpl @Inject constructor() : Tracer {

    private companion object {
        val HOST_PATTERN = Regex("^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\$")
        val FROM_REGEX = Regex("""from\s+(\S+).*time=(\d+\.?\d*)\s*ms""")
        val EXCEEDED_REGEX = Regex("""From\s+(\S+)\s.*[Tt]ime to live exceeded""")
    }

    private fun validateHost(host: String): String {
        require(host.isNotBlank() && host.length <= 253 && !host.startsWith("-") && HOST_PATTERN.matches(host)) {
            "Invalid host"
        }
        return host
    }

    override fun trace(host: String, maxHops: Int): Flow<TracerouteHop> = flow {
        val sanitized = validateHost(host)
        val targetIp = try {
            java.net.InetAddress.getByName(sanitized).hostAddress
        } catch (_: Exception) {
            sanitized
        }

        for (ttl in 1..maxHops) {
            if (!coroutineContext.isActive) break

            val process = ProcessBuilder("ping", "-c", "1", "-t", ttl.toString(), "-W", "2", "--", sanitized)
                .redirectErrorStream(true)
                .start()

            val output: String
            try {
                output = process.inputStream.bufferedReader().readText()
                process.waitFor()
            } finally {
                process.destroyForcibly()
            }

            val hop = parseHopOutput(ttl, output, sanitized)
            emit(hop)

            if (!hop.isTimeout && hop.ip == targetIp) break
        }
    }.flowOn(Dispatchers.IO)

    // ICMP TTL-exceeded replies don't include time= so intermediate hops have no RTT data
    private fun parseHopOutput(ttl: Int, output: String, targetHost: String): TracerouteHop {
        FROM_REGEX.find(output)?.let { match ->
            val ip = match.groupValues[1]
            val rtt = match.groupValues[2].toFloatOrNull() ?: 0f
            return TracerouteHop(
                hopNumber = ttl,
                ip = ip,
                rttMs = listOf(rtt),
            )
        }

        EXCEEDED_REGEX.find(output)?.let { match ->
            val ip = match.groupValues[1]
            return TracerouteHop(
                hopNumber = ttl,
                ip = ip,
                rttMs = emptyList(),
            )
        }

        return TracerouteHop(
            hopNumber = ttl,
            isTimeout = true,
        )
    }
}
