package com.ventoux.netlens.feature.ping.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ventoux.netlens.feature.ping.model.PingResult
import java.io.IOException
import javax.inject.Inject

class PingerImpl @Inject constructor() : Pinger {

    private companion object {
        val HOST_PATTERN = Regex("^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\$")
    }

    private fun validateHost(host: String): String {
        require(host.isNotBlank() && host.length <= 253 && !host.startsWith("-") && HOST_PATTERN.matches(host)) {
            "Invalid host: must be a valid hostname or IPv4 address (IPv6 not supported)"
        }
        return host
    }

    override fun ping(host: String, count: Int): Flow<PingResult> {
        val sanitized = validateHost(host)
        return runPingProcess(listOf("ping", "-c", count.toString(), "--", sanitized))
    }

    override fun pingContinuous(host: String): Flow<PingResult> {
        val sanitized = validateHost(host)
        return runPingProcess(listOf("ping", "-i", "1", "--", sanitized))
    }

    private fun runPingProcess(args: List<String>): Flow<PingResult> = callbackFlow {
        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
        }

        launch(Dispatchers.IO) {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val result = PingOutputParser.parseReplyLine(line)
                        if (result != null) {
                            send(result)
                        }
                    }
                }
                channel.close()
            } catch (_: IOException) {
                channel.close()
            }
        }

        awaitClose {
            process.destroy()
        }
    }
}
