package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey

/** Where (which network) a device has been seen — cross-network history. */
@Entity(
    tableName = "device_sightings",
    primaryKeys = ["deviceId", "networkKey"],
    foreignKeys = [
        ForeignKey(
            entity = KnownDeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DeviceSightingEntity(
    val deviceId: Long,
    val networkKey: String,
    val networkLabel: String?,
    val lastIp: String,
    val firstSeen: Long,
    val lastSeen: Long,
)
