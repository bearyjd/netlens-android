package com.ventoux.netlens.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.ventoux.netlens.billing.BillingClientFactory
import com.ventoux.netlens.billing.BillingPrefs
import com.ventoux.netlens.billing.GplayProStatus
import com.ventoux.netlens.billing.PlayBillingClientWrapper
import com.ventoux.netlens.core.billing.ProStatus
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
                Log.e(TAG, "Encrypted prefs unavailable, using plain prefs", e)
                return context.getSharedPreferences(PREFS_NAME_LEGACY, Context.MODE_PRIVATE)
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
