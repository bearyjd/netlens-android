package com.ventouxlabs.netlens.feature.dns.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.feature.dns.engine.DnsResolver
import com.ventouxlabs.netlens.feature.dns.engine.DnsResolverImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DnsModule {

    @Binds
    @Singleton
    abstract fun bindDnsResolver(
        impl: DnsResolverImpl,
    ): DnsResolver
}
