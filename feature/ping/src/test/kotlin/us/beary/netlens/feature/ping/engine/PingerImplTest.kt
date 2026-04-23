package us.beary.netlens.feature.ping.engine

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PingerImplTest {

    private val pinger = PingerImpl()

    @Test
    fun `blank host throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            pinger.ping("", 1).first()
        }
    }

    @Test
    fun `whitespace-only host throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            pinger.ping("   ", 1).first()
        }
    }

    @Test
    fun `host starting with dash throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            pinger.ping("-evil.com", 1).first()
        }
    }

    @Test
    fun `host with shell injection throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            pinger.ping("; rm -rf /", 1).first()
        }
    }

    @Test
    fun `host with spaces throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            pinger.ping("8.8.8.8 && echo pwned", 1).first()
        }
    }

    @Test
    fun `host exceeding 253 chars throws IllegalArgumentException`() = runTest {
        val longHost = "a".repeat(254) + ".com"
        assertThrows<IllegalArgumentException> {
            pinger.ping(longHost, 1).first()
        }
    }

    @Test
    fun `valid hostname does not throw on validation`() {
        val error = runCatching {
            PingerImpl().ping("example.com", 1)
        }
        assertNotNull(error.getOrNull())
    }

    @Test
    fun `valid IP does not throw on validation`() {
        val error = runCatching {
            PingerImpl().ping("8.8.8.8", 1)
        }
        assertNotNull(error.getOrNull())
    }
}
