package us.beary.netlens.feature.httptester.engine

import io.ktor.client.HttpClient
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
import us.beary.netlens.feature.httptester.model.HttpMethod
import us.beary.netlens.feature.httptester.model.HttpRequestConfig
import us.beary.netlens.feature.httptester.model.HttpResponseResult
import us.beary.netlens.core.network.SsrfGuard
import java.io.Closeable
import java.net.URL
import javax.inject.Inject

class HttpRequesterImpl @Inject constructor() : HttpRequester, Closeable {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
        expectSuccess = false
    }

    override suspend fun execute(config: HttpRequestConfig): HttpResponseResult {
        val url = config.url.trim()
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "URL must start with http:// or https://"
        }

        val parsedUrl = URL(url)
        val host = parsedUrl.host
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
    }
}
