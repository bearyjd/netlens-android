package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.secure.KeyValueStore

/**
 * In-memory [KeyValueStore] mirroring `EncryptedKeyValueStore`'s blank handling: reading a blank
 * value yields null, and writing a null or blank value removes the key rather than storing it.
 *
 * Three byte-identical private copies of this existed — in `:feature:ipinfo`,
 * `:feature:widgetsettings` and `:feature:posture` — before it moved here. They had not drifted
 * yet, which is the only reason the move was free; the blank handling is the part that would
 * have drifted, because it is easy to write the naive version that stores `""` and then a test
 * asserting "unset" passes against a fake that is looser than production.
 */
class FakeKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]?.takeIf { it.isNotBlank() }

    override fun putString(key: String, value: String?) {
        if (value.isNullOrBlank()) map.remove(key) else map[key] = value
    }
}
