package com.ventouxlabs.netlens.feature.monitor

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.core.data.model.EndpointCheck
import com.ventouxlabs.netlens.core.data.model.MonitoredEndpoint
import com.ventouxlabs.netlens.feature.monitor.model.MonitorUiState
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests — see `AndroidScreenshotConventionPlugin`.
 *
 * The endpoint list and the check-history list are keyed on raw `it.id` from two different
 * tables. They are safe today because they live in two *separate* `LazyColumn`s — the detail
 * view replaces the list rather than appearing beside it. The selected-endpoint case below is
 * what pins that: if the two ever ended up in one container, an endpoint and a check sharing
 * row id 1 would reproduce #116.
 */
class MonitorContentRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun endpoint(id: Long, label: String, url: String) =
        MonitoredEndpoint(id = id, label = label, url = url, createdAt = 1_000L)

    private fun check(id: Long, endpointId: Long, ok: Boolean, ms: Long) = EndpointCheck(
        id = id,
        endpointId = endpointId,
        timestamp = 1_000L + id,
        statusCode = if (ok) 200 else 503,
        latencyMs = ms,
        isSuccess = ok,
        errorMessage = if (ok) null else "Service Unavailable",
    )

    private fun render(state: MonitorUiState) = paparazzi.snapshot {
        MonitorContent(
            state = state,
            onAddEndpoint = { _, _, _, _ -> },
            onRemoveEndpoint = {},
            onSelectEndpoint = {},
            onDeselectEndpoint = {},
            onDismissError = {},
            onCheckNow = {},
            onBack = {},
        )
    }

    @Test
    fun `the empty state renders`() {
        render(MonitorUiState())
    }

    @Test
    fun `a list of endpoints renders`() {
        render(
            MonitorUiState(
                endpoints = listOf(
                    endpoint(1, "API", "https://api.example.com/health"),
                    endpoint(2, "Site", "https://example.com"),
                ),
                latestChecksByEndpointId = mapOf(
                    1L to check(1, 1, ok = true, ms = 120),
                    2L to check(2, 2, ok = false, ms = 5_000),
                ),
            ),
        )
    }

    @Test
    fun `a selected endpoint with its check history renders`() {
        // Endpoint id 1 and check id 1 are both present on purpose — the two lists key on raw
        // ids from different tables, so this is the shape that would collide if they ever shared
        // a lazy container.
        render(
            MonitorUiState(
                endpoints = listOf(endpoint(1, "API", "https://api.example.com/health")),
                selectedEndpoint = endpoint(1, "API", "https://api.example.com/health"),
                checks = listOf(
                    check(1, 1, ok = true, ms = 120),
                    check(2, 1, ok = true, ms = 98),
                    check(3, 1, ok = false, ms = 5_000),
                ),
            ),
        )
    }

    @Test
    fun `an error renders`() {
        render(MonitorUiState(error = "Could not reach endpoint"))
    }
}
