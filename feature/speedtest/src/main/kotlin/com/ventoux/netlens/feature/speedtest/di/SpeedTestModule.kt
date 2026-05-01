package com.ventoux.netlens.feature.speedtest.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventoux.netlens.feature.speedtest.engine.SpeedTestEngine
import com.ventoux.netlens.feature.speedtest.engine.SpeedTestEngineImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeedTestModule {

    @Binds
    abstract fun bindSpeedTestEngine(impl: SpeedTestEngineImpl): SpeedTestEngine
}
