package com.ventouxlabs.netlens.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.ventouxlabs.netlens.core.data.secure.KeyValueStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class PersistedPostureScore(
    val grade: String,
    val numericScore: Int,
    val issueCount: Int,
    val topIssue: String?,
    val timestampMs: Long,
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val encryptedStore: KeyValueStore,
) {
    // AbuseIPDB key is stored in EncryptedKeyValueStore. A MutableStateFlow bridges the
    // encrypted store (which doesn't natively emit) to the Flow-based API consumers expect.
    // It starts empty and is populated asynchronously in the init block below: reading the
    // encrypted store opens EncryptedSharedPreferences (Android Keystore + disk I/O), which
    // is too costly to run on the main thread during Hilt's app-init field injection.
    private val _abuseIpDbApiKeyFlow = MutableStateFlow("")

    /** The store's data with an unreadable (IOException) store treated as empty, so defaults apply. */
    private val safeData: Flow<Preferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            // Hydrate the key off the main thread.
            val storedKey = encryptedStore.getString(ABUSEIPDB_ENCRYPTED_KEY)
            if (!storedKey.isNullOrBlank()) {
                _abuseIpDbApiKeyFlow.value = storedKey
            }
            // One-shot migration: if DataStore has a plaintext AbuseIPDB key from a prior install,
            // copy it to the encrypted store and remove it from DataStore.
            val plaintextKey = safeData.first()[ABUSEIPDB_API_KEY]
            if (!plaintextKey.isNullOrBlank() && storedKey.isNullOrBlank()) {
                encryptedStore.putString(ABUSEIPDB_ENCRYPTED_KEY, plaintextKey)
                _abuseIpDbApiKeyFlow.value = plaintextKey
            }
            if (!plaintextKey.isNullOrBlank()) {
                dataStore.edit { it.remove(ABUSEIPDB_API_KEY) }
            }
        }
    }

    val favoriteToolRoutes: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[FAVORITES_KEY] ?: DEFAULT_FAVORITES
    }

    val recentToolRoutes: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[RECENTS_KEY]
            ?.split(RECENTS_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    suspend fun setFavorites(routes: Set<String>) {
        dataStore.edit { prefs ->
            prefs[FAVORITES_KEY] = routes
        }
    }

    suspend fun toggleFavorite(route: String) {
        dataStore.edit { prefs ->
            val current = prefs[FAVORITES_KEY] ?: DEFAULT_FAVORITES
            prefs[FAVORITES_KEY] = if (route in current) {
                current - route
            } else {
                current + route
            }
        }
    }

    // Stored as a plain string ("system"/"light"/"dark") so core:data stays free
    // of UI-layer types; the app layer maps it to core:ui's ThemeMode enum.
    val themeMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: THEME_MODE_SYSTEM
    }

    suspend fun setThemeMode(mode: String) {
        require(mode in VALID_THEME_MODES) { "Unknown theme mode: $mode" }
        dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode
        }
    }

    val ipInfoConsentGranted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[IPINFO_CONSENT_KEY] ?: false
    }

    suspend fun setIpInfoConsent(granted: Boolean) {
        dataStore.edit { prefs ->
            prefs[IPINFO_CONSENT_KEY] = granted
        }
    }

    suspend fun recordToolUsage(route: String) {
        dataStore.edit { prefs ->
            val current = prefs[RECENTS_KEY]
                ?.split(RECENTS_SEPARATOR)
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            val updated = (listOf(route) + current.filter { it != route })
                .take(MAX_RECENTS)
            prefs[RECENTS_KEY] = updated.joinToString(RECENTS_SEPARATOR)
        }
    }

    /**
     * Kill switch for device-identity auto-merge. Off stops new merges, weak scoring and Conflict
     * handling (the plain MAC-or-IP lookup); it does not undo completed merges or clear existing
     * suggestions. An unreadable store reads as the default.
     */
    val autoMergeDevices: Flow<Boolean> = safeData.map { it[AUTO_MERGE_DEVICES] ?: DEFAULT_AUTO_MERGE_DEVICES }

    suspend fun setAutoMergeDevices(enabled: Boolean) {
        dataStore.edit { it[AUTO_MERGE_DEVICES] = enabled }
    }

    val latencyMonitorEnabled: Flow<Boolean> = dataStore.data.map { it[LATENCY_MONITOR_ENABLED] ?: false }
    val latencyMonitorHost: Flow<String> = dataStore.data.map { it[LATENCY_MONITOR_HOST] ?: "1.1.1.1" }
    val latencyAlertThresholdMs: Flow<Int> = dataStore.data.map { it[LATENCY_ALERT_THRESHOLD_MS] ?: 200 }

    suspend fun setLatencyMonitorEnabled(enabled: Boolean) {
        dataStore.edit { it[LATENCY_MONITOR_ENABLED] = enabled }
    }

    suspend fun setLatencyMonitorHost(host: String) {
        dataStore.edit { it[LATENCY_MONITOR_HOST] = host }
    }

    suspend fun setLatencyAlertThresholdMs(ms: Int) {
        dataStore.edit { it[LATENCY_ALERT_THRESHOLD_MS] = ms }
    }

    /** DNS server for the DNS lookup tool, as an IP literal. Blank means use the built-in default. */
    val dnsResolverOverride: Flow<String> = safeData.map { it[DNS_RESOLVER_OVERRIDE] ?: "" }

    suspend fun setDnsResolverOverride(server: String) {
        val trimmed = server.trim()
        require(trimmed.isEmpty() || isIpLiteral(trimmed)) { "DNS resolver must be an IP address: $trimmed" }
        dataStore.edit { it[DNS_RESOLVER_OVERRIDE] = trimmed }
    }

    // Literal-only check so validation never triggers a DNS lookup of its own.
    private fun isIpLiteral(value: String): Boolean {
        val octets = value.split('.')
        if (octets.size == 4 && octets.all { o -> o.isNotEmpty() && o.all(Char::isDigit) && o.toInt() in 0..255 }) return true
        return ':' in value && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
    }

    val abuseIpDbApiKey: Flow<String> = _abuseIpDbApiKeyFlow.asStateFlow()

    suspend fun setAbuseIpDbApiKey(key: String) {
        val trimmed = key.trim()
        encryptedStore.putString(ABUSEIPDB_ENCRYPTED_KEY, trimmed.ifBlank { null })
        _abuseIpDbApiKeyFlow.value = trimmed
    }

    val postureScore: Flow<PersistedPostureScore?> = dataStore.data.map { prefs ->
        val grade = prefs[POSTURE_GRADE] ?: return@map null
        PersistedPostureScore(
            grade = grade,
            numericScore = prefs[POSTURE_NUMERIC_SCORE] ?: 0,
            issueCount = prefs[POSTURE_ISSUE_COUNT] ?: 0,
            topIssue = prefs[POSTURE_TOP_ISSUE],
            timestampMs = prefs[POSTURE_TIMESTAMP] ?: 0L,
        )
    }

    suspend fun setPostureScore(grade: String, numericScore: Int, issueCount: Int, topIssue: String?) {
        dataStore.edit { prefs ->
            prefs[POSTURE_GRADE] = grade
            prefs[POSTURE_NUMERIC_SCORE] = numericScore
            prefs[POSTURE_ISSUE_COUNT] = issueCount
            if (topIssue != null) prefs[POSTURE_TOP_ISSUE] = topIssue else prefs.remove(POSTURE_TOP_ISSUE)
            prefs[POSTURE_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    val watchCadenceMinutes: Flow<Int> = dataStore.data.map { it[WATCH_CADENCE_MINUTES] ?: DEFAULT_WATCH_CADENCE_MINUTES }
    val watchMasterEnabled: Flow<Boolean> = dataStore.data.map { it[WATCH_MASTER_ENABLED] ?: false }

    suspend fun setWatchCadenceMinutes(minutes: Int) {
        dataStore.edit { it[WATCH_CADENCE_MINUTES] = minutes }
    }

    suspend fun setWatchMasterEnabled(enabled: Boolean) {
        dataStore.edit { it[WATCH_MASTER_ENABLED] = enabled }
    }

    companion object {
        private val FAVORITES_KEY = stringSetPreferencesKey("favorite_tools")
        private val RECENTS_KEY = stringPreferencesKey("recent_tools")
        private val IPINFO_CONSENT_KEY = booleanPreferencesKey("ipinfo_consent_granted")
        private val LATENCY_MONITOR_ENABLED = booleanPreferencesKey("latency_monitor_enabled")
        private val LATENCY_MONITOR_HOST = stringPreferencesKey("latency_monitor_host")
        private val LATENCY_ALERT_THRESHOLD_MS = intPreferencesKey("latency_alert_threshold_ms")
        // Legacy DataStore key — kept only for one-shot migration in init block.
        private val ABUSEIPDB_API_KEY = stringPreferencesKey("abuseipdb_api_key")
        // Key used inside EncryptedKeyValueStore (netlens_secrets).
        private const val ABUSEIPDB_ENCRYPTED_KEY = "abuseipdb_api_key"
        private val POSTURE_GRADE = stringPreferencesKey("posture_grade")
        private val POSTURE_NUMERIC_SCORE = intPreferencesKey("posture_numeric_score")
        private val POSTURE_ISSUE_COUNT = intPreferencesKey("posture_issue_count")
        private val POSTURE_TOP_ISSUE = stringPreferencesKey("posture_top_issue")
        private val POSTURE_TIMESTAMP = longPreferencesKey("posture_timestamp")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val WATCH_CADENCE_MINUTES = intPreferencesKey("watch_cadence_minutes")
        private val WATCH_MASTER_ENABLED = booleanPreferencesKey("watch_master_enabled")
        private val DNS_RESOLVER_OVERRIDE = stringPreferencesKey("dns_resolver_override")
        private val AUTO_MERGE_DEVICES = booleanPreferencesKey("auto_merge_devices")
        const val DEFAULT_AUTO_MERGE_DEVICES = true
        const val DEFAULT_WATCH_CADENCE_MINUTES = 60
        private const val RECENTS_SEPARATOR = ","
        private const val MAX_RECENTS = 5
        val DEFAULT_FAVORITES = setOf("ping", "lanscan", "dns")
        const val THEME_MODE_SYSTEM = "system"
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"
        val VALID_THEME_MODES = setOf(THEME_MODE_SYSTEM, THEME_MODE_LIGHT, THEME_MODE_DARK)
    }
}
