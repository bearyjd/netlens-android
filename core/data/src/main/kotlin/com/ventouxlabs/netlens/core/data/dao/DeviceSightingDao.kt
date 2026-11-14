package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity

@Dao
interface DeviceSightingDao {
    @Query("SELECT * FROM device_sightings WHERE deviceId = :deviceId ORDER BY lastSeen DESC")
    suspend fun getForDevice(deviceId: Long): List<DeviceSightingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sighting: DeviceSightingEntity)
}
