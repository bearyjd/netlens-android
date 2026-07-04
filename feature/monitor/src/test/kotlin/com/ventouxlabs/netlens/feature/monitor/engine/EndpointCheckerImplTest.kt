package com.ventouxlabs.netlens.feature.monitor.engine

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EndpointCheckerImplTest {

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

        val checker = EndpointCheckerImpl(engine)
        checker.use {
            val result = checker.check("https://example.com/redirect")

            assertFalse(privateHostContacted, "The private redirect target must never be contacted")
            assertEquals(HttpStatusCode.Found.value, result.statusCode)
        }
    }

    @Test
    fun `ftp url throws IllegalArgumentException`() {
        val checker = EndpointCheckerImpl()
        checker.use {
            assertThrows<IllegalArgumentException> {
                runBlocking { checker.check("ftp://example.com") }
            }
        }
    }

    @Test
    fun `empty url throws IllegalArgumentException`() {
        val checker = EndpointCheckerImpl()
        checker.use {
            assertThrows<IllegalArgumentException> {
                runBlocking { checker.check("") }
            }
        }
    }

    @Test
    fun `private host 127_0_0_1 throws IllegalArgumentException`() {
        val checker = EndpointCheckerImpl()
        checker.use {
            assertThrows<IllegalArgumentException> {
                runBlocking { checker.check("http://127.0.0.1") }
            }
        }
    }

    @Test
    fun `private host localhost throws IllegalArgumentException`() {
        val checker = EndpointCheckerImpl()
        checker.use {
            assertThrows<IllegalArgumentException> {
                runBlocking { checker.check("http://localhost") }
            }
        }
    }

    @Test
    fun `valid public url does not throw IllegalArgumentException`() {
        val checker = EndpointCheckerImpl()
        checker.use {
            val result = runBlocking { checker.check("https://example.com") }
            assertNotNull(result)
            assertFalse(result.isSuccess && result.errorMessage != null)
        }
    }

    private companion object {
        const val PRIVATE_HOST = "127.0.0.1"
    }
}
