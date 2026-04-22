package us.beary.netlens.feature.lanscan.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.feature.lanscan.engine.SubnetScanner
import us.beary.netlens.feature.lanscan.engine.SubnetScannerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LanScanModule {

    @Binds
    @Singleton
    abstract fun bindSubnetScanner(
        impl: SubnetScannerImpl,
    ): SubnetScanner
}
