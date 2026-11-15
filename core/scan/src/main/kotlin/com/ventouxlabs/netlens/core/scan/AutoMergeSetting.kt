package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the resolver may auto-merge rows and weak-score new devices (the kill switch). Off means the
 * resolver's plain MAC-or-IP lookup: no merge, no weak scoring, no suggestions and no identifier-Conflict
 * rows. Identifiers are still recorded, so turning it back on loses nothing. Read once per scan.
 */
fun interface AutoMergeSetting {
    suspend fun isEnabled(): Boolean
}

/** Backed by the `autoMergeDevices` preference (default on), read afresh on every call. */
@Singleton
class DefaultAutoMergeSetting @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : AutoMergeSetting {
    override suspend fun isEnabled(): Boolean = preferences.autoMergeDevices.first()
}
