package com.ventoux.netlens.feature.monitor.engine

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.head
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import com.ventoux.netlens.core.data.model.EndpointCheck
import com.ventoux.netlens.core.network.SsrfGuard
import java.io.Closeable
import java.net.URL
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class EndpointCheckerImpl @Inject constructor() : EndpointChecker, Closeable {

    private val client = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        expectSuccess = false
    }

    override suspend fun check(url: String): EndpointCheck {
        val trimmedUrl = url.trim()
        require(trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            "URL must start with http:// or https://"
        }

        val host = URL(trimmedUrl).host
        require(!SsrfGuard.isPrivateOrLoopback(host)) {
            "Requests to private or loopback network addresses are not allowed"
        }

        val startNanos = System.nanoTime()

        return try {
            val response = tryHead(trimmedUrl)
            val latencyMs = (System.nanoTime() - startNanos) / 1_000_000
            val statusCode = response.status.value
            val isSuccess = statusCode in 200..399

            EndpointCheck(
                endpointId = 0,
                statusCode = statusCode,
                latencyMs = latencyMs,
                isSuccess = isSuccess,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val latencyMs = (System.nanoTime() - startNanos) / 1_000_000
            EndpointCheck(
                endpointId = 0,
                statusCode = 0,
                latencyMs = latencyMs,
                isSuccess = false,
                errorMessage = e.message ?: "Unknown error",
            )
        }
    }

    override fun close() {
        client.close()
    }

    private suspend fun tryHead(url: String): HttpResponse {
        return try {
            client.head(url)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            client.get(url)
        }
    }
}
