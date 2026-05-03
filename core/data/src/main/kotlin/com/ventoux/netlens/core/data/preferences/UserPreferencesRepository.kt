package com.ventoux.netlens.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
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

    companion object {
        private val FAVORITES_KEY = stringSetPreferencesKey("favorite_tools")
        private val RECENTS_KEY = stringPreferencesKey("recent_tools")
        private val IPINFO_CONSENT_KEY = booleanPreferencesKey("ipinfo_consent_granted")
        private val LATENCY_MONITOR_ENABLED = booleanPreferencesKey("latency_monitor_enabled")
        private val LATENCY_MONITOR_HOST = stringPreferencesKey("latency_monitor_host")
        private val LATENCY_ALERT_THRESHOLD_MS = intPreferencesKey("latency_alert_threshold_ms")
        private const val RECENTS_SEPARATOR = ","
        private const val MAX_RECENTS = 5
        val DEFAULT_FAVORITES = setOf("ping", "lanscan", "dns")
    }
}
