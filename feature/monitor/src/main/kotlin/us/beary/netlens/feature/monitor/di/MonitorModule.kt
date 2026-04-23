package us.beary.netlens.feature.monitor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.feature.monitor.engine.EndpointChecker
import us.beary.netlens.feature.monitor.engine.EndpointCheckerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MonitorModule {

    @Binds
    @Singleton
    abstract fun bindEndpointChecker(impl: EndpointCheckerImpl): EndpointChecker
}
