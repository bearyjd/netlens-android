package com.ventouxlabs.netlens.feature.httptester.engine

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod as KtorHttpMethod
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readString
import com.ventouxlabs.netlens.feature.httptester.model.HttpMethod
import com.ventouxlabs.netlens.feature.httptester.model.HttpRequestConfig
import com.ventouxlabs.netlens.feature.httptester.model.HttpResponseResult
import com.ventouxlabs.netlens.core.network.SsrfGuard
import java.io.Closeable
import java.net.URL
import javax.inject.Inject

class HttpRequesterImpl private constructor(
    private val client: HttpClient,
) : HttpRequester, Closeable {

    @Inject constructor() : this(HttpClient(CIO) { configureSecureDefaults() })

    /** Visible for testing: allows swapping in a [io.ktor.client.engine.mock.MockEngine]. */
    internal constructor(engine: HttpClientEngine) : this(HttpClient(engine) { configureSecureDefaults() })

    override suspend fun execute(config: HttpRequestConfig): HttpResponseResult {
        val url = config.url.trim()
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "URL must start with http:// or https://"
        }

        val parsedUrl = URL(url)
        val host = parsedUrl.host
        // SsrfGuard resolves every A/AAAA record and rejects the host if *any* record is
        // private/loopback/link-local, which blocks the common DNS-rebinding payloads.
        // Residual risk: the Ktor CIO engine re-resolves the hostname when it opens the
        // connection, so a low-TTL record that flips to a private address in the window
        // between this check and connect is not fully closed. True per-request IP pinning
        // is not exposed by CIO in Ktor 3.0.3 without swapping the engine, which is out of
        // scope here; the any-record-private rejection above is the mitigation.
        require(!SsrfGuard.isPrivateOrLoopback(host)) {
            "Requests to private or loopback network addresses are not allowed"
        }

        val startNanos = System.nanoTime()

        try {
            val response = client.request(url) {
                method = config.method.toKtorMethod()
                headers {
                    config.headers.forEach { (key, value) ->
                        append(key, value)
                    }
                }
                if (config.body != null && config.method.supportsBody()) {
                    setBody(config.body)
                }
            }

            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000

            val responseHeaders = response.headers.entries().associate { (key, values) ->
                key to values
            }

            val body = response.bodyAsChannel().readRemaining(MAX_RESPONSE_BODY_BYTES).readString()

            return HttpResponseResult(
                statusCode = response.status.value,
                statusDescription = response.status.description,
                headers = responseHeaders,
                body = body,
                latencyMs = elapsedMs,
                contentLength = response.headers["Content-Length"]?.toLongOrNull(),
            )
        } catch (e: CancellationException) {
            throw e
        }
    }

    override fun close() {
        client.close()
    }

    private fun HttpMethod.toKtorMethod(): KtorHttpMethod = when (this) {
        HttpMethod.GET -> KtorHttpMethod.Get
        HttpMethod.POST -> KtorHttpMethod.Post
        HttpMethod.PUT -> KtorHttpMethod.Put
        HttpMethod.DELETE -> KtorHttpMethod.Delete
        HttpMethod.PATCH -> KtorHttpMethod.Patch
        HttpMethod.HEAD -> KtorHttpMethod.Head
    }

    private fun HttpMethod.supportsBody(): Boolean = when (this) {
        HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH -> true
        HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD -> false
    }

    companion object {
        private const val MAX_RESPONSE_BODY_BYTES = 1_048_576L // 1 MB
        private const val TIMEOUT_MS = 15_000L

        private fun HttpClientConfig<*>.configureSecureDefaults() {
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MS
                connectTimeoutMillis = TIMEOUT_MS
                socketTimeoutMillis = TIMEOUT_MS
            }
            expectSuccess = false
            // SSRF hardening: SsrfGuard only validates the user-entered host before the
            // first request. Ktor auto-follows redirects by default with no re-validation,
            // so a malicious/compromised endpoint could 302 the client into a private or
            // loopback address. Disabling auto-follow means any 3xx is returned as-is to
            // the caller; following a redirect the user sees re-enters execute() and goes
            // through the same SsrfGuard check on the new host.
            followRedirects = false
        }
    }
}
