package com.ventouxlabs.netlens.feature.celltower.di

import com.ventouxlabs.netlens.feature.celltower.engine.CellTowerReader
import com.ventouxlabs.netlens.feature.celltower.engine.CellTowerReaderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class CellTowerModule {

    @Binds
    abstract fun bindCellTowerReader(impl: CellTowerReaderImpl): CellTowerReader
}
