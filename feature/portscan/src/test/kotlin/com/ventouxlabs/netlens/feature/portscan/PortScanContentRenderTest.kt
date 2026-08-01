package com.ventouxlabs.netlens.feature.portscan

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.core.ui.UiText
import com.ventouxlabs.netlens.feature.portscan.model.PortResult
import com.ventouxlabs.netlens.feature.portscan.model.PortRiskLevel
import com.ventouxlabs.netlens.feature.portscan.model.PortScanUiState
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests — see `AndroidScreenshotConventionPlugin`.
 *
 * Also covers the host shapes that #117 touched: the HTTP Test and TLS chips are hidden when
 * `HostName.toAuthority` refuses the host, and an IPv6 host has to be bracketed before it goes
 * in front of `:port`. Rendering a scan against a bare IPv6 literal is what exercises that.
 */
class PortScanContentRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun result(port: Int, open: Boolean, risk: PortRiskLevel, service: String) = PortResult(
        port = port,
        serviceName = service,
        isOpen = open,
        latencyMs = 4,
        riskLevel = risk,
        description = "$service on $port",
    )

    private fun render(state: PortScanUiState) = paparazzi.snapshot {
        PortScanContent(
            state = state,
            onHostChanged = {},
            onScan = { _, _ -> },
            onCancel = {},
            onNavigateToTool = { _, _ -> },
            onOpenService = {},
        )
    }

    @Test
    fun `the idle empty state renders`() {
        render(PortScanUiState())
    }

    @Test
    fun `open and closed ports render together`() {
        render(
            PortScanUiState(
                host = "192.168.1.10",
                results = listOf(
                    result(22, true, PortRiskLevel.CRITICAL, "ssh"),
                    result(80, true, PortRiskLevel.WARNING, "http"),
                    result(443, true, PortRiskLevel.INFO, "https"),
                    result(3306, false, PortRiskLevel.CLOSED, "mysql"),
                ),
                openCount = 3,
            ),
        )
    }

    @Test
    fun `an ipv6 host renders with its service chips`() {
        // The HTTP Test chip builds "$scheme://$authority$portSuffix"; an unbracketed IPv6
        // literal in front of ":port" is what silently opened a blank HTTP Tester before #117.
        render(
            PortScanUiState(
                host = "fe80::1",
                results = listOf(result(80, true, PortRiskLevel.WARNING, "http")),
                openCount = 1,
            ),
        )
    }

    @Test
    fun `a host the validator refuses renders without action chips`() {
        render(
            PortScanUiState(
                host = "192.168.1.1@evil.example",
                results = listOf(result(80, true, PortRiskLevel.WARNING, "http")),
                openCount = 1,
            ),
        )
    }

    @Test
    fun `a scan in progress renders`() {
        render(PortScanUiState(host = "192.168.1.10", isScanning = true, progress = 0.35f))
    }

    @Test
    fun `an error renders`() {
        render(PortScanUiState(host = "nope.invalid", error = UiText.Dynamic("Unknown host")))
    }
}
