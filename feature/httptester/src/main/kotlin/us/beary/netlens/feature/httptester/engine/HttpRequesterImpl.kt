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
import java.io.Closeable
import java.net.InetAddress
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
        require(!isPrivateOrLoopback(host)) {
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

private fun isPrivateOrLoopback(host: String): Boolean {
    if (host.equals("localhost", ignoreCase = true)) return true

    val addresses = try {
        InetAddress.getAllByName(host)
    } catch (_: Exception) {
        return false
    }

    return addresses.any { addr ->
        addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            isCarrierGradeNatOrUniqueLocal(addr)
    }
}

private fun isCarrierGradeNatOrUniqueLocal(addr: InetAddress): Boolean {
    val bytes = addr.address
    if (bytes.size == 4) {
        // 169.254.0.0/16 is covered by isLinkLocalAddress
        // 10.0.0.0/8 and 192.168.0.0/16 and 172.16.0.0/12 are covered by isSiteLocalAddress
        // Nothing extra needed for IPv4 — the JDK helpers cover all private ranges
        return false
    }
    if (bytes.size == 16) {
        // fc00::/7 (unique local) — first byte 0xFC or 0xFD
        val first = bytes[0].toInt() and 0xFF
        if (first == 0xFC || first == 0xFD) return true
        // fe80::/10 is covered by isLinkLocalAddress
        // ::1 is covered by isLoopbackAddress
    }
    return false
}
