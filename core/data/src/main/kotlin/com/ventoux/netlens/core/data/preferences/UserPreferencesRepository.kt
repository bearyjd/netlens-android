package com.ventoux.netlens.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

    companion object {
        private val FAVORITES_KEY = stringSetPreferencesKey("favorite_tools")
        private val RECENTS_KEY = stringPreferencesKey("recent_tools")
        private const val RECENTS_SEPARATOR = ","
        private const val MAX_RECENTS = 5
        val DEFAULT_FAVORITES = setOf("ping", "lanscan", "dns")
    }
}
