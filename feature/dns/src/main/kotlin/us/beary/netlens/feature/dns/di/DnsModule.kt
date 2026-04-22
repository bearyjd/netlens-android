package us.beary.netlens.feature.dns.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.feature.dns.engine.DnsResolver
import us.beary.netlens.feature.dns.engine.DnsResolverImpl
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
