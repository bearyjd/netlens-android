package com.ventoux.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history_http", indices = [Index("timestamp")])
data class HttpTesterHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val url: String,
    val method: String,
    val statusCode: Int,
    val durationMs: Long,
    val responseSize: Long,
)
