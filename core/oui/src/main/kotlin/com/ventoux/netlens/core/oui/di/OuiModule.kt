package com.ventoux.netlens.core.oui.di

import com.ventoux.netlens.core.oui.OuiLookup
import com.ventoux.netlens.core.oui.OuiLookupImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OuiModule {

    @Binds
    @Singleton
    abstract fun bindOuiLookup(impl: OuiLookupImpl): OuiLookup
}
