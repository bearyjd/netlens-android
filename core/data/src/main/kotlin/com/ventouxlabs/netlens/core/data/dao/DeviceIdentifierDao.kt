package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity

@Dao
interface DeviceIdentifierDao {
    @Query("SELECT * FROM device_identifiers WHERE kind = :kind AND value = :value AND scopeKey = :scopeKey")
    suspend fun find(kind: String, value: String, scopeKey: String): DeviceIdentifierEntity?

    @Query("SELECT * FROM device_identifiers WHERE deviceId = :deviceId")
    suspend fun getForDevice(deviceId: Long): List<DeviceIdentifierEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(identifier: DeviceIdentifierEntity): Long

    @Query("UPDATE device_identifiers SET lastSeen = :lastSeen WHERE id = :id")
    suspend fun touch(id: Long, lastSeen: Long)

    @Query("UPDATE OR IGNORE device_identifiers SET scopeKey = :scopeKey WHERE id = :id")
    // OR IGNORE: a unique-index collision leaves the row unchanged instead of throwing mid-scan.
    suspend fun rescope(id: Long, scopeKey: String): Int
}
