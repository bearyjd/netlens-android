package com.ventouxlabs.netlens.feature.tls.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.feature.tls.engine.TlsInspector
import com.ventouxlabs.netlens.feature.tls.engine.TlsInspectorImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TlsModule {

    @Binds
    @Singleton
    abstract fun bindTlsInspector(impl: TlsInspectorImpl): TlsInspector
}
