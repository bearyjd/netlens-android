package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An immutable inventory snapshot promoted from one completed LAN scan event. */
@Entity(tableName = "lan_scan_inventories", indices = [Index("createdAt")])
data class LanScanInventoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sourceEventId: Long,
    val capturedAt: Long,
    val subnet: String?,
    val deviceCount: Int,
    val devicesJson: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
