package us.beary.netlens.feature.whois.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.feature.whois.engine.DomainResolver
import us.beary.netlens.feature.whois.engine.DomainResolverImpl
import us.beary.netlens.feature.whois.engine.RdnsResolver
import us.beary.netlens.feature.whois.engine.RdnsResolverImpl
import us.beary.netlens.feature.whois.engine.WhoisClient
import us.beary.netlens.feature.whois.engine.WhoisClientImpl
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
