package com.ventouxlabs.netlens.feature.monitor.engine

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.head
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import com.ventouxlabs.netlens.core.data.model.EndpointCheck
import com.ventouxlabs.netlens.core.network.SsrfGuard
import java.io.Closeable
import java.net.URL
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class EndpointCheckerImpl private constructor(
    private val client: HttpClient,
) : EndpointChecker, Closeable {

    @Inject constructor() : this(HttpClient(CIO) { configureSecureDefaults() })

    /** Visible for testing: allows swapping in a [io.ktor.client.engine.mock.MockEngine]. */
    internal constructor(engine: HttpClientEngine) : this(HttpClient(engine) { configureSecureDefaults() })

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

    companion object {
        private const val TIMEOUT_MS = 10_000L

        private fun HttpClientConfig<*>.configureSecureDefaults() {
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MS
                connectTimeoutMillis = TIMEOUT_MS
                socketTimeoutMillis = TIMEOUT_MS
            }
            expectSuccess = false
            // SSRF hardening: SsrfGuard only validates the user-entered host before the
            // first request. Ktor auto-follows redirects by default with no re-validation,
            // so a monitored endpoint that starts 302-ing could redirect the client into a
            // private or loopback address. Disabling auto-follow means any 3xx is returned
            // as-is; check() treats a 3xx status as a reachable/successful check without
            // ever contacting the redirect target.
            followRedirects = false
        }
    }
}
