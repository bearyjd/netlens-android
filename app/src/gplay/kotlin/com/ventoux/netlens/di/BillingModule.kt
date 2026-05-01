package com.ventoux.netlens.di

import com.ventoux.netlens.billing.GplayProStatus
import com.ventoux.netlens.core.billing.ProStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    abstract fun bindProStatus(impl: GplayProStatus): ProStatus
}
