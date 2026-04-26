package com.ventoux.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.core.data.model.WhoisHistoryEntry

@Dao
interface WhoisHistoryDao {
    @Query("SELECT * FROM history_whois ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<WhoisHistoryEntry>>

    @Query("SELECT * FROM history_whois WHERE `query` LIKE '%' || :searchQuery || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(searchQuery: String, limit: Int = 50): Flow<List<WhoisHistoryEntry>>

    @Query("SELECT * FROM history_whois WHERE id = :id")
    suspend fun getById(id: Long): WhoisHistoryEntry?

    @Insert
    suspend fun insert(entry: WhoisHistoryEntry)

    @Query("DELETE FROM history_whois WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_whois WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_whois")
    suspend fun deleteAll()
}
