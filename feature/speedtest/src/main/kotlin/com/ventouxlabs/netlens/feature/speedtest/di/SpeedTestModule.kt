package com.ventouxlabs.netlens.feature.speedtest.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.feature.speedtest.engine.SpeedTestEngine
import com.ventouxlabs.netlens.feature.speedtest.engine.SpeedTestEngineImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeedTestModule {

    @Binds
    abstract fun bindSpeedTestEngine(impl: SpeedTestEngineImpl): SpeedTestEngine
}
