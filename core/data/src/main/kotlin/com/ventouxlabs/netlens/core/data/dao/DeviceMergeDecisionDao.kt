package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceMergeDecisionDao {
    @Query("SELECT * FROM device_merge_decisions WHERE decision = 'SUGGESTED' ORDER BY score DESC")
    fun observeSuggestions(): Flow<List<DeviceMergeDecisionEntity>>

    @Query("SELECT * FROM device_merge_decisions WHERE deviceIdLow = :low AND deviceIdHigh = :high")
    suspend fun get(low: Long, high: Long): DeviceMergeDecisionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(decision: DeviceMergeDecisionEntity)
}
