package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.core.data.model.DnsHistoryEntry

@Dao
interface DnsHistoryDao {
    @Query("SELECT * FROM history_dns ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<DnsHistoryEntry>>

    @Query("SELECT * FROM history_dns WHERE `query` LIKE '%' || :searchQuery || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(searchQuery: String, limit: Int = 50): Flow<List<DnsHistoryEntry>>

    @Query("SELECT * FROM history_dns WHERE id = :id")
    suspend fun getById(id: Long): DnsHistoryEntry?

    @Insert
    suspend fun insert(entry: DnsHistoryEntry)

    @Query("DELETE FROM history_dns WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_dns WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_dns")
    suspend fun deleteAll()
}
