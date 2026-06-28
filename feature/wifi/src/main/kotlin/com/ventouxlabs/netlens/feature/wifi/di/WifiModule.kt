package com.ventouxlabs.netlens.feature.wifi.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.feature.wifi.engine.WifiScanner
import com.ventouxlabs.netlens.feature.wifi.engine.WifiScannerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class WifiModule {

    @Binds
    abstract fun bindWifiScanner(impl: WifiScannerImpl): WifiScanner
}
