package us.beary.netlens.feature.httptester.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod as KtorHttpMethod
import us.beary.netlens.feature.httptester.model.HttpMethod
import us.beary.netlens.feature.httptester.model.HttpRequestConfig
import us.beary.netlens.feature.httptester.model.HttpResponseResult
import javax.inject.Inject

class HttpRequesterImpl @Inject constructor() : HttpRequester {

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

        val startNanos = System.nanoTime()

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

        return HttpResponseResult(
            statusCode = response.status.value,
            statusDescription = response.status.description,
            headers = responseHeaders,
            body = response.bodyAsText(),
            latencyMs = elapsedMs,
            contentLength = response.headers["Content-Length"]?.toLongOrNull(),
        )
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
}
