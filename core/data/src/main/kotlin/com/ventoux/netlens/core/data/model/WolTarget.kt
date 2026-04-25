package com.ventoux.netlens.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wol_targets")
data class WolTarget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val macAddress: String,
    val broadcastIp: String = "255.255.255.255",
    val port: Int = 9,
    val createdAt: Long = System.currentTimeMillis(),
)
