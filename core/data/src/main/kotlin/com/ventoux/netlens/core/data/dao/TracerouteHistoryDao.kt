package com.ventoux.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.core.data.model.TracerouteHistoryEntry

@Dao
interface TracerouteHistoryDao {
    @Query("SELECT * FROM history_traceroute ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<TracerouteHistoryEntry>>

    @Query("SELECT * FROM history_traceroute WHERE host LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<TracerouteHistoryEntry>>

    @Query("SELECT * FROM history_traceroute WHERE id = :id")
    suspend fun getById(id: Long): TracerouteHistoryEntry?

    @Insert
    suspend fun insert(entry: TracerouteHistoryEntry)

    @Query("DELETE FROM history_traceroute WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_traceroute WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_traceroute")
    suspend fun deleteAll()
}
