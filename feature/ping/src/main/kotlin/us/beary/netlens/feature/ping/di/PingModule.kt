package us.beary.netlens.feature.ping.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.feature.ping.engine.Pinger
import us.beary.netlens.feature.ping.engine.PingerImpl
import us.beary.netlens.feature.ping.service.PingServiceController
import us.beary.netlens.feature.ping.service.PingServiceControllerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PingModule {

    @Binds
    abstract fun bindPinger(impl: PingerImpl): Pinger

    @Binds
    @Singleton
    abstract fun bindServiceController(impl: PingServiceControllerImpl): PingServiceController
}
