package com.ventouxlabs.netlens.feature.traceroute

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.feature.traceroute.model.TracerouteHop
import com.ventouxlabs.netlens.feature.traceroute.model.TracerouteUiState
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests — does this screen *render*, not does it look right. No golden images;
 * see `AndroidScreenshotConventionPlugin`. These assert nothing on purpose: a render failure
 * escapes through the JUnit rule, so a `try/catch` here would silently pass.
 */
class TracerouteContentRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun hop(n: Int, ip: String?, rtts: List<Float>, timeout: Boolean = false) =
        TracerouteHop(
            hopNumber = n,
            ip = ip,
            hostname = ip?.let { "host$n.example" },
            rttMs = rtts,
            isTimeout = timeout,
        )

    private fun render(state: TracerouteUiState) = paparazzi.snapshot {
        TracerouteContent(
            state = state,
            onHostChange = {},
            onStartTrace = {},
            onStopTrace = {},
            onNavigateToTool = { _, _ -> },
        )
    }

    @Test
    fun `the idle empty state renders`() {
        render(TracerouteUiState())
    }

    @Test
    fun `a completed trace renders`() {
        render(
            TracerouteUiState(
                host = "example.com",
                hops = listOf(
                    hop(1, "192.168.1.1", listOf(1.2f, 1.4f, 1.1f)),
                    hop(2, "10.0.0.1", listOf(8.3f, 9.1f, 8.8f)),
                    hop(3, "93.184.216.34", listOf(24.5f, 25.1f, 24.9f)),
                ),
            ),
        )
    }

    @Test
    fun `timed-out hops render`() {
        // A hop with no IP and no RTTs is the common real-world case — routers that drop ICMP.
        render(
            TracerouteUiState(
                host = "example.com",
                hops = listOf(
                    hop(1, "192.168.1.1", listOf(1.2f)),
                    hop(2, null, emptyList(), timeout = true),
                    hop(3, null, emptyList(), timeout = true),
                    hop(4, "93.184.216.34", listOf(24.5f)),
                ),
            ),
        )
    }

    @Test
    fun `a trace in progress renders`() {
        render(
            TracerouteUiState(
                host = "example.com",
                hops = listOf(hop(1, "192.168.1.1", listOf(1.2f))),
                isTracing = true,
            ),
        )
    }

    @Test
    fun `an error renders`() {
        render(TracerouteUiState(host = "nope.invalid", error = "Unknown host"))
    }
}
