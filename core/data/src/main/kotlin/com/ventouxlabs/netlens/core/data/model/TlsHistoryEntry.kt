package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history_tls", indices = [Index("timestamp")])
data class TlsHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val host: String,
    val port: Int = 443,
    val issuer: String,
    val subject: String,
    val expiresAt: String,
    val protocol: String,
    val isValid: Boolean,
)
