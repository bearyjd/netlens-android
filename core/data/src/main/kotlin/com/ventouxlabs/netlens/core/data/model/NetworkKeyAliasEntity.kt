package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Maps every key a network has been identified by to one canonical key. */
@Entity(
    tableName = "network_key_aliases",
    indices = [Index("canonicalKey")],
)
data class NetworkKeyAliasEntity(
    @PrimaryKey val aliasKey: String,
    val canonicalKey: String,
    val firstSeen: Long,
)
