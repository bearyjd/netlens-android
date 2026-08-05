package com.ventouxlabs.netlens.core.data.testing

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the two properties a consumer actually relies on: writes are visible to later reads, and
 * `data` is a live flow rather than a one-shot snapshot. `UserPreferencesRepository` exposes its
 * values as `dataStore.data.map { … }`, so a fake whose `data` did not re-emit would make every
 * "value changes" test unfalsifiable.
 */
class FakeDataStoreTest {

    private val flag = booleanPreferencesKey("flag")
    private val name = stringPreferencesKey("name")

    @Test
    fun `starts empty`() = runTest {
        assertNull(FakeDataStore().data.first()[flag])
    }

    @Test
    fun `a written value reads back`() = runTest {
        val store = FakeDataStore()
        store.edit { it[flag] = true }

        assertEquals(true, store.data.first()[flag])
    }

    @Test
    fun `updateData returns the transformed preferences`() = runTest {
        val store = FakeDataStore()

        val returned = store.updateData { prefs ->
            prefs.toMutablePreferences().apply { this[name] = "netlens" }
        }

        assertEquals("netlens", returned[name])
    }

    @Test
    fun `data re-emits after a write rather than replaying a snapshot`() = runTest {
        val store = FakeDataStore()
        store.edit { it[flag] = false }
        assertEquals(false, store.data.first()[flag])

        store.edit { it[flag] = true }

        // Same flow, read again: a snapshot-only fake would still report false here and every
        // "the value changed" assertion built on it would be vacuous.
        assertEquals(true, store.data.first()[flag])
    }

    @Test
    fun `independent keys do not clobber each other`() = runTest {
        val store = FakeDataStore()
        store.edit { it[flag] = true }
        store.edit { it[name] = "keep" }

        val prefs = store.data.first()
        assertTrue(prefs[flag] == true && prefs[name] == "keep")
    }
}
