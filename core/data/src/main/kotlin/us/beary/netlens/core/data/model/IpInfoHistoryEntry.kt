package us.beary.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history_ipinfo", indices = [Index("timestamp")])
data class IpInfoHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val ip: String,
    val isp: String?,
    val org: String?,
    val countryCode: String?,
    val city: String?,
    val isVpn: Boolean,
)
