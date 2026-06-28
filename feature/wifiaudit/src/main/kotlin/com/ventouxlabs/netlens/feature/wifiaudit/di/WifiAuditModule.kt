package com.ventouxlabs.netlens.feature.wifiaudit.di

import com.ventouxlabs.netlens.feature.wifiaudit.engine.WifiInfoReader
import com.ventouxlabs.netlens.feature.wifiaudit.engine.WifiInfoReaderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class WifiAuditModule {

    @Binds
    abstract fun bindWifiInfoReader(impl: WifiInfoReaderImpl): WifiInfoReader
}
