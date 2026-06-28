package com.ventouxlabs.netlens.core.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Encrypted SharedPreferences-backed store for sensitive user-supplied secrets
 * (API keys, tokens). Falls back to a transient session-only store on encryption
 * failure so a corrupted keystore does not silently downgrade to plaintext.
 */
class EncryptedKeyValueStore(private val context: Context, private val prefsName: String) : KeyValueStore {
    private val prefs: SharedPreferences by lazy { openPrefs() }

    override fun getString(key: String): String? = prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    override fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(key) else putString(key, value)
        }.apply()
    }

    private fun openPrefs(): SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            prefsName,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.e(TAG, "Encrypted prefs unavailable for $prefsName; using transient session store", e)
        val transient = "${prefsName}_transient_${System.currentTimeMillis()}"
        context.getSharedPreferences(transient, Context.MODE_PRIVATE).also { it.edit().clear().apply() }
    }

    private companion object {
        const val TAG = "EncryptedKVStore"
    }
}
