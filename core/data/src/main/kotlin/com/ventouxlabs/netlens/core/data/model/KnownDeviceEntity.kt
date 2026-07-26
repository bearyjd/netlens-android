package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "known_devices",
    indices = [Index("lastSeen"), Index("isKnown"), Index(value = ["macAddress"], unique = true), Index("ip")],
)
data class KnownDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Nullable: many discovered devices never get a resolvable MAC (mDNS/SSDP-only
    // devices that don't answer pings, or devices absent from /proc/net/arp). Identity
    // then falls back to IP — see LanScanViewModel.persistScanResults.
    val macAddress: String?,
    val hostname: String?,
    val ip: String,
    val vendor: String?,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val isKnown: Boolean = false,
    val deviceType: String? = null,
    val osGuess: String? = null,
    // User-supplied friendly name; display precedence is customName ?: hostname ?: vendor ?: ip.
    val customName: String? = null,
    // Watched-network this row was last tagged against (null = unwatched/legacy).
    val networkId: Long? = null,
    // User-supplied labels, comma-separated. Always read/written through DeviceTags so the
    // stored form stays normalised (trimmed, de-duplicated, no embedded commas).
    val tags: String? = null,
    // Free-form user note about the device.
    val notes: String? = null,
    // Where the device physically lives ("Garage", "Kids' room"). Kept separate from tags so
    // it can be shown as a distinct field and sorted on.
    val location: String? = null,
)
