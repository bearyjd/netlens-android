package com.ventoux.netlens.feature.ipcalc.model

data class IpCalcUiState(
    val input: String = "",
    val result: SubnetInfo? = null,
    val error: String? = null,
)
