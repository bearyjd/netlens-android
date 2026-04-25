package com.ventoux.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history_dns", indices = [Index("timestamp")])
data class DnsHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val query: String,
    val recordType: String,
    val resultsJson: String,
)
