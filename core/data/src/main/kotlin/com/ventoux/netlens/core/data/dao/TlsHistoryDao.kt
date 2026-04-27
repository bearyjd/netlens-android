package com.ventoux.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.core.data.model.TlsHistoryEntry

@Dao
interface TlsHistoryDao {
    @Query("SELECT * FROM history_tls ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<TlsHistoryEntry>>

    @Query("SELECT * FROM history_tls WHERE host LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<TlsHistoryEntry>>

    @Query("SELECT * FROM history_tls WHERE id = :id")
    suspend fun getById(id: Long): TlsHistoryEntry?

    @Insert
    suspend fun insert(entry: TlsHistoryEntry)

    @Query("DELETE FROM history_tls WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_tls WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_tls")
    suspend fun deleteAll()
}
