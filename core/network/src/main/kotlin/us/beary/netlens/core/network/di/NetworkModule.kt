package us.beary.netlens.core.network.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.core.network.ConnectivityManagerNetworkInterfaceProvider
import us.beary.netlens.core.network.ConnectivityManagerNetworkMonitor
import us.beary.netlens.core.network.NetworkInterfaceProvider
import us.beary.netlens.core.network.NetworkMonitor
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
