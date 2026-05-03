package com.ventoux.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventoux.netlens.core.data.model.KnownDeviceEntity

@Dao
interface KnownDeviceDao {
    @Query("SELECT * FROM known_devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<KnownDeviceEntity>>

    @Query("SELECT * FROM known_devices WHERE macAddress = :mac")
    suspend fun getByMac(mac: String): KnownDeviceEntity?

    @Query("SELECT * FROM known_devices WHERE isKnown = 0 ORDER BY lastSeen DESC")
    fun getUnknownDevices(): Flow<List<KnownDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(device: KnownDeviceEntity): Long

    @Query(
        "UPDATE known_devices SET hostname = :hostname, ip = :ip, vendor = :vendor, " +
            "lastSeen = :lastSeen, deviceType = :deviceType, osGuess = :osGuess " +
            "WHERE macAddress = :mac",
    )
    suspend fun updateLastSeen(
        mac: String,
        hostname: String?,
        ip: String,
        vendor: String?,
        lastSeen: Long,
        deviceType: String?,
        osGuess: String?,
    )

    @Query("UPDATE known_devices SET isKnown = :isKnown WHERE macAddress = :mac")
    suspend fun setKnown(mac: String, isKnown: Boolean)

    @Query(
        "SELECT * FROM known_devices WHERE hostname LIKE '%' || :query || '%' " +
            "OR ip LIKE '%' || :query || '%' OR vendor LIKE '%' || :query || '%' " +
            "OR macAddress LIKE '%' || :query || '%'",
    )
    fun search(query: String): Flow<List<KnownDeviceEntity>>

    @Query("DELETE FROM known_devices WHERE macAddress = :mac")
    suspend fun delete(mac: String)

    @Query("DELETE FROM known_devices")
    suspend fun deleteAll()
}
