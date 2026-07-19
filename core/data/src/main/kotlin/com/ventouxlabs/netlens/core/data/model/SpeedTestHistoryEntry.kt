package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history_speedtest", indices = [Index("timestamp")])
data class SpeedTestHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val downloadMbps: Float,
    val uploadMbps: Float,
    val latencyMs: Long,
    val serverName: String,
    // How latencyMs was measured. New rows are raw TCP-connect RTT; rows written before
    // schema v14 timed a full HTTPS HEAD and are tagged LEGACY_HTTP by MIGRATION_13_14.
    val latencyMethod: String = LATENCY_METHOD_TCP_CONNECT,
) {
    companion object {
        const val LATENCY_METHOD_TCP_CONNECT = "TCP_CONNECT"
        const val LATENCY_METHOD_LEGACY_HTTP = "LEGACY_HTTP"
    }
}
