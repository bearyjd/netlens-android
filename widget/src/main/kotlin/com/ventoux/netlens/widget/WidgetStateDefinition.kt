package com.ventoux.netlens.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.state.GlanceStateDefinition
import java.io.File

object WidgetStateDefinition : GlanceStateDefinition<Preferences> {

    val SCORE_GRADE = stringPreferencesKey("score_grade")
    val SCORE_COLOR_ARGB = intPreferencesKey("score_color_argb")
    val ISSUE_COUNT = intPreferencesKey("issue_count")
    val TOP_ISSUE = stringPreferencesKey("top_issue")
    val TOP_ISSUE_ID = stringPreferencesKey("top_issue_id")

    val IS_CONNECTED = booleanPreferencesKey("is_connected")
    val SSID = stringPreferencesKey("ssid")
    val ENCRYPTION_TYPE = stringPreferencesKey("encryption_type")
    val IS_ENCRYPTION_SECURE = booleanPreferencesKey("is_encryption_secure")

    val PUBLIC_IP = stringPreferencesKey("public_ip")
    val COUNTRY_FLAG = stringPreferencesKey("country_flag")
    val COUNTRY_NAME = stringPreferencesKey("country_name")
    val COUNTRY_CODE = stringPreferencesKey("country_code")
    val ISP_NAME = stringPreferencesKey("isp_name")
    val ASN_NAME = stringPreferencesKey("asn_name")

    val SPEED_MBPS = floatPreferencesKey("speed_mbps")
    val SPEED_LABEL = stringPreferencesKey("speed_label")
    val SPEED_TIMESTAMP = longPreferencesKey("speed_timestamp")

    val LATENCY_MS = longPreferencesKey("latency_ms")
    val DEVICE_COUNT = intPreferencesKey("device_count")
    val VPN_ACTIVE = booleanPreferencesKey("vpn_active")

    val LAST_SCAN_TIMESTAMP = longPreferencesKey("last_scan_timestamp")
    val IS_SCAN_RUNNING = booleanPreferencesKey("is_scan_running")

    val LOCAL_IP = stringPreferencesKey("local_ip")
    val PING_MS = intPreferencesKey("ping_ms")
    val HAS_IPV6 = booleanPreferencesKey("has_ipv6")
    val VPN_INTERFACE_NAME = stringPreferencesKey("vpn_interface_name")
    val RSSI = intPreferencesKey("rssi")
    val RSSI_LEVEL = intPreferencesKey("rssi_level")
    val LINK_SPEED_MBPS = intPreferencesKey("link_speed_mbps")
    val CELL_GENERATION = stringPreferencesKey("cell_generation")
    val IS_METERED = booleanPreferencesKey("is_metered")
    val IS_CAPTIVE_PORTAL = booleanPreferencesKey("is_captive_portal")
    val HAS_PRIVATE_DNS = booleanPreferencesKey("has_private_dns")

    private const val DATA_STORE_NAME = "netlens_widget_state"

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = DATA_STORE_NAME,
    )

    override suspend fun getDataStore(
        context: Context,
        fileKey: String,
    ): DataStore<Preferences> = context.dataStore

    override fun getLocation(
        context: Context,
        fileKey: String,
    ): File = context.filesDir.resolve("datastore").resolve("$DATA_STORE_NAME.preferences_pb")
}

fun Preferences.toWidgetState(): WidgetState = WidgetState(
    scoreGrade = this[WidgetStateDefinition.SCORE_GRADE].orEmpty(),
    scoreColorArgb = this[WidgetStateDefinition.SCORE_COLOR_ARGB] ?: 0,
    issueCount = this[WidgetStateDefinition.ISSUE_COUNT] ?: 0,
    topIssue = this[WidgetStateDefinition.TOP_ISSUE].orEmpty(),
    topIssueId = this[WidgetStateDefinition.TOP_ISSUE_ID].orEmpty(),
    isConnected = this[WidgetStateDefinition.IS_CONNECTED] ?: false,
    ssid = this[WidgetStateDefinition.SSID],
    encryptionType = this[WidgetStateDefinition.ENCRYPTION_TYPE].orEmpty(),
    isEncryptionSecure = this[WidgetStateDefinition.IS_ENCRYPTION_SECURE] ?: true,
    publicIp = this[WidgetStateDefinition.PUBLIC_IP].orEmpty(),
    countryFlag = this[WidgetStateDefinition.COUNTRY_FLAG].orEmpty(),
    countryName = this[WidgetStateDefinition.COUNTRY_NAME].orEmpty(),
    countryCode = this[WidgetStateDefinition.COUNTRY_CODE].orEmpty(),
    ispName = this[WidgetStateDefinition.ISP_NAME].orEmpty(),
    asnName = this[WidgetStateDefinition.ASN_NAME].orEmpty(),
    speedMbps = this[WidgetStateDefinition.SPEED_MBPS] ?: -1f,
    speedLabel = this[WidgetStateDefinition.SPEED_LABEL].orEmpty(),
    speedTimestamp = this[WidgetStateDefinition.SPEED_TIMESTAMP] ?: 0L,
    latencyMs = this[WidgetStateDefinition.LATENCY_MS] ?: -1L,
    deviceCount = this[WidgetStateDefinition.DEVICE_COUNT] ?: 0,
    vpnActive = this[WidgetStateDefinition.VPN_ACTIVE] ?: false,
    lastScanTimestamp = this[WidgetStateDefinition.LAST_SCAN_TIMESTAMP] ?: 0L,
    isScanRunning = this[WidgetStateDefinition.IS_SCAN_RUNNING] ?: false,
    localIp = this[WidgetStateDefinition.LOCAL_IP].orEmpty(),
    pingMs = this[WidgetStateDefinition.PING_MS] ?: -1,
    hasIpv6 = this[WidgetStateDefinition.HAS_IPV6] ?: false,
    vpnInterfaceName = this[WidgetStateDefinition.VPN_INTERFACE_NAME].orEmpty(),
    rssi = this[WidgetStateDefinition.RSSI] ?: -1000,
    rssiLevel = this[WidgetStateDefinition.RSSI_LEVEL] ?: -1,
    linkSpeedMbps = this[WidgetStateDefinition.LINK_SPEED_MBPS] ?: -1,
    cellGeneration = this[WidgetStateDefinition.CELL_GENERATION].orEmpty(),
    isMetered = this[WidgetStateDefinition.IS_METERED] ?: false,
    isCaptivePortal = this[WidgetStateDefinition.IS_CAPTIVE_PORTAL] ?: false,
    hasPrivateDns = this[WidgetStateDefinition.HAS_PRIVATE_DNS] ?: false,
)
