package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One piece of fingerprint evidence for a device; capped at 64 rows per device by the repository. */
@Entity(
    tableName = "device_observations",
    foreignKeys = [
        ForeignKey(
            entity = KnownDeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["deviceId", "source", "field", "value"], unique = true, name = "index_device_observations_dedupe"),
    ],
)
data class DeviceObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: Long,
    val source: String,
    val field: String,
    val value: String,
    val weight: Int,
    val detail: String?,
    val lastSeen: Long,
)
