package com.ventouxlabs.netlens.feature.whois.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.feature.whois.engine.DomainResolver
import com.ventouxlabs.netlens.feature.whois.engine.DomainResolverImpl
import com.ventouxlabs.netlens.feature.whois.engine.RdnsResolver
import com.ventouxlabs.netlens.feature.whois.engine.RdnsResolverImpl
import com.ventouxlabs.netlens.feature.whois.engine.WhoisClient
import com.ventouxlabs.netlens.feature.whois.engine.WhoisClientImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WhoisModule {

    @Binds
    @Singleton
    abstract fun bindWhoisClient(
        impl: WhoisClientImpl,
    ): WhoisClient

    @Binds
    @Singleton
    abstract fun bindRdnsResolver(
        impl: RdnsResolverImpl,
    ): RdnsResolver

    @Binds
    @Singleton
    abstract fun bindDomainResolver(
        impl: DomainResolverImpl,
    ): DomainResolver
}
