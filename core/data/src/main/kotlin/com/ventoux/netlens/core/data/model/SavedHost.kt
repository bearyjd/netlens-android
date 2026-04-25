package com.ventoux.netlens.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_hosts")
data class SavedHost(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostname: String,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
