package com.ventouxlabs.netlens.core.scan

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the resolver may auto-merge rows and weak-score new devices (the kill switch). The default
 * is off until Task 1.7 binds a user preference (a dark launch). Off means the resolver's plain
 * MAC-or-IP lookup: no merge, no weak scoring, no suggestions and no identifier-Conflict rows, so
 * `pendingMergeSuggestions` stays empty and the Conflict alert path is unreachable until it is on.
 */
fun interface AutoMergeSetting {
    suspend fun isEnabled(): Boolean
}

@Singleton
class DefaultAutoMergeSetting @Inject constructor() : AutoMergeSetting {
    override suspend fun isEnabled(): Boolean = false
}
