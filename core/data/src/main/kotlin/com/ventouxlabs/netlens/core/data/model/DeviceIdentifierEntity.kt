package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A stable identifier (MAC, UPnP UDN, ...) owned by one known device. [scopeKey] is NOT NULL:
 * "" = global, otherwise the NetworkKey — SQLite treats NULLs as distinct in a UNIQUE index.
 * [kind] and [source] hold IdentifierKind / ObservationSource names (core:scan owns those enums).
 */
@Entity(
    tableName = "device_identifiers",
    foreignKeys = [
        ForeignKey(
            entity = KnownDeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["kind", "value", "scopeKey"], unique = true),
        Index("deviceId"),
    ],
)
data class DeviceIdentifierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: Long,
    val kind: String,
    val value: String,
    val scopeKey: String,
    val source: String,
    val proven: Boolean = false,
    val firstSeen: Long,
    val lastSeen: Long,
)
