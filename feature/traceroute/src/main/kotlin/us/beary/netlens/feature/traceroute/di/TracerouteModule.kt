package us.beary.netlens.feature.traceroute.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.feature.traceroute.engine.Tracer
import us.beary.netlens.feature.traceroute.engine.TracerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class TracerouteModule {

    @Binds
    abstract fun bindTracer(impl: TracerImpl): Tracer
}
