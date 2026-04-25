package com.ventoux.netlens.feature.monitor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventoux.netlens.feature.monitor.engine.EndpointChecker
import com.ventoux.netlens.feature.monitor.engine.EndpointCheckerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MonitorModule {

    @Binds
    @Singleton
    abstract fun bindEndpointChecker(impl: EndpointCheckerImpl): EndpointChecker
}
