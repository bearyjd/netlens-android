package com.ventouxlabs.netlens.core.network.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.core.network.ConnectivityManagerNetworkInterfaceProvider
import com.ventouxlabs.netlens.core.network.ConnectivityManagerNetworkMonitor
import com.ventouxlabs.netlens.core.network.NetworkInterfaceProvider
import com.ventouxlabs.netlens.core.network.NetworkMonitor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(
        impl: ConnectivityManagerNetworkMonitor,
    ): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindNetworkInterfaceProvider(
        impl: ConnectivityManagerNetworkInterfaceProvider,
    ): NetworkInterfaceProvider
}
