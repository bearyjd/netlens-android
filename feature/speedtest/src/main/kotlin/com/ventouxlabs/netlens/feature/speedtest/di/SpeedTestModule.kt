package com.ventouxlabs.netlens.feature.speedtest.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.feature.speedtest.engine.SpeedTestEngine
import com.ventouxlabs.netlens.feature.speedtest.engine.SpeedTestEngineImpl
import com.ventouxlabs.netlens.feature.speedtest.network.ConnectivityManagerMeteredNetworkChecker
import com.ventouxlabs.netlens.feature.speedtest.network.MeteredNetworkChecker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeedTestModule {

    // Scoped @Singleton so the single CIO HttpClient held by the impl lives for the
    // whole app rather than being re-created (and leaked) on every ViewModel.
    @Binds
    @Singleton
    abstract fun bindSpeedTestEngine(impl: SpeedTestEngineImpl): SpeedTestEngine

    @Binds
    abstract fun bindMeteredNetworkChecker(impl: ConnectivityManagerMeteredNetworkChecker): MeteredNetworkChecker
}
