package com.ventouxlabs.netlens.feature.wifi.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.feature.wifi.engine.WifiScanner
import com.ventouxlabs.netlens.feature.wifi.engine.WifiScannerImpl
import com.ventouxlabs.netlens.feature.wifi.engine.WifiSignalSampler
import com.ventouxlabs.netlens.feature.wifi.engine.WifiSignalSamplerImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class WifiModule {

    @Binds
    abstract fun bindWifiScanner(impl: WifiScannerImpl): WifiScanner

    @Binds
    abstract fun bindWifiSignalSampler(impl: WifiSignalSamplerImpl): WifiSignalSampler
}
