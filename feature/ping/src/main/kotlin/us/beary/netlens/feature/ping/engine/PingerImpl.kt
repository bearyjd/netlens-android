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

    override fun ping(host: String, count: Int): Flow<PingResult> = callbackFlow {
        val process = withContext(Dispatchers.IO) {
            ProcessBuilder("ping", "-c", count.toString(), host)
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
