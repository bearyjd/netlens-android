package com.ventouxlabs.netlens.feature.dnsleak.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.feature.dnsleak.engine.DnsLeakDetector
import com.ventouxlabs.netlens.feature.dnsleak.engine.DnsLeakDetectorImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DnsLeakModule {

    @Binds
    @Singleton
    abstract fun bindDnsLeakDetector(
        impl: DnsLeakDetectorImpl,
    ): DnsLeakDetector
}
