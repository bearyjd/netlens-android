package com.ventoux.netlens.feature.mdns.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventoux.netlens.feature.mdns.engine.MdnsScanner
import com.ventoux.netlens.feature.mdns.engine.MdnsScannerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class MdnsModule {

    @Binds
    abstract fun bindMdnsScanner(impl: MdnsScannerImpl): MdnsScanner
}
