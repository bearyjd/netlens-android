package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity

@Dao
interface DeviceIdentifierDao {
    @Query("SELECT * FROM device_identifiers WHERE kind = :kind AND value = :value AND scopeKey = :scopeKey")
    suspend fun find(kind: String, value: String, scopeKey: String): DeviceIdentifierEntity?

    @Query("SELECT * FROM device_identifiers WHERE deviceId = :deviceId")
    suspend fun getForDevice(deviceId: Long): List<DeviceIdentifierEntity>

    @Query("SELECT COUNT(*) FROM device_identifiers WHERE deviceId = :deviceId AND kind = :kind")
    suspend fun countByKind(deviceId: Long, kind: String): Int

    /** Count and insert in one transaction, so concurrent writers cannot overshoot [cap]. -1 = refused. */
    @Transaction
    suspend fun insertBelowCap(identifier: DeviceIdentifierEntity, cap: Int): Long =
        if (countByKind(identifier.deviceId, identifier.kind) >= cap) -1L else insertIfNew(identifier)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(identifier: DeviceIdentifierEntity): Long

    @Query("UPDATE device_identifiers SET lastSeen = :lastSeen WHERE id = :id")
    suspend fun touch(id: Long, lastSeen: Long)

    @Query("UPDATE OR IGNORE device_identifiers SET scopeKey = :scopeKey WHERE id = :id")
    // OR IGNORE: a unique-index collision leaves the row unchanged instead of throwing mid-scan.
    suspend fun rescope(id: Long, scopeKey: String): Int
}
