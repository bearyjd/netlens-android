package com.ventouxlabs.netlens.feature.celltower.model

data class CellTowerUiState(
    val connectedTower: CellTowerInfo? = null,
    val neighborCells: List<CellTowerInfo> = emptyList(),
    val isRefreshing: Boolean = false,
    val hasPermission: Boolean = false,
    val noCellular: Boolean = false,
    val error: String? = null,
)
