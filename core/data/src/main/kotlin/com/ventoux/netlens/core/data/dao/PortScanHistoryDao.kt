package com.ventoux.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.core.data.model.PortScanHistoryEntry

@Dao
interface PortScanHistoryDao {
    @Query("SELECT * FROM history_portscan ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<PortScanHistoryEntry>>

    @Query("SELECT * FROM history_portscan WHERE host LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<PortScanHistoryEntry>>

    @Query("SELECT * FROM history_portscan WHERE id = :id")
    suspend fun getById(id: Long): PortScanHistoryEntry?

    @Insert
    suspend fun insert(entry: PortScanHistoryEntry)

    @Query("DELETE FROM history_portscan WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_portscan WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_portscan")
    suspend fun deleteAll()
}
