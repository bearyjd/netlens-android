package com.ventoux.netlens.feature.netlog.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventoux.netlens.feature.netlog.engine.NetworkMonitor
import com.ventoux.netlens.feature.netlog.engine.NetworkMonitorImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetLogModule {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: NetworkMonitorImpl): NetworkMonitor
}
