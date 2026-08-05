package com.ventouxlabs.netlens.feature.lanscan.di

import com.ventouxlabs.netlens.feature.lanscan.engine.ScanLocationProvider
import com.ventouxlabs.netlens.feature.lanscan.engine.ScanLocationProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * `:feature:lanscan`'s own bindings. Everything else the LAN scan needs — the scan engines,
 * the port domain, the DAOs — is bound by `core:scan` and `core:data`, which is why this module
 * did not exist until location capture needed a seam.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LanScanModule {

    @Binds
    abstract fun bindScanLocationProvider(impl: ScanLocationProviderImpl): ScanLocationProvider
}
