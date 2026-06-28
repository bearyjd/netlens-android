package com.ventouxlabs.netlens.feature.lanscan.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.feature.lanscan.NewDeviceNotifier
import com.ventouxlabs.netlens.feature.lanscan.NewDeviceNotifierImpl
import com.ventouxlabs.netlens.feature.lanscan.engine.ArpTableReader
import com.ventouxlabs.netlens.feature.lanscan.engine.ArpTableReaderImpl
import com.ventouxlabs.netlens.feature.lanscan.engine.DeviceFingerprinter
import com.ventouxlabs.netlens.feature.lanscan.engine.DeviceFingerprinterImpl
import com.ventouxlabs.netlens.feature.lanscan.engine.LanMdnsScanner
import com.ventouxlabs.netlens.feature.lanscan.engine.LanMdnsScannerImpl
import com.ventouxlabs.netlens.feature.lanscan.engine.NetBiosProber
import com.ventouxlabs.netlens.feature.lanscan.engine.NetBiosProberImpl
import com.ventouxlabs.netlens.feature.lanscan.engine.SsdpScanner
import com.ventouxlabs.netlens.feature.lanscan.engine.SsdpScannerImpl
import com.ventouxlabs.netlens.feature.lanscan.engine.SubnetScanner
import com.ventouxlabs.netlens.feature.lanscan.engine.SubnetScannerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LanScanModule {

    @Binds
    @Singleton
    abstract fun bindSubnetScanner(
        impl: SubnetScannerImpl,
    ): SubnetScanner

    @Binds
    @Singleton
    abstract fun bindLanMdnsScanner(
        impl: LanMdnsScannerImpl,
    ): LanMdnsScanner

    @Binds
    @Singleton
    abstract fun bindSsdpScanner(
        impl: SsdpScannerImpl,
    ): SsdpScanner

    @Binds
    @Singleton
    abstract fun bindNetBiosProber(
        impl: NetBiosProberImpl,
    ): NetBiosProber

    @Binds
    @Singleton
    abstract fun bindArpTableReader(
        impl: ArpTableReaderImpl,
    ): ArpTableReader

    @Binds
    @Singleton
    abstract fun bindDeviceFingerprinter(
        impl: DeviceFingerprinterImpl,
    ): DeviceFingerprinter

    @Binds
    @Singleton
    abstract fun bindNewDeviceNotifier(
        impl: NewDeviceNotifierImpl,
    ): NewDeviceNotifier
}
