package com.ventouxlabs.netlens.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.ventouxlabs.netlens.billing.BillingClientFactory
import com.ventouxlabs.netlens.billing.BillingPrefs
import com.ventouxlabs.netlens.billing.GplayProStatus
import com.ventouxlabs.netlens.billing.PlayBillingClientWrapper
import com.ventouxlabs.netlens.core.billing.ProStatus
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    abstract fun bindProStatus(impl: GplayProStatus): ProStatus

    companion object {
        private const val TAG = "BillingModule"
        private const val PREFS_NAME = "netlens_billing_secure"
        private const val PREFS_NAME_LEGACY = "netlens_billing"
        private const val KEY_PRO_UNLOCKED = "pro_unlocked"

        @Provides
        @Singleton
        fun provideBillingClientFactory(
            @ApplicationContext context: Context,
        ): BillingClientFactory = BillingClientFactory { listener ->
            PlayBillingClientWrapper(context, listener)
        }

        @Provides
        @Singleton
        @BillingPrefs
        fun provideBillingPrefs(
            @ApplicationContext context: Context,
        ): SharedPreferences {
            val encrypted = try {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Encrypted prefs unavailable; Pro state will not persist this session", e)
                context.getSharedPreferences(PREFS_NAME_LEGACY, Context.MODE_PRIVATE).edit().clear().apply()
                val transientName = "netlens_billing_transient_${System.currentTimeMillis()}"
                return context.getSharedPreferences(transientName, Context.MODE_PRIVATE).also {
                    it.edit().clear().apply()
                }
            }

            val legacy = context.getSharedPreferences(PREFS_NAME_LEGACY, Context.MODE_PRIVATE)
            if (legacy.contains(KEY_PRO_UNLOCKED)) {
                encrypted.edit()
                    .putBoolean(KEY_PRO_UNLOCKED, legacy.getBoolean(KEY_PRO_UNLOCKED, false))
                    .apply()
                legacy.edit().clear().apply()
            }

            return encrypted
        }
    }
}
