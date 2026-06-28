package com.ventouxlabs.netlens.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.core.data.secure.EncryptedKeyValueStore
import com.ventouxlabs.netlens.core.data.secure.KeyValueStore
import javax.inject.Singleton

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.userPreferencesDataStore

    @Provides
    @Singleton
    fun provideKeyValueStore(
        @ApplicationContext context: Context,
    ): KeyValueStore = EncryptedKeyValueStore(context, "netlens_secrets")
}
