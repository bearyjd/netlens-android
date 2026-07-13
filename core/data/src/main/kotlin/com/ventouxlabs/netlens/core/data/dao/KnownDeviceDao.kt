package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity

@Dao
interface KnownDeviceDao {
    @Query("SELECT * FROM known_devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<KnownDeviceEntity>>

    @Query("SELECT * FROM known_devices WHERE macAddress = :mac")
    suspend fun getByMac(mac: String): KnownDeviceEntity?

    // Fallback identity for devices with no resolvable MAC: matches an existing
    // mac-less row for the same IP so re-scans update it in place instead of
    // creating a new row every time (and so it upgrades to mac-keyed once resolved).
    @Query("SELECT * FROM known_devices WHERE ip = :ip AND macAddress IS NULL")
    suspend fun getByIpWithoutMac(ip: String): KnownDeviceEntity?

    @Query("SELECT * FROM known_devices WHERE isKnown = 0 ORDER BY lastSeen DESC")
    fun getUnknownDevices(): Flow<List<KnownDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(device: KnownDeviceEntity): Long

    @Query(
        "UPDATE known_devices SET hostname = :hostname, ip = :ip, vendor = :vendor, " +
            "lastSeen = :lastSeen, deviceType = :deviceType, osGuess = :osGuess " +
            "WHERE id = :id",
    )
    suspend fun updateLastSeen(
        id: Long,
        hostname: String?,
        ip: String,
        vendor: String?,
        lastSeen: Long,
        deviceType: String?,
        osGuess: String?,
    )

    @Query("UPDATE known_devices SET macAddress = :mac WHERE id = :id")
    suspend fun setMacAddress(id: Long, mac: String)

    @Query("UPDATE known_devices SET isKnown = :isKnown WHERE id = :id")
    suspend fun setKnown(id: Long, isKnown: Boolean)

    @Query(
        "SELECT * FROM known_devices WHERE hostname LIKE '%' || :query || '%' " +
            "OR ip LIKE '%' || :query || '%' OR vendor LIKE '%' || :query || '%' " +
            "OR macAddress LIKE '%' || :query || '%'",
    )
    fun search(query: String): Flow<List<KnownDeviceEntity>>

    @Query("DELETE FROM known_devices WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM known_devices")
    suspend fun deleteAll()
}
