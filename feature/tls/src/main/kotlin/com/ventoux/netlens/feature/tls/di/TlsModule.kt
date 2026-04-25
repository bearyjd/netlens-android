package com.ventoux.netlens.feature.tls.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventoux.netlens.feature.tls.engine.TlsInspector
import com.ventoux.netlens.feature.tls.engine.TlsInspectorImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TlsModule {

    @Binds
    @Singleton
    abstract fun bindTlsInspector(impl: TlsInspectorImpl): TlsInspector
}
