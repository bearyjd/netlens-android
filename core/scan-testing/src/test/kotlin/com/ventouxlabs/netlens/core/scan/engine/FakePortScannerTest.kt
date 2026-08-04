package com.ventouxlabs.netlens.core.scan.engine

import com.ventouxlabs.netlens.core.scan.model.PortResult
import com.ventouxlabs.netlens.core.scan.model.PortRiskLevel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A test double is only useful if it is no weaker than the thing it stands in for — the same rule
 * [FakeOuiLookupTest] pins for its fake.
 *
 * This one had drifted twice before it was shared: `:feature:lanscan` carried two private copies
 * (`StubPortScanner` and `FakePortScanner`) that both hardcoded `emptyFlow()`, so neither could
 * emit a result or raise an error. A test written against those would pass whether or not the code
 * under test handled open ports or scan failures at all. Both seams are pinned here.
 */
class FakePortScannerTest {

    private fun result(port: Int, open: Boolean = true) = PortResult(
        port = port,
        serviceName = "svc$port",
        isOpen = open,
        latencyMs = 7,
        riskLevel = PortRiskLevel.WARNING,
        description = "svc on $port",
    )

    @Test
    fun `configured results are emitted in order`() = runTest {
        val scanner = FakePortScanner().apply {
            results = listOf(result(22), result(80), result(443))
        }

        val emitted = scanner.scan("192.168.1.10", listOf(22, 80, 443)).toList()

        assertEquals(listOf(22, 80, 443), emitted.map { it.port })
    }

    @Test
    fun `the whole result is carried through, not just the port`() = runTest {
        // The weak copies emitted nothing, so no test using them could catch a consumer that
        // dropped riskLevel or isOpen on the floor.
        val scanner = FakePortScanner().apply { results = listOf(result(3306, open = false)) }

        val only = scanner.scan("host", listOf(3306)).toList().single()

        assertEquals(3306, only.port)
        assertEquals(false, only.isOpen)
        assertEquals(PortRiskLevel.WARNING, only.riskLevel)
        assertEquals(7L, only.latencyMs)
    }

    @Test
    fun `a configured error is thrown rather than completing empty`() = runTest {
        val scanner = FakePortScanner().apply { error = IllegalStateException("host unreachable") }

        val failure = runCatching { scanner.scan("host", listOf(22)).toList() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("host unreachable", failure?.message)
    }

    @Test
    fun `an error wins over configured results instead of emitting them first`() = runTest {
        val scanner = FakePortScanner().apply {
            results = listOf(result(22))
            error = IllegalStateException("boom")
        }

        val failure = runCatching { scanner.scan("host", listOf(22)).toList() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun `an unconfigured scanner emits nothing`() = runTest {
        // This is the *only* behaviour the two deleted copies had. Keeping it as the default means
        // they were a strict subset, so swapping them for this fake could not change a test.
        assertTrue(FakePortScanner().scan("host", listOf(22, 80)).toList().isEmpty())
    }

    @Test
    fun `the flow is cold, so a second collection re-emits`() = runTest {
        val scanner = FakePortScanner().apply { results = listOf(result(22)) }
        val flow = scanner.scan("host", listOf(22))

        assertEquals(1, flow.toList().size)
        assertEquals(1, flow.toList().size)
    }
}
