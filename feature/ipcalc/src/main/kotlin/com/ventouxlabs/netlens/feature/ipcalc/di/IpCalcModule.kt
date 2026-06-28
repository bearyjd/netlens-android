package com.ventouxlabs.netlens.feature.ipcalc.di

import com.ventouxlabs.netlens.feature.ipcalc.engine.SubnetCalculator
import com.ventouxlabs.netlens.feature.ipcalc.engine.SubnetCalculatorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class IpCalcModule {

    @Binds
    abstract fun bindSubnetCalculator(impl: SubnetCalculatorImpl): SubnetCalculator
}
