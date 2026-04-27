package com.ventoux.netlens.feature.lanscan.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.ventoux.netlens.feature.lanscan.engine.ArpTableReader
import com.ventoux.netlens.feature.lanscan.engine.ArpTableReaderImpl
import com.ventoux.netlens.feature.lanscan.engine.LanMdnsScanner
import com.ventoux.netlens.feature.lanscan.engine.LanMdnsScannerImpl
import com.ventoux.netlens.feature.lanscan.engine.NetBiosProber
import com.ventoux.netlens.feature.lanscan.engine.NetBiosProberImpl
import com.ventoux.netlens.feature.lanscan.engine.SsdpScanner
import com.ventoux.netlens.feature.lanscan.engine.SsdpScannerImpl
import com.ventoux.netlens.feature.lanscan.engine.SubnetScanner
import com.ventoux.netlens.feature.lanscan.engine.SubnetScannerImpl
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
}
