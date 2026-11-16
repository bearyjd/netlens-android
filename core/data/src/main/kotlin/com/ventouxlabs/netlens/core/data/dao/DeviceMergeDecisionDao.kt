package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Scan paths may only [suggest] / [updateSuggestion]; both are no-ops on a pair the user already
 * decided. Only [setDistinct] (a user action) may change a stored decision.
 */
@Dao
interface DeviceMergeDecisionDao {
    @Query("SELECT * FROM device_merge_decisions WHERE decision = 'SUGGESTED' ORDER BY score DESC LIMIT 50")
    fun observeSuggestions(): Flow<List<DeviceMergeDecisionEntity>>

    @Query("SELECT * FROM device_merge_decisions WHERE deviceIdLow = :low AND deviceIdHigh = :high")
    suspend fun get(low: Long, high: Long): DeviceMergeDecisionEntity?

    @Query(
        "SELECT COUNT(*) FROM device_merge_decisions WHERE decision = 'DISTINCT' " +
            "AND (deviceIdLow IN (:ids) OR deviceIdHigh IN (:ids))",
    )
    suspend fun distinctCountTouching(ids: List<Long>): Int

    /** True when the user ever said any of [ids] is a different device from another row. */
    suspend fun touchesDistinct(ids: List<Long>): Boolean = ids.isNotEmpty() && distinctCountTouching(ids) > 0

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
}
