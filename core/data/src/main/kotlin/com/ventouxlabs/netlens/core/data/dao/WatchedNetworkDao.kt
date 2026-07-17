package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedNetworkDao {
    @Query("SELECT * FROM watched_networks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchedNetworkEntity>>

    @Query("SELECT * FROM watched_networks WHERE gatewayMac = :mac")
    suspend fun getByGatewayMac(mac: String): WatchedNetworkEntity?

    // Unique index on gatewayMac makes REPLACE an idempotent upsert-by-identity.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(network: WatchedNetworkEntity): Long

    @Query("UPDATE watched_networks SET watchEnabled = :enabled WHERE id = :id")
    suspend fun setWatchEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM watched_networks WHERE id = :id")
    suspend fun delete(id: Long)
}
