package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.core.data.model.HttpTesterHistoryEntry

@Dao
interface HttpTesterHistoryDao {
    @Query("SELECT * FROM history_http ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<HttpTesterHistoryEntry>>

    @Query("SELECT * FROM history_http WHERE url LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<HttpTesterHistoryEntry>>

    @Query("SELECT * FROM history_http WHERE id = :id")
    suspend fun getById(id: Long): HttpTesterHistoryEntry?

    @Insert
    suspend fun insert(entry: HttpTesterHistoryEntry)

    @Query("DELETE FROM history_http WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_http WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_http")
    suspend fun deleteAll()
}
