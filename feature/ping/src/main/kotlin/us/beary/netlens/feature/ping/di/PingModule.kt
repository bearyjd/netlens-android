package us.beary.netlens.feature.ping.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.feature.ping.engine.Pinger
import us.beary.netlens.feature.ping.engine.PingerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class PingModule {

    @Binds
    abstract fun bindPinger(impl: PingerImpl): Pinger
}
