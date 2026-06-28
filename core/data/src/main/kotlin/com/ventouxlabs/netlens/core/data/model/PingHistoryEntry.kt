package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history_ping", indices = [Index("timestamp")])
data class PingHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val host: String,
    val sentCount: Int,
    val receivedCount: Int,
    val minMs: Float,
    val avgMs: Float,
    val maxMs: Float,
    val mode: String = "FIXED",
)
