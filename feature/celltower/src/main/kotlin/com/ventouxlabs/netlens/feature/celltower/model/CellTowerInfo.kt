package com.ventouxlabs.netlens.feature.celltower.model

import kotlinx.serialization.Serializable

@Serializable
data class CellTowerInfo(
    val networkType: String,
    val operatorName: String = "",
    val cellId: String = "",
    val tac: String = "",
    val band: String = "",
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val rssi: Int? = null,
    val isRegistered: Boolean = false,
)
