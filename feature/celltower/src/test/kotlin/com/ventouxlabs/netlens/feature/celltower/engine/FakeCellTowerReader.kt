package com.ventouxlabs.netlens.feature.celltower.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeCellTowerReader : CellTowerReader {

    var onceResult: CellTowerState? = null
    val observeFlow = MutableSharedFlow<CellTowerState>()

    override fun observe(): Flow<CellTowerState> = observeFlow

    override fun readOnce(): CellTowerState? = onceResult

    suspend fun emit(state: CellTowerState) = observeFlow.emit(state)
}
