package com.ventouxlabs.netlens.feature.traceroute.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.feature.traceroute.engine.HopGeolocator
import com.ventouxlabs.netlens.feature.traceroute.engine.HopGeolocatorImpl
import com.ventouxlabs.netlens.feature.traceroute.engine.Tracer
import com.ventouxlabs.netlens.feature.traceroute.engine.TracerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class TracerouteModule {

    @Binds
    abstract fun bindTracer(impl: TracerImpl): Tracer

    @Binds
    abstract fun bindHopGeolocator(impl: HopGeolocatorImpl): HopGeolocator
}
