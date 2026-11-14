package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity

@Dao
interface DeviceObservationDao {
    @Query("SELECT * FROM device_observations WHERE deviceId = :deviceId ORDER BY lastSeen DESC")
    suspend fun getForDevice(deviceId: Long): List<DeviceObservationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(observation: DeviceObservationEntity): Long

    // Keeps the newest [keep] rows per device; the repository calls this after each write.
    @Query(
        "DELETE FROM device_observations WHERE deviceId = :deviceId AND id NOT IN " +
            "(SELECT id FROM device_observations WHERE deviceId = :deviceId ORDER BY lastSeen DESC, id DESC LIMIT :keep)",
    )
    suspend fun trimToNewest(deviceId: Long, keep: Int)
}
