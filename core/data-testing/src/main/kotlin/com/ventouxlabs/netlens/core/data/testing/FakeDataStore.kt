package com.ventouxlabs.netlens.core.data.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [DataStore] of [Preferences], for testing anything built on
 * `UserPreferencesRepository` — which takes an injectable `DataStore` precisely so it can be
 * faked on the JVM (there is no Robolectric in this repo, so a real DataStore is untestable).
 *
 * Two identical private copies existed, in `:feature:ipinfo` and `:feature:widgetsettings`.
 *
 * Deliberately has **no seeding helper**. `:feature:ipinfo`'s copy carried a `setConsent()` that
 * wrote `booleanPreferencesKey("ipinfo_consent_granted")` — a second copy of a key that is
 * private to `UserPreferencesRepository`. A production rename would have left that test passing
 * against a dead key. Seed through the repository's own API instead
 * (`setIpInfoConsent`, `setThemeMode`, …) so the test exercises the real write path.
 */
class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(mutablePreferencesOf())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
