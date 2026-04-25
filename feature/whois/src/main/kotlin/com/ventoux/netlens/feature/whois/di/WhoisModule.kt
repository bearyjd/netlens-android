package com.ventoux.netlens.feature.whois.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventoux.netlens.feature.whois.engine.DomainResolver
import com.ventoux.netlens.feature.whois.engine.DomainResolverImpl
import com.ventoux.netlens.feature.whois.engine.RdnsResolver
import com.ventoux.netlens.feature.whois.engine.RdnsResolverImpl
import com.ventoux.netlens.feature.whois.engine.WhoisClient
import com.ventoux.netlens.feature.whois.engine.WhoisClientImpl
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
