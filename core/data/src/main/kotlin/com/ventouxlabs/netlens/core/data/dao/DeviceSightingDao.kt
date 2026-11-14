package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity

@Dao
interface DeviceSightingDao {
    @Query("SELECT * FROM device_sightings WHERE deviceId = :deviceId ORDER BY lastSeen DESC")
    suspend fun getForDevice(deviceId: Long): List<DeviceSightingEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(sighting: DeviceSightingEntity): Long

    @Query(
        "UPDATE device_sightings SET lastIp = :lastIp, lastSeen = :lastSeen, networkLabel = :networkLabel " +
            "WHERE deviceId = :deviceId AND networkKey = :networkKey",
    )
    suspend fun updateSeen(deviceId: Long, networkKey: String, networkLabel: String?, lastIp: String, lastSeen: Long)

    // Keeps the original firstSeen: insert-ignore, then refresh the mutable columns.
    @Transaction
    suspend fun upsert(sighting: DeviceSightingEntity) {
        insertIfNew(sighting)
        updateSeen(sighting.deviceId, sighting.networkKey, sighting.networkLabel, sighting.lastIp, sighting.lastSeen)
    }
}
