package com.ventoux.netlens.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitored_endpoints")
data class MonitoredEndpoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val url: String,
    val intervalSeconds: Int = 60,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
