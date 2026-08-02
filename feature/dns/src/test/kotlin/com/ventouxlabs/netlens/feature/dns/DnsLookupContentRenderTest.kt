package com.ventouxlabs.netlens.feature.dns

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.feature.dns.model.DnsError
import com.ventouxlabs.netlens.feature.dns.model.DnsLookupUiState
import com.ventouxlabs.netlens.feature.dns.model.DnsRecordType
import com.ventouxlabs.netlens.feature.dns.model.DnsResult
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests — see `AndroidScreenshotConventionPlugin`.
 *
 * The multi-type case matters here beyond coverage: results are grouped by record type and keyed
 * `"dns:${startIndex + i}"` off a `resultIndex` that accumulates across groups. A per-group index
 * would collide between groups, so rendering several types at once is what exercises that.
 */
class DnsLookupContentRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun render(state: DnsLookupUiState) = paparazzi.snapshot {
        DnsLookupContent(
            state = state,
            onDomainChanged = {},
            onTypeToggled = {},
            onLookup = {},
            onNavigateToTool = { _, _ -> },
        )
    }

    @Test
    fun `the idle empty state renders`() {
        render(DnsLookupUiState())
    }

    @Test
    fun `several record types render together`() {
        render(
            DnsLookupUiState(
                domain = "example.com",
                selectedTypes = setOf(DnsRecordType.A, DnsRecordType.AAAA, DnsRecordType.MX),
                results = listOf(
                    DnsResult(DnsRecordType.A, "example.com", "93.184.216.34", 300),
                    DnsResult(DnsRecordType.A, "example.com", "93.184.216.35", 300),
                    DnsResult(DnsRecordType.AAAA, "example.com", "2606:2800:220:1:248:1893:25c8:1946", 300),
                    DnsResult(DnsRecordType.MX, "example.com", "10 mail.example.com", 3600),
                ),
            ),
        )
    }

    @Test
    fun `a lookup in progress renders`() {
        render(DnsLookupUiState(domain = "example.com", isLoading = true))
    }

    @Test
    fun `each error kind renders`() {
        render(DnsLookupUiState(error = DnsError.EmptyDomain))
        render(DnsLookupUiState(domain = "example.com", selectedTypes = emptySet(), error = DnsError.NoTypes))
        render(DnsLookupUiState(domain = "example.com", error = DnsError.LookupFailed("SERVFAIL")))
    }
}
