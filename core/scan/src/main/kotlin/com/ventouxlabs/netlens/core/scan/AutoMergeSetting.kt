package com.ventouxlabs.netlens.core.scan

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the resolver may auto-merge rows and weak-score new devices (the kill switch). The default
 * is off until Task 1.7 binds a user preference: off means plain MAC-or-IP lookup.
 */
fun interface AutoMergeSetting {
    suspend fun isEnabled(): Boolean
}

@Singleton
class DefaultAutoMergeSetting @Inject constructor() : AutoMergeSetting {
    override suspend fun isEnabled(): Boolean = false
}
