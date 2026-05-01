package com.ventoux.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.core.data.model.SpeedTestHistoryEntry

@Dao
interface SpeedTestHistoryDao {
    @Query("SELECT * FROM history_speedtest ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<SpeedTestHistoryEntry>>

    @Query("SELECT * FROM history_speedtest WHERE serverName LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<SpeedTestHistoryEntry>>

    @Query("SELECT * FROM history_speedtest WHERE id = :id")
    suspend fun getById(id: Long): SpeedTestHistoryEntry?

    @Insert
    suspend fun insert(entry: SpeedTestHistoryEntry)

    @Query("DELETE FROM history_speedtest WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_speedtest WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_speedtest")
    suspend fun deleteAll()
}
