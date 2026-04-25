package us.beary.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history_lanscan", indices = [Index("timestamp")])
data class LanScanHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val ssid: String?,
    val subnet: String?,
    val deviceCount: Int,
    val devicesJson: String,
)
