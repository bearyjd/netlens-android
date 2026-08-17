package com.ventouxlabs.netlens.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.ventouxlabs.netlens.core.data.preferences.PersistedPostureScore
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.widget.model.WidgetIpResponse
import com.ventouxlabs.netlens.widget.util.CollectedNetworkData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The derivation and write stages `WidgetRefreshWorker.doWork()` used to hold inline. Everything
 * here is framework-free; `doWork()`'s remaining I/O (ConnectivityManager, Room, a socket to
 * 8.8.8.8, an HTTPS call) is not covered and needs an instrumentation test.
 */
class WidgetSnapshotTest {

    private val now = 1_700_000_000_000L

    private fun persisted(
        grade: String = "B",
        issueCount: Int = 2,
        topIssue: String? = "Weak encryption",
        timestampMs: Long = now,
    ) = PersistedPostureScore(
        grade = grade,
        numericScore = 80,
        issueCount = issueCount,
        topIssue = topIssue,
        timestampMs = timestampMs,
    )

    // --- resolveWidgetScore -------------------------------------------------------------------

    @Test
    fun `offline yields no score at all`() {
        val score = resolveWidgetScore(
            isConnected = false,
            persisted = persisted(),
            nowMs = now,
            encryptionType = "WPA3",
            deviceCount = 1,
            vpnState = VpnState.FullTunnel,
        )

        assertNull(score)
    }

    // A fresh audit from the Security Posture screen outranks the widget's own estimate. The
    // encryption/device/VPN inputs here would compute an A, so an A means the persisted score lost.
    @Test
    fun `a fresh persisted score wins over the computed one`() {
        val score = resolveWidgetScore(
            isConnected = true,
            persisted = persisted(grade = "D", issueCount = 4, topIssue = "Open network"),
            nowMs = now + POSTURE_SCORE_FRESHNESS_MS - 1,
            encryptionType = "WPA3",
            deviceCount = 1,
            vpnState = VpnState.FullTunnel,
        )

        assertEquals("D", score?.grade)
        assertEquals(4, score?.issueCount)
        assertEquals("Open network", score?.topIssue)
        // The persisted record has no issue id, so tapping the widget can't deep-link to a finding.
        assertNull(score?.topIssueId)
    }

    // Exactly at the boundary the persisted score is already stale — the comparison is `<`, and an
    // off-by-one to `<=` would pin a days-old grade for one extra sampling window.
    @Test
    fun `a persisted score exactly at the freshness boundary is stale`() {
        val score = resolveWidgetScore(
            isConnected = true,
            persisted = persisted(grade = "D", issueCount = 4),
            nowMs = now + POSTURE_SCORE_FRESHNESS_MS,
            encryptionType = "WPA3",
            deviceCount = 1,
            vpnState = VpnState.FullTunnel,
        )

        assertEquals("A", score?.grade)
    }

    @Test
    fun `no persisted score falls back to the computed one`() {
        val score = resolveWidgetScore(
            isConnected = true,
            persisted = null,
            nowMs = now,
            encryptionType = null,
            deviceCount = 40,
            vpnState = VpnState.None,
        )

        assertEquals(computeWidgetScore(null, 40, VpnState.None), score)
    }

    // --- resolveIpDisplay ---------------------------------------------------------------------

    @Test
    fun `a well-formed response splits the ASN off the org field`() {
        val display = resolveIpDisplay(
            WidgetIpResponse(ip = "1.1.1.1", country = "US", org = "AS13335 Cloudflare, Inc."),
        )

        assertEquals("1.1.1.1", display?.ip)
        assertEquals("AS13335", display?.asnName)
        assertEquals("Cloudflare, Inc.", display?.ispName)
        assertEquals("US", display?.countryCode)
        assertEquals("🇺🇸", display?.countryFlag)
        // Not pinned to a literal — displayCountry is JVM-default-locale dependent — but still
        // falsifiable if the field stopped being populated at all.
        assertTrue(display?.countryName?.isNotEmpty() == true)
    }

    @Test
    fun `null response yields no display`() {
        assertNull(resolveIpDisplay(null))
    }

    // The gate is a whole-string match, not a search — a response carrying an address plus
    // anything else is dropped rather than rendered into the widget.
    @Test
    fun `an ip with trailing content is rejected`() {
        assertNull(resolveIpDisplay(WidgetIpResponse(ip = "1.1.1.1 (proxied)", country = "US")))
        assertNull(resolveIpDisplay(WidgetIpResponse(ip = "not-an-ip", country = "US")))
        assertNull(resolveIpDisplay(WidgetIpResponse(ip = "", country = "US")))
    }

    @Test
    fun `an org without an ASN keeps its full text as the ISP name`() {
        val display = resolveIpDisplay(WidgetIpResponse(ip = "8.8.8.8", country = "US", org = "Google"))

        assertEquals("", display?.asnName)
        assertEquals("Google", display?.ispName)
    }

    // The case the `ifBlank` fallback actually exists for, and the only one that pins it: an org
    // that is *nothing but* an ASN leaves substringAfter(" ") empty, which would render a widget
    // row with a blank ISP. A single-word org does not exercise this — substringAfter returns the
    // whole string when the delimiter is absent, so that case passes with or without the fallback.
    @Test
    fun `an org that is only an ASN falls back rather than showing a blank ISP`() {
        val display = resolveIpDisplay(WidgetIpResponse(ip = "8.8.8.8", country = "US", org = "AS15169 "))

        assertEquals("AS15169", display?.asnName)
        assertEquals("AS15169 ", display?.ispName)
    }

    @Test
    fun `an invalid country code yields an empty flag rather than garbage`() {
        assertEquals("", resolveIpDisplay(WidgetIpResponse(ip = "8.8.8.8", country = "XXX"))?.countryFlag)
    }

    // --- applyWidgetSnapshot ------------------------------------------------------------------

    private fun snapshot(
        isConnected: Boolean = true,
        ssid: String? = "HomeWiFi",
        encryptionType: String? = "WPA3",
        score: WidgetScore? = null,
        ipDisplay: WidgetIpDisplay? = null,
        latencyMs: Long = 25L,
        pingMs: Int = 12,
    ) = WidgetSnapshot(
        isConnected = isConnected,
        ssid = ssid,
        encryptionType = encryptionType,
        score = score,
        ipDisplay = ipDisplay,
        latencyMs = latencyMs,
        pingMs = pingMs,
        deviceCount = 7,
        vpnState = VpnState.None,
        collected = collected,
        dnsServers = listOf("1.1.1.1", "9.9.9.9"),
        routingMode = "full",
        isDnsLeaking = false,
        nowMs = now,
    )

    /**
     * Every field distinct, and none left at its default — a passthrough that swapped `rssi` for
     * `rssiLevel` (or dropped one entirely) is invisible against defaults.
     */
    private val collected = CollectedNetworkData(
        localIp = "192.168.1.5",
        isVpn = false,
        vpnInterfaceName = "tun7",
        rssi = -61,
        rssiLevel = 3,
        linkSpeedMbps = 433,
        cellGeneration = "5G",
        hasIpv6 = true,
        isMetered = true,
        isCaptivePortal = true,
        hasPrivateDns = true,
        dnsServers = listOf("ignored-by-the-write-path"),
    )

    /** Applies [snapshots] in order to one set of preferences, as consecutive refreshes would. */
    private fun preferencesAfter(vararg snapshots: WidgetSnapshot): MutablePreferences =
        mutablePreferencesOf().apply { snapshots.forEach { applyWidgetSnapshot(it) } }

    /**
     * Asserts **every** key `applyWidgetSnapshot` writes, with a distinct value per key.
     *
     * A dropped or mis-mapped key is the defect this extraction risks, and a partial assertion
     * would stay green for most of them — so this covers the whole surface rather than a sample.
     */
    @Test
    fun `a connected wifi snapshot writes every key`() {
        val score = WidgetScore(grade = "B", colorArgb = 42, issueCount = 2, topIssue = "issue", topIssueId = "id")
        val ip = WidgetIpDisplay("203.0.113.7", "🇺🇸", "United States", "US", "Cloudflare", "AS13335")
        val prefs = preferencesAfter(snapshot(score = score, ipDisplay = ip))

        assertEquals(true, prefs[WidgetStateDefinition.IS_CONNECTED])
        assertEquals("HomeWiFi", prefs[WidgetStateDefinition.SSID])
        assertEquals("WPA3", prefs[WidgetStateDefinition.ENCRYPTION_TYPE])
        assertEquals(true, prefs[WidgetStateDefinition.IS_ENCRYPTION_SECURE])
        assertEquals(now, prefs[WidgetStateDefinition.LAST_SCAN_TIMESTAMP])
        assertEquals(false, prefs[WidgetStateDefinition.IS_SCAN_RUNNING])

        assertEquals("B", prefs[WidgetStateDefinition.SCORE_GRADE])
        assertEquals(42, prefs[WidgetStateDefinition.SCORE_COLOR_ARGB])
        assertEquals(2, prefs[WidgetStateDefinition.ISSUE_COUNT])
        assertEquals("issue", prefs[WidgetStateDefinition.TOP_ISSUE])
        assertEquals("id", prefs[WidgetStateDefinition.TOP_ISSUE_ID])

        assertEquals("203.0.113.7", prefs[WidgetStateDefinition.PUBLIC_IP])
        assertEquals("🇺🇸", prefs[WidgetStateDefinition.COUNTRY_FLAG])
        assertEquals("United States", prefs[WidgetStateDefinition.COUNTRY_NAME])
        assertEquals("US", prefs[WidgetStateDefinition.COUNTRY_CODE])
        assertEquals("Cloudflare", prefs[WidgetStateDefinition.ISP_NAME])
        assertEquals("AS13335", prefs[WidgetStateDefinition.ASN_NAME])

        assertEquals(25L, prefs[WidgetStateDefinition.LATENCY_MS])
        assertEquals("25", prefs[WidgetStateDefinition.LATENCY_HISTORY])
        assertEquals(12, prefs[WidgetStateDefinition.PING_MS])
        assertEquals(7, prefs[WidgetStateDefinition.DEVICE_COUNT])
        assertEquals(VpnState.None.serialize(), prefs[WidgetStateDefinition.VPN_STATE])

        assertEquals("192.168.1.5", prefs[WidgetStateDefinition.LOCAL_IP])
        assertEquals(true, prefs[WidgetStateDefinition.HAS_IPV6])
        assertEquals("tun7", prefs[WidgetStateDefinition.VPN_INTERFACE_NAME])
        assertEquals(-61, prefs[WidgetStateDefinition.RSSI])
        assertEquals(3, prefs[WidgetStateDefinition.RSSI_LEVEL])
        assertEquals(433, prefs[WidgetStateDefinition.LINK_SPEED_MBPS])
        assertEquals("5G", prefs[WidgetStateDefinition.CELL_GENERATION])
        assertEquals(true, prefs[WidgetStateDefinition.IS_METERED])
        assertEquals(true, prefs[WidgetStateDefinition.IS_CAPTIVE_PORTAL])
        assertEquals(true, prefs[WidgetStateDefinition.HAS_PRIVATE_DNS])

        // The snapshot's own dnsServers, not collected.dnsServers — doWork() resolves the two
        // separately and only the former reaches the widget.
        assertEquals("1.1.1.1,9.9.9.9", prefs[WidgetStateDefinition.DNS_SERVERS])
        assertEquals("full", prefs[WidgetStateDefinition.ROUTING_MODE])
        assertEquals(false, prefs[WidgetStateDefinition.IS_DNS_LEAKING])
        assertEquals(now, prefs[WidgetStateDefinition.LAST_REFRESH_MS])
    }

    // The stale-data bug this whole write path exists to prevent: WifiManager keeps reporting the
    // last association after a move to cellular, so the keys must be *removed*, not left alone.
    @Test
    fun `moving off wifi clears the previous SSID and encryption`() {
        val prefs = preferencesAfter(
            snapshot(),
            snapshot(ssid = null, encryptionType = null),
        )

        assertFalse(prefs.contains(WidgetStateDefinition.SSID))
        assertFalse(prefs.contains(WidgetStateDefinition.ENCRYPTION_TYPE))
        assertFalse(prefs.contains(WidgetStateDefinition.IS_ENCRYPTION_SECURE))
    }

    // Deliberately asymmetric with SSID: a missing score keeps the last grade on screen, so an
    // offline blip doesn't blank the widget's headline number.
    @Test
    fun `a missing score leaves the previous grade in place`() {
        val prefs = preferencesAfter(
            snapshot(score = WidgetScore("A", 1, 0, null, null)),
            snapshot(isConnected = false, score = null),
        )

        assertEquals("A", prefs[WidgetStateDefinition.SCORE_GRADE])
        assertEquals(false, prefs[WidgetStateDefinition.IS_CONNECTED])
    }

    // Within a written score the optional fields behave like SSID — a score with no top issue
    // must not inherit the previous score's issue text.
    @Test
    fun `a score without a top issue clears the previous issue text`() {
        val prefs = preferencesAfter(
            snapshot(score = WidgetScore("D", 1, 3, "Open network", "encryption")),
            snapshot(score = WidgetScore("A", 1, 0, null, null)),
        )

        assertEquals("A", prefs[WidgetStateDefinition.SCORE_GRADE])
        assertEquals(0, prefs[WidgetStateDefinition.ISSUE_COUNT])
        assertFalse(prefs.contains(WidgetStateDefinition.TOP_ISSUE))
        assertFalse(prefs.contains(WidgetStateDefinition.TOP_ISSUE_ID))
    }

    // No IP block is written when there is nothing valid to write — but unlike SSID the previous
    // one is kept, which is worth pinning so a future change to either is a deliberate one.
    @Test
    fun `a missing ip display leaves the previous public ip in place`() {
        val display = WidgetIpDisplay("1.1.1.1", "🇺🇸", "United States", "US", "Cloudflare", "AS13335")
        val prefs = preferencesAfter(
            snapshot(ipDisplay = display),
            snapshot(ipDisplay = null),
        )

        assertEquals("1.1.1.1", prefs[WidgetStateDefinition.PUBLIC_IP])
        assertEquals("AS13335", prefs[WidgetStateDefinition.ASN_NAME])
    }

    @Test
    fun `latency history accumulates across refreshes and drops failed measurements`() {
        val prefs = preferencesAfter(
            snapshot(latencyMs = 10L),
            snapshot(latencyMs = -1L),
            snapshot(latencyMs = 30L),
        )

        // The failed middle measurement leaves no gap in the sparkline; LATENCY_MS is always
        // whatever the latest refresh measured, failed or not.
        assertEquals("10,30", prefs[WidgetStateDefinition.LATENCY_HISTORY])
        assertEquals(30L, prefs[WidgetStateDefinition.LATENCY_MS])
    }

    @Test
    fun `insecure encryption is flagged`() {
        val prefs = preferencesAfter(snapshot(encryptionType = "WEP"))

        // assertEquals, not assertFalse(x == true) — the typed getter returns null for an absent
        // key, and `null == true` is false, so the weaker form would pass if the write vanished.
        assertEquals(false, prefs[WidgetStateDefinition.IS_ENCRYPTION_SECURE])
        assertEquals("WEP", prefs[WidgetStateDefinition.ENCRYPTION_TYPE])
    }
}
