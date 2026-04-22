package us.beary.netlens.feature.portscan.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.feature.portscan.engine.PortScanner
import us.beary.netlens.feature.portscan.engine.PortScannerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class PortScanModule {

    @Binds
    abstract fun bindPortScanner(
        impl: PortScannerImpl,
    ): PortScanner
}
