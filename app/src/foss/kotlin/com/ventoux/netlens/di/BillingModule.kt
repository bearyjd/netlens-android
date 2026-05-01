package com.ventoux.netlens.di

import android.app.Activity
import com.ventoux.netlens.core.billing.ProStatus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideProStatus(): ProStatus = object : ProStatus {
        override val isPro: StateFlow<Boolean> = MutableStateFlow(true)
        override fun launchPurchase(activity: Activity) {}
    }
}
