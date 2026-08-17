package com.ventouxlabs.netlens.widget

import androidx.datastore.preferences.core.MutablePreferences
import com.ventouxlabs.netlens.core.data.preferences.PersistedPostureScore
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.widget.model.WidgetIpResponse
import com.ventouxlabs.netlens.widget.util.CollectedNetworkData
import com.ventouxlabs.netlens.widget.util.toFlagEmoji
import java.util.Locale

/**
 * The derivation and write stages of [WidgetRefreshWorker.doWork], separated from its I/O so they
 * can be tested without a device.
 *
 * `doWork()` itself remains unverifiable in a unit test — it reaches live `ConnectivityManager`,
 * `WifiManager`, Room, a raw socket to 8.8.8.8 and an HTTPS call — but almost none of the logic
 * that decides *what the widget shows* is in that I/O. It is here: which score wins, which IP
 * fields survive validation, and which keys must be **removed** rather than left stale.
 */

/** How long a persisted posture score stays authoritative over a freshly-computed one. */
internal const val POSTURE_SCORE_FRESHNESS_MS = 30 * 60 * 1000L

/**
 * The posture score the widget should show.
 *
 * A score persisted by the Security Posture screen is a real audit and outranks the widget's own
 * three-signal estimate — but only while it is fresh, otherwise a scan from days ago would pin the
 * grade. Offline yields `null` (no grade at all) rather than a computed-from-nothing "F".
 */
internal fun resolveWidgetScore(
    isConnected: Boolean,
    persisted: PersistedPostureScore?,
    nowMs: Long,
    encryptionType: String?,
    deviceCount: Int,
    vpnState: VpnState,
): WidgetScore? {
    if (!isConnected) return null
    if (persisted != null && (nowMs - persisted.timestampMs) < POSTURE_SCORE_FRESHNESS_MS) {
        return WidgetScore(
            grade = persisted.grade,
            colorArgb = gradeColorArgb(persisted.grade),
            issueCount = persisted.issueCount,
            topIssue = persisted.topIssue,
            topIssueId = null,
        )
    }
    return computeWidgetScore(encryptionType, deviceCount, vpnState)
}

/** The public-IP block as the widget renders it, already split out of ipinfo.io's `org` field. */
internal data class WidgetIpDisplay(
    val ip: String,
    val countryFlag: String,
    val countryName: String,
    val countryCode: String,
    val ispName: String,
    val asnName: String,
)

private val IP_PATTERN = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")

/**
 * Validates and reshapes an ipinfo.io response, or returns `null` if there is nothing worth
 * showing.
 *
 * The pattern is matched against the **whole** string (`Regex.matches`, not `containsMatchIn`), so
 * a response carrying anything besides a dotted quad is dropped rather than rendered. `org` arrives
 * as `"AS13335 Cloudflare, Inc."`; the ASN is only taken when it actually looks like one, and a
 * response with no space at all keeps its full text as the ISP name instead of blanking out.
 */
internal fun resolveIpDisplay(response: WidgetIpResponse?): WidgetIpDisplay? {
    val ip = response?.takeIf { IP_PATTERN.matches(it.ip) } ?: return null
    return WidgetIpDisplay(
        ip = ip.ip,
        countryFlag = ip.country.toFlagEmoji(),
        countryName = Locale("", ip.country).displayCountry,
        countryCode = ip.country,
        ispName = ip.org.substringAfter(" ").ifBlank { ip.org },
        asnName = ip.org.substringBefore(" ").takeIf { it.startsWith("AS") } ?: "",
    )
}

/** Everything one refresh resolved, in the shape the widget's DataStore consumes. */
internal data class WidgetSnapshot(
    val isConnected: Boolean,
    val ssid: String?,
    val encryptionType: String?,
    val score: WidgetScore?,
    val ipDisplay: WidgetIpDisplay?,
    /**
     * Whether the user still consents to the `ipinfo.io` lookup.
     *
     * Needed because a revoked consent and a failed fetch both arrive as a null [ipDisplay], and
     * they must be handled differently — see [applyWidgetSnapshot].
     */
    val ipConsentGranted: Boolean,
    val latencyMs: Long,
    val pingMs: Int,
    val deviceCount: Int,
    val vpnState: VpnState,
    val collected: CollectedNetworkData,
    val routingMode: String,
    val isDnsLeaking: Boolean,
    val nowMs: Long,
)

/** The public-IP block, written and cleared as a unit. */
private val IP_KEYS = listOf(
    WidgetStateDefinition.PUBLIC_IP,
    WidgetStateDefinition.COUNTRY_FLAG,
    WidgetStateDefinition.COUNTRY_NAME,
    WidgetStateDefinition.COUNTRY_CODE,
    WidgetStateDefinition.ISP_NAME,
    WidgetStateDefinition.ASN_NAME,
)

/**
 * Writes [snapshot] over the widget's current preferences.
 *
 * **The `remove` calls are the point of this function, not an afterthought.** SSID, encryption and
 * the two optional issue fields are absent-not-empty when they do not apply, and DataStore keeps
 * whatever was written last — so leaving them alone shows the café's WPA3 badge on a cellular
 * widget. A missing score is different: it leaves the previous grade in place deliberately, so an
 * offline blip does not blank the grade the user last saw.
 *
 * The public-IP block splits that decision on *why* it is missing, which is why
 * [WidgetSnapshot.ipConsentGranted] exists: a failed fetch keeps the last values (same reasoning as
 * the score), but a **withdrawn consent clears them**, because that is personal data the user just
 * revoked permission for and the widget would otherwise display it indefinitely.
 */
internal fun MutablePreferences.applyWidgetSnapshot(snapshot: WidgetSnapshot) {
    this[WidgetStateDefinition.IS_CONNECTED] = snapshot.isConnected

    if (snapshot.ssid != null) {
        this[WidgetStateDefinition.SSID] = snapshot.ssid
    } else {
        remove(WidgetStateDefinition.SSID)
    }

    this[WidgetStateDefinition.LAST_SCAN_TIMESTAMP] = snapshot.nowMs
    this[WidgetStateDefinition.IS_SCAN_RUNNING] = false

    if (snapshot.encryptionType != null) {
        this[WidgetStateDefinition.ENCRYPTION_TYPE] = snapshot.encryptionType
        this[WidgetStateDefinition.IS_ENCRYPTION_SECURE] = isEncryptionSecure(snapshot.encryptionType)
    } else {
        remove(WidgetStateDefinition.ENCRYPTION_TYPE)
        remove(WidgetStateDefinition.IS_ENCRYPTION_SECURE)
    }

    snapshot.score?.let { score ->
        this[WidgetStateDefinition.SCORE_GRADE] = score.grade
        this[WidgetStateDefinition.SCORE_COLOR_ARGB] = score.colorArgb
        this[WidgetStateDefinition.ISSUE_COUNT] = score.issueCount
        if (score.topIssue != null) {
            this[WidgetStateDefinition.TOP_ISSUE] = score.topIssue
        } else {
            remove(WidgetStateDefinition.TOP_ISSUE)
        }
        if (score.topIssueId != null) {
            this[WidgetStateDefinition.TOP_ISSUE_ID] = score.topIssueId
        } else {
            remove(WidgetStateDefinition.TOP_ISSUE_ID)
        }
    }

    val ip = snapshot.ipDisplay
    when {
        ip != null -> {
            this[WidgetStateDefinition.PUBLIC_IP] = ip.ip
            this[WidgetStateDefinition.COUNTRY_FLAG] = ip.countryFlag
            this[WidgetStateDefinition.COUNTRY_NAME] = ip.countryName
            this[WidgetStateDefinition.COUNTRY_CODE] = ip.countryCode
            this[WidgetStateDefinition.ISP_NAME] = ip.ispName
            this[WidgetStateDefinition.ASN_NAME] = ip.asnName
        }
        // Consent withdrawn: the cached public IP, ISP and country are personal data the user
        // just revoked permission for, so they must go — a stale grade is a cosmetic problem,
        // a stale public IP sitting on the home screen is not the same kind of problem.
        !snapshot.ipConsentGranted -> IP_KEYS.forEach { remove(it) }
        // Consent still granted, fetch failed: keep the last known values rather than blanking
        // the row on a transient network error.
        else -> Unit
    }

    this[WidgetStateDefinition.LATENCY_MS] = snapshot.latencyMs
    this[WidgetStateDefinition.LATENCY_HISTORY] = appendLatencySample(
        existingCsv = this[WidgetStateDefinition.LATENCY_HISTORY],
        sample = snapshot.latencyMs.takeIf { it >= 0L }?.toInt(),
    )
    this[WidgetStateDefinition.DEVICE_COUNT] = snapshot.deviceCount
    this[WidgetStateDefinition.VPN_STATE] = snapshot.vpnState.serialize()

    this[WidgetStateDefinition.LOCAL_IP] = snapshot.collected.localIp
    this[WidgetStateDefinition.PING_MS] = snapshot.pingMs
    this[WidgetStateDefinition.HAS_IPV6] = snapshot.collected.hasIpv6
    this[WidgetStateDefinition.VPN_INTERFACE_NAME] = snapshot.collected.vpnInterfaceName
    this[WidgetStateDefinition.RSSI] = snapshot.collected.rssi
    this[WidgetStateDefinition.RSSI_LEVEL] = snapshot.collected.rssiLevel
    this[WidgetStateDefinition.LINK_SPEED_MBPS] = snapshot.collected.linkSpeedMbps
    this[WidgetStateDefinition.CELL_GENERATION] = snapshot.collected.cellGeneration
    this[WidgetStateDefinition.IS_METERED] = snapshot.collected.isMetered
    this[WidgetStateDefinition.IS_CAPTIVE_PORTAL] = snapshot.collected.isCaptivePortal
    this[WidgetStateDefinition.HAS_PRIVATE_DNS] = snapshot.collected.hasPrivateDns
    this[WidgetStateDefinition.DNS_SERVERS] = snapshot.collected.dnsServers.joinToString(",")
    this[WidgetStateDefinition.ROUTING_MODE] = snapshot.routingMode
    this[WidgetStateDefinition.IS_DNS_LEAKING] = snapshot.isDnsLeaking
    this[WidgetStateDefinition.LAST_REFRESH_MS] = snapshot.nowMs
}
