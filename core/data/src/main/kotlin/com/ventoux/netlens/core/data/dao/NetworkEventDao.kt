package com.ventoux.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.core.data.model.NetworkEvent

@Dao
interface NetworkEventDao {

    @Query("SELECT * FROM network_events ORDER BY timestamp DESC")
    fun getAll(): Flow<List<NetworkEvent>>

    @Query("SELECT * FROM network_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<NetworkEvent>>

    @Query(
        "SELECT * FROM network_events " +
            "WHERE (:hasTypeFilter = 0 OR eventType IN (:types)) " +
            "AND (:from IS NULL OR timestamp >= :from) " +
            "AND (:to IS NULL OR timestamp <= :to) " +
            "ORDER BY timestamp DESC LIMIT :limit",
    )
    fun getFiltered(
        types: Set<String>,
        hasTypeFilter: Int,
        from: Long?,
        to: Long?,
        limit: Int,
    ): Flow<List<NetworkEvent>>

    @Insert
    suspend fun insert(event: NetworkEvent)

    @Query("DELETE FROM network_events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM network_events")
    suspend fun deleteAll()
}
