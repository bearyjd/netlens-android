package com.ventoux.netlens.feature.speedtest.engine

import com.ventoux.netlens.feature.speedtest.model.SpeedProgress
import com.ventoux.netlens.feature.speedtest.model.SpeedTestPhase
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.head
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlin.random.Random

class SpeedTestEngineImpl @Inject constructor() : SpeedTestEngine {

    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = TIMEOUT_MS
        }
    }

    override fun measureDownload(): Flow<SpeedProgress> = flow {
        val url = "$BASE_URL/__down?bytes=$DOWNLOAD_BYTES"
        var totalBytes = 0L
        val startTime = System.currentTimeMillis()
        val buffer = ByteArray(BUFFER_SIZE)

        client.prepareGet(url).execute { response ->
            val channel = response.bodyAsChannel()
            while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read <= 0) break
                totalBytes += read
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > 0) {
                    val speedMbps = (totalBytes * 8f) / (elapsed * 1000f)
                    emit(
                        SpeedProgress(
                            bytesTransferred = totalBytes,
                            elapsedMs = elapsed,
                            speedMbps = speedMbps,
                            phase = SpeedTestPhase.DOWNLOAD,
                        ),
                    )
                }
            }
        }
    }

    override fun measureUpload(): Flow<SpeedProgress> = flow {
        val url = "$BASE_URL/__up"
        val payload = Random.nextBytes(UPLOAD_BYTES.toInt())
        var totalBytes = 0L
        val startTime = System.currentTimeMillis()

        client.preparePost(url) {
            contentType(ContentType.Application.OctetStream)
            setBody(payload)
        }.execute {
            totalBytes = payload.size.toLong()
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 0) {
                val speedMbps = (totalBytes * 8f) / (elapsed * 1000f)
                emit(
                    SpeedProgress(
                        bytesTransferred = totalBytes,
                        elapsedMs = elapsed,
                        speedMbps = speedMbps,
                        phase = SpeedTestPhase.UPLOAD,
                    ),
                )
            }
        }
    }

    override suspend fun measureLatency(): Long {
        val times = mutableListOf<Long>()
        repeat(LATENCY_SAMPLES) {
            val start = System.currentTimeMillis()
            client.head("$BASE_URL/__down?bytes=0")
            val elapsed = System.currentTimeMillis() - start
            times.add(elapsed)
        }
        return if (times.isNotEmpty()) times.sorted().let { it[it.size / 2] } else 0L
    }

    companion object {
        const val BASE_URL = "https://speed.cloudflare.com"
        private const val DOWNLOAD_BYTES = 25_000_000L
        private const val UPLOAD_BYTES = 10_000_000L
        private const val BUFFER_SIZE = 65_536
        private const val TIMEOUT_MS = 30_000L
        private const val LATENCY_SAMPLES = 5
    }
}
