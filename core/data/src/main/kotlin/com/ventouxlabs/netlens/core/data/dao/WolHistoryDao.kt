package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.core.data.model.WolHistoryEntry

@Dao
interface WolHistoryDao {
    @Query("SELECT * FROM history_wol ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<WolHistoryEntry>>

    @Query("SELECT * FROM history_wol WHERE mac LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<WolHistoryEntry>>

    @Query("SELECT * FROM history_wol WHERE id = :id")
    suspend fun getById(id: Long): WolHistoryEntry?

    @Insert
    suspend fun insert(entry: WolHistoryEntry)

    @Query("DELETE FROM history_wol WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_wol WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_wol")
    suspend fun deleteAll()
}
