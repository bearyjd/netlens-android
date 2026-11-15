package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the resolver may auto-merge rows and weak-score new devices (the kill switch). Off means the
 * resolver's plain MAC-or-IP lookup: no new merges, no weak scoring, no new suggestions and no
 * identifier-Conflict handling. It does not undo merges already completed or clear suggestions already
 * pending. "Loses nothing" means identifiers, observations and sightings keep being recorded, so turning
 * it back on starts from complete data. Read once per scan.
 */
fun interface AutoMergeSetting {
    suspend fun isEnabled(): Boolean
}

/**
 * Backed by the `autoMergeDevices` preference (default on), read afresh on every call. A read that
 * fails answers off, the safe direction, so a broken preference store cannot abort a scan.
 */
@Singleton
class DefaultAutoMergeSetting @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : AutoMergeSetting {
    override suspend fun isEnabled(): Boolean = try {
        preferences.autoMergeDevices.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
