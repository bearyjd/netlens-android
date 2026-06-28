package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history_traceroute", indices = [Index("timestamp")])
data class TracerouteHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val host: String,
    val hopCount: Int,
    val hopsJson: String,
)
