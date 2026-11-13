package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.scan.model.PortRiskLevel
import com.ventouxlabs.netlens.feature.lanscan.model.HostPortResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortResultDisplayTest {

    private fun result(
        port: Int,
        risk: PortRiskLevel,
        isOpen: Boolean = true,
        protocol: String = "TCP",
    ) = HostPortResult(
        port = port,
        protocol = protocol,
        serviceName = "svc",
        isOpen = isOpen,
        latencyMs = 1,
        riskLevel = risk,
        description = "d",
    )

    @Test
    fun `mixed input yields only open groups in risk order`() {
        val input = listOf(
            result(1, PortRiskLevel.CLOSED, isOpen = false),
            result(443, PortRiskLevel.INFO),
            result(80, PortRiskLevel.WARNING),
            result(2, PortRiskLevel.CLOSED, isOpen = false),
            result(23, PortRiskLevel.CRITICAL),
        )
        val groups = openPortGroups(input)
        assertEquals(
            listOf(PortRiskLevel.CRITICAL, PortRiskLevel.WARNING, PortRiskLevel.INFO),
            groups.map { it.first },
        )
        assertTrue(groups.flatMap { it.second }.all { it.isOpen })
        assertEquals(3, groups.sumOf { it.second.size })
    }

    @Test
    fun `zero open yields no groups and correct summary`() {
        val input = List(5) { result(it + 1, PortRiskLevel.CLOSED, isOpen = false) }
        assertTrue(openPortGroups(input).isEmpty())
        assertEquals(PortScanSummary(open = 0, closedOrFiltered = 5, scanned = 5), portScanSummary(input))
    }

    @Test
    fun `summary counts open and closed`() {
        val input = listOf(
            result(80, PortRiskLevel.WARNING),
            result(22, PortRiskLevel.CRITICAL),
            result(1, PortRiskLevel.CLOSED, isOpen = false),
        )
        assertEquals(PortScanSummary(open = 2, closedOrFiltered = 1, scanned = 3), portScanSummary(input))
        assertEquals(PortScanSummary(0, 0, 0), portScanSummary(emptyList()))
    }

    @Test
    fun `same port on two protocols stays distinct`() {
        val groups = openPortGroups(
            listOf(
                result(80, PortRiskLevel.WARNING, protocol = "TCP"),
                result(80, PortRiskLevel.WARNING, protocol = "UDP"),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(listOf("TCP", "UDP"), groups[0].second.map { it.protocol })
    }

    @Test
    fun `description hidden when blank or equal to service name`() {
        assertEquals(null, portDescriptionToShow("Port 8008", "Port 8008"))
        assertEquals(null, portDescriptionToShow("SSH", "  "))
        assertEquals(null, portDescriptionToShow("SSH", ""))
        assertEquals(null, portDescriptionToShow("SSH", "ssh"))
    }

    @Test
    fun `description shown when different from service name`() {
        assertEquals("Secure Shell", portDescriptionToShow("SSH", "Secure Shell"))
    }

    @Test
    fun `generic fallback service name is hidden but named service kept`() {
        assertEquals(null, portServiceNameToShow(8008, "Port 8008"))
        assertEquals(null, portServiceNameToShow(8008, ""))
        assertEquals("SSH", portServiceNameToShow(22, "SSH"))
        assertEquals("Port 9", portServiceNameToShow(8008, "Port 9"))
    }
}
