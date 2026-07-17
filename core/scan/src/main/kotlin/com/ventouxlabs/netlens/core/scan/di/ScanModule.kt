package com.ventouxlabs.netlens.core.scan.di

import com.ventouxlabs.netlens.core.scan.NewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.NewDeviceNotifierImpl
import com.ventouxlabs.netlens.core.scan.engine.ArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.ArpTableReaderImpl
import com.ventouxlabs.netlens.core.scan.engine.DeviceFingerprinter
import com.ventouxlabs.netlens.core.scan.engine.DeviceFingerprinterImpl
import com.ventouxlabs.netlens.core.scan.engine.LanMdnsScanner
import com.ventouxlabs.netlens.core.scan.engine.LanMdnsScannerImpl
import com.ventouxlabs.netlens.core.scan.engine.NetBiosProber
import com.ventouxlabs.netlens.core.scan.engine.NetBiosProberImpl
import com.ventouxlabs.netlens.core.scan.engine.SsdpScanner
import com.ventouxlabs.netlens.core.scan.engine.SsdpScannerImpl
import com.ventouxlabs.netlens.core.scan.engine.SubnetScanner
import com.ventouxlabs.netlens.core.scan.engine.SubnetScannerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {

    @Binds
    @Singleton
    abstract fun bindSubnetScanner(impl: SubnetScannerImpl): SubnetScanner

    @Binds
    @Singleton
    abstract fun bindLanMdnsScanner(impl: LanMdnsScannerImpl): LanMdnsScanner

    @Binds
    @Singleton
    abstract fun bindSsdpScanner(impl: SsdpScannerImpl): SsdpScanner

    @Binds
    @Singleton
    abstract fun bindNetBiosProber(impl: NetBiosProberImpl): NetBiosProber

    @Binds
    @Singleton
    abstract fun bindArpTableReader(impl: ArpTableReaderImpl): ArpTableReader

    @Binds
    @Singleton
    abstract fun bindDeviceFingerprinter(impl: DeviceFingerprinterImpl): DeviceFingerprinter

    @Binds
    @Singleton
    abstract fun bindNewDeviceNotifier(impl: NewDeviceNotifierImpl): NewDeviceNotifier
}
