package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.fillEmptyUserFieldsFrom

/**
 * The two multi-table writes of device identity, each one SQLite transaction (Room cannot span
 * DAOs, so the building-block statements live here too; callers use only the two `@Transaction`
 * methods).
 */
@Dao
interface DeviceIdentityDao {
    @Query("SELECT * FROM known_devices WHERE id = :id")
    suspend fun deviceById(id: Long): KnownDeviceEntity?

    @Query("SELECT COUNT(*) FROM device_identifiers WHERE kind = :kind AND value = :value AND scopeKey = :scopeKey")
    suspend fun identifierCount(kind: String, value: String, scopeKey: String): Int

    @Query("SELECT COUNT(*) FROM known_devices WHERE macAddress = :mac")
    suspend fun macCount(mac: String): Int

    // ABORT (default): inside insertDeviceWithIdentifiers a violation must roll the transaction back.
    @Insert
    suspend fun insertDeviceRow(device: KnownDeviceEntity): Long

    @Insert
    suspend fun insertIdentifierRow(identifier: DeviceIdentifierEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertObservationRow(observation: DeviceObservationEntity): Long

    @Query("UPDATE device_identifiers SET deviceId = :to WHERE deviceId = :from")
    suspend fun moveIdentifiers(from: Long, to: Long)

    // OR IGNORE: the unique keys of these tables include deviceId, so a row the survivor already
    // has would collide; the loser's copy stays and is removed by the cascade with the loser.
    @Query("UPDATE OR IGNORE device_observations SET deviceId = :to WHERE deviceId = :from")
    suspend fun moveObservations(from: Long, to: Long)

    @Query("UPDATE OR IGNORE device_sightings SET deviceId = :to WHERE deviceId = :from")
    suspend fun moveSightings(from: Long, to: Long)

    @Query(
        "UPDATE known_devices SET customName = :customName, tags = :tags, notes = :notes, " +
            "location = :location, deviceTypeOverride = :deviceTypeOverride, " +
            "osOverride = :osOverride, modelOverride = :modelOverride WHERE id = :id",
    )
    suspend fun writeUserFields(
        id: Long,
        customName: String?,
        tags: String?,
        notes: String?,
        location: String?,
        deviceTypeOverride: String?,
        osOverride: String?,
        modelOverride: String?,
    )

    @Query("UPDATE known_devices SET isKnown = :isKnown WHERE id = :id")
    suspend fun writeKnown(id: Long, isKnown: Boolean)

    @Query("DELETE FROM known_devices WHERE id = :id")
    suspend fun deleteDevice(id: Long)

    /**
     * Inserts the row with its identifiers and observations, or returns -1 (inserting nothing) when
     * another writer already owns one of the identifiers — or, with [dedupeByMac], the MAC column.
     * The check and the insert share one transaction, which replaces the unique MAC index v19 dropped.
     * Identifier/observation `deviceId` values on input are ignored. A constraint violation after the
     * pre-check (e.g. the same identifier twice in [identifiers]) throws and rolls everything back.
     */
    @Transaction
    suspend fun insertDeviceWithIdentifiers(
        device: KnownDeviceEntity,
        identifiers: List<DeviceIdentifierEntity>,
        observations: List<DeviceObservationEntity>,
        dedupeByMac: Boolean,
    ): Long {
        if (identifiers.any { identifierCount(it.kind, it.value, it.scopeKey) > 0 }) return DUPLICATE
        if (dedupeByMac && device.macAddress != null && macCount(device.macAddress) > 0) return DUPLICATE
        val id = insertDeviceRow(device)
        identifiers.forEach { insertIdentifierRow(it.copy(id = 0, deviceId = id)) }
        observations.forEach { insertObservationRow(it.copy(id = 0, deviceId = id)) }
        return id
    }

    /**
     * Folds [loserId] into [survivorId]: identifiers, observations and sightings move over, the
     * loser's user-authored fields fill only the survivor's *empty* fields, the survivor keeps its
     * `isKnown` unless [isKnown] is given, and the loser row is deleted LAST (its children cascade
     * with it, so they must have moved first). Returns false, touching nothing, if either row is gone.
     */
    @Transaction
    suspend fun mergeDevices(survivorId: Long, loserId: Long, isKnown: Boolean?): Boolean {
        val survivor = deviceById(survivorId) ?: return false
        val loser = deviceById(loserId) ?: return false
        if (survivorId == loserId) return false
        moveIdentifiers(loserId, survivorId)
        moveObservations(loserId, survivorId)
        moveSightings(loserId, survivorId)
        val filled = survivor.fillEmptyUserFieldsFrom(loser)
        writeUserFields(
            survivorId, filled.customName, filled.tags, filled.notes, filled.location,
            filled.deviceTypeOverride, filled.osOverride, filled.modelOverride,
        )
        if (isKnown != null) writeKnown(survivorId, isKnown)
        deleteDevice(loserId)
        return true
    }

    companion object {
        /** What `insertDeviceWithIdentifiers` returns when it inserted nothing. */
        const val DUPLICATE = -1L
    }
}
