package com.ventoux.netlens.feature.wifi.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventoux.netlens.feature.wifi.engine.WifiScanner
import com.ventoux.netlens.feature.wifi.engine.WifiScannerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class WifiModule {

    @Binds
    abstract fun bindWifiScanner(impl: WifiScannerImpl): WifiScanner
}
