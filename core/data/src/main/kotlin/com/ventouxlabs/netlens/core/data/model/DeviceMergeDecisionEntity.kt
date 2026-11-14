package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A persisted merge suggestion or the user's "these are distinct" decision; low id < high id. */
@Entity(
    tableName = "device_merge_decisions",
    primaryKeys = ["deviceIdLow", "deviceIdHigh"],
    foreignKeys = [
        ForeignKey(
            entity = KnownDeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceIdLow"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = KnownDeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceIdHigh"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deviceIdHigh"), Index("decision", "score")],
)
data class DeviceMergeDecisionEntity(
    val deviceIdLow: Long,
    val deviceIdHigh: Long,
    // 'SUGGESTED' | 'DISTINCT' | 'MERGED'
    val decision: String,
    val score: Int?,
    // Comma-joined reason codes, display only.
    val reasons: String?,
    val decidedAt: Long,
)
