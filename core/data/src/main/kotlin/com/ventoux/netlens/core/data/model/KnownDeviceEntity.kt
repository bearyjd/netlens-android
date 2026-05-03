package com.ventoux.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "known_devices",
    indices = [Index("lastSeen"), Index("isKnown")],
)
data class KnownDeviceEntity(
    @PrimaryKey val macAddress: String,
    val hostname: String?,
    val ip: String,
    val vendor: String?,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val isKnown: Boolean = false,
    val deviceType: String? = null,
    val osGuess: String? = null,
)
