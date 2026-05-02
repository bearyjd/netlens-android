package com.ventoux.netlens.di

import com.ventoux.netlens.billing.FossProStatus
import com.ventoux.netlens.core.billing.ProStatus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideProStatus(): ProStatus = FossProStatus()
}
