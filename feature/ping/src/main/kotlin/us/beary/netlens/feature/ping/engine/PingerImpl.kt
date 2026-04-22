package us.beary.netlens.feature.ping.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import us.beary.netlens.feature.ping.model.PingResult
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

    override fun ping(host: String, count: Int): Flow<PingResult> = callbackFlow {
        val sanitized = validateHost(host)
        val process = withContext(Dispatchers.IO) {
            ProcessBuilder("ping", "-c", count.toString(), sanitized)
                .redirectErrorStream(true)
                .start()
        }

        try {
            withContext(Dispatchers.IO) {
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!isActive) break
                        val result = PingOutputParser.parseReplyLine(line)
                        if (result != null) {
                            trySend(result)
                        }
                    }
                }
            }
            channel.close()
        } catch (e: kotlinx.coroutines.CancellationException) {
            channel.close()
            throw e
        } catch (e: Exception) {
            channel.close(e)
        } finally {
            process.destroy()
        }

        awaitClose {
            process.destroy()
        }
    }
}
