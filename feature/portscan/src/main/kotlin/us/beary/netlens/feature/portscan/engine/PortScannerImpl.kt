package us.beary.netlens.feature.portscan.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import us.beary.netlens.feature.portscan.model.PortResult
import us.beary.netlens.feature.portscan.model.WellKnownPorts
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject

class PortScannerImpl @Inject constructor() : PortScanner {

    override fun scan(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
    ): Flow<PortResult> = channelFlow {
        val batches = ports.chunked(BATCH_SIZE)
        for (batch in batches) {
            coroutineScope {
                val jobs = batch.map { port ->
                    async {
                        scanPort(host, port, timeoutMs)
                    }
                }
                for (job in jobs) {
                    send(job.await())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun scanPort(host: String, port: Int, timeoutMs: Int): PortResult {
        val serviceName = WellKnownPorts.getServiceName(port)
        val startTime = System.nanoTime()
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            val latencyMs = (System.nanoTime() - startTime) / NS_PER_MS
            PortResult(
                port = port,
                serviceName = serviceName,
                isOpen = true,
                latencyMs = latencyMs,
            )
        } catch (_: IOException) {
            PortResult(
                port = port,
                serviceName = serviceName,
                isOpen = false,
                latencyMs = 0,
            )
        } finally {
            try {
                socket?.close()
            } catch (_: IOException) {
                // Ignore close errors
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 50
        const val NS_PER_MS = 1_000_000L
    }
}
