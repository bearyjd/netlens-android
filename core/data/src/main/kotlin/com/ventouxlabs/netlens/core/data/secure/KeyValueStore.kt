package com.ventouxlabs.netlens.core.data.secure

/**
 * Abstraction over a key-value store for sensitive string values.
 * Production implementation uses [EncryptedKeyValueStore]; tests use an in-memory fake.
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}
