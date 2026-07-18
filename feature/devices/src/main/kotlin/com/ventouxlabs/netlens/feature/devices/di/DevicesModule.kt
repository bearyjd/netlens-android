package com.ventouxlabs.netlens.feature.devices.di

import com.ventouxlabs.netlens.feature.devices.NetworkIdentity
import com.ventouxlabs.netlens.feature.devices.NetworkIdentityImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DevicesModule {

    @Binds
    @Singleton
    abstract fun bindNetworkIdentity(impl: NetworkIdentityImpl): NetworkIdentity
}
