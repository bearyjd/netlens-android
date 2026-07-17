package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watched_networks",
    indices = [Index(value = ["gatewayMac"], unique = true)],
)
data class WatchedNetworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // SSID captured in the foreground for display only; identity is the gateway MAC.
    val displayName: String?,
    val gatewayMac: String,
    val subnet: String,
    val watchEnabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
)
