package com.ventouxlabs.netlens.core.network.testing

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * A [MockEngine] that answers the first request with a 302 into a private address, and records
 * whether anything ever actually followed it.
 *
 * Every HTTP client in this app must refuse to chase a redirect into a private or loopback host —
 * an attacker who controls a public URL you fetch otherwise gets a read primitive onto the LAN the
 * phone is sitting on. That is the `configureSecureDefaults()` contract, and the way you prove it
 * holds is to offer the redirect and assert nobody took it.
 *
 * ```kotlin
 * val probe = SsrfRedirectProbe()
 * val result = HttpRequesterImpl(probe.engine).execute(config(url = "https://example.com/redirect"))
 *
 * probe.assertPrivateHostNotContacted()
 * assertEquals(HttpStatusCode.Found.value, result.statusCode)
 * ```
 *
 * @param privateHost the address the redirect points at; loopback by default.
 * @param privatePort deliberately port 9 (discard) so a fixture that somehow escapes into a real
 *   network connects to nothing.
 */
class SsrfRedirectProbe(
    val privateHost: String = "127.0.0.1",
    private val privatePort: Int = 9,
) {

    /** True once the redirect target has been requested — which must never happen. */
    var privateHostContacted: Boolean = false
        private set

    /** The exact `Location` header the engine hands back, for tests asserting on it. */
    val location: String = "https://$privateHost:$privatePort/private"

    val engine: MockEngine = MockEngine { request ->
        if (request.url.host == privateHost) {
            privateHostContacted = true
            respond(content = "secret", status = HttpStatusCode.OK)
        } else {
            respond(
                content = "",
                status = HttpStatusCode.Found,
                // Same scheme as the initial request (https) so Ktor's built-in https->http
                // downgrade protection doesn't mask the hole actually under test: a redirect into
                // a private/loopback host. Downgrade the scheme here and the client refuses for
                // the wrong reason, and the test passes whether or not the SSRF guard exists.
                headers = headersOf(HttpHeaders.Location, location),
            )
        }
    }

    /** Fails with the reason spelled out, rather than a bare `assertFalse`. */
    fun assertPrivateHostNotContacted() {
        check(!privateHostContacted) {
            "The client followed a redirect to $privateHost — the SSRF guard is not holding."
        }
    }
}
