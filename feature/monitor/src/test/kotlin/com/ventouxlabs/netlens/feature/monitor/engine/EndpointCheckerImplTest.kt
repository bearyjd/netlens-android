package com.ventouxlabs.netlens.feature.monitor.engine

import com.ventouxlabs.netlens.core.network.testing.SsrfRedirectProbe
import io.ktor.http.HttpStatusCode
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
        val probe = SsrfRedirectProbe()

        val checker = EndpointCheckerImpl(probe.engine)
        checker.use {
            val result = checker.check("https://example.com/redirect")

            probe.assertPrivateHostNotContacted()
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
}
