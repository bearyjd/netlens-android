package com.ventouxlabs.netlens.feature.httptester.engine

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import com.ventouxlabs.netlens.feature.httptester.model.HttpMethod
import com.ventouxlabs.netlens.feature.httptester.model.HttpRequestConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HttpRequesterImplTest {

    @Test
    fun `normal 200 response flows through execute correctly`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("example.com", request.url.host)
            respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val requester = HttpRequesterImpl(engine)
        val result = requester.execute(
            HttpRequestConfig(
                url = "https://example.com/status",
                method = HttpMethod.GET,
                headers = emptyMap(),
                body = null,
            ),
        )

        assertEquals(HttpStatusCode.OK.value, result.statusCode)
        assertEquals("""{"ok":true}""", result.body)
        assertTrue(result.headers[HttpHeaders.ContentType]?.contains("application/json") == true)
    }

    @Test
    fun `does not follow redirect into private address`() = runTest {
        var privateHostContacted = false

        val engine = MockEngine { request ->
            if (request.url.host == PRIVATE_HOST) {
                privateHostContacted = true
                respond(content = "secret", status = HttpStatusCode.OK)
            } else {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    // Same scheme as the initial request (https) so Ktor's built-in
                    // https->http downgrade protection doesn't mask the SSRF hole
                    // we're actually testing: redirecting into a private/loopback host.
                    headers = headersOf(HttpHeaders.Location, "https://$PRIVATE_HOST:9/private"),
                )
            }
        }

        val requester = HttpRequesterImpl(engine)
        val result = requester.execute(
            HttpRequestConfig(
                url = "https://example.com/redirect",
                method = HttpMethod.GET,
                headers = emptyMap(),
                body = null,
            ),
        )

        assertFalse(privateHostContacted, "The private redirect target must never be contacted")
        assertEquals(HttpStatusCode.Found.value, result.statusCode)
        assertEquals(listOf("https://$PRIVATE_HOST:9/private"), result.headers[HttpHeaders.Location])
    }

    private companion object {
        const val PRIVATE_HOST = "127.0.0.1"
    }
}
