package com.ventoux.netlens.feature.wol.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventoux.netlens.feature.wol.engine.WolSender
import com.ventoux.netlens.feature.wol.engine.WolSenderImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WolBindsModule {

    @Binds
    @Singleton
    abstract fun bindWolSender(impl: WolSenderImpl): WolSender
}
