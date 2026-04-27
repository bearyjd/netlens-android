package com.ventoux.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.core.data.model.MdnsHistoryEntry

@Dao
interface MdnsHistoryDao {
    @Query("SELECT * FROM history_mdns ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<MdnsHistoryEntry>>

    @Query("SELECT * FROM history_mdns WHERE servicesJson LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<MdnsHistoryEntry>>

    @Query("SELECT * FROM history_mdns WHERE id = :id")
    suspend fun getById(id: Long): MdnsHistoryEntry?

    @Insert
    suspend fun insert(entry: MdnsHistoryEntry)

    @Query("DELETE FROM history_mdns WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_mdns WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_mdns")
    suspend fun deleteAll()
}
