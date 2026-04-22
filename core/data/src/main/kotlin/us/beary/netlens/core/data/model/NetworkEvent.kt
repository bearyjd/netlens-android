package us.beary.netlens.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_events")
data class NetworkEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val transportType: String,
    val networkDetails: String,
    val isVpn: Boolean = false,
)
