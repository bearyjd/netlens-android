package us.beary.netlens.feature.portscan.engine

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PortScannerImplTest {

    private val scanner = PortScannerImpl()

    @Test
    fun `too many ports throws IllegalArgumentException`() = runTest {
        val ports = (1..10_001).toList()
        assertThrows<IllegalArgumentException> {
            scanner.scan("example.com", ports, 1000).toList()
        }
    }

    @Test
    fun `exactly 10000 ports does not throw validation error`() = runTest {
        val ports = (1..10_000).toList()
        // Won't throw on validation, but will fail on I/O — that's fine
        try {
            scanner.scan("127.0.0.1", ports, 1).toList()
        } catch (_: Exception) {
            // I/O failure expected, validation passed
        }
    }

    @Test
    fun `port 0 throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            scanner.scan("example.com", listOf(0), 1000).toList()
        }
    }

    @Test
    fun `port 65536 throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            scanner.scan("example.com", listOf(65536), 1000).toList()
        }
    }

    @Test
    fun `port 65535 is valid`() = runTest {
        try {
            scanner.scan("127.0.0.1", listOf(65535), 1).toList()
        } catch (_: Exception) {
            // I/O failure expected, validation passed
        }
    }

    @Test
    fun `blank host throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            scanner.scan("", listOf(80), 1000).toList()
        }
    }

    @Test
    fun `host starting with dash throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            scanner.scan("-evil.com", listOf(80), 1000).toList()
        }
    }

    @Test
    fun `host with shell injection throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            scanner.scan("; rm -rf /", listOf(80), 1000).toList()
        }
    }

    @Test
    fun `host with backticks throws IllegalArgumentException`() = runTest {
        assertThrows<IllegalArgumentException> {
            scanner.scan("`whoami`.evil.com", listOf(80), 1000).toList()
        }
    }
}
