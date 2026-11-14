package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Scan paths may only [suggest] / [updateSuggestion]; both are no-ops on a pair the user already
 * decided. Only [setDistinct] / [setMerged] (user actions) may change a stored decision.
 */
@Dao
interface DeviceMergeDecisionDao {
    @Query("SELECT * FROM device_merge_decisions WHERE decision = 'SUGGESTED' ORDER BY score DESC LIMIT 50")
    fun observeSuggestions(): Flow<List<DeviceMergeDecisionEntity>>

    @Query("SELECT * FROM device_merge_decisions WHERE deviceIdLow = :low AND deviceIdHigh = :high")
    suspend fun get(low: Long, high: Long): DeviceMergeDecisionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun suggest(decision: DeviceMergeDecisionEntity): Long

    @Query(
        "UPDATE device_merge_decisions SET score = :score, reasons = :reasons " +
            "WHERE deviceIdLow = :low AND deviceIdHigh = :high AND decision = 'SUGGESTED'",
    )
    suspend fun updateSuggestion(low: Long, high: Long, score: Int?, reasons: String?): Int

    @Query(
        "INSERT OR REPLACE INTO device_merge_decisions(deviceIdLow, deviceIdHigh, decision, score, reasons, decidedAt) " +
            "VALUES (:low, :high, 'DISTINCT', NULL, NULL, :decidedAt)",
    )
    suspend fun setDistinct(low: Long, high: Long, decidedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO device_merge_decisions(deviceIdLow, deviceIdHigh, decision, score, reasons, decidedAt) " +
            "VALUES (:low, :high, 'MERGED', NULL, NULL, :decidedAt)",
    )
    suspend fun setMerged(low: Long, high: Long, decidedAt: Long)
}
