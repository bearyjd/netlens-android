package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.MergeKind
import com.ventouxlabs.netlens.core.data.model.MergeLogEntity
import com.ventouxlabs.netlens.core.data.model.fillEmptyUserFieldsFrom
import com.ventouxlabs.netlens.core.data.model.hasUserDetails
import com.ventouxlabs.netlens.core.data.model.mergeLogEntry

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

    @Query(
        "SELECT COUNT(*) FROM device_merge_decisions WHERE decision = 'DISTINCT' " +
            "AND (deviceIdLow = :id OR deviceIdHigh = :id)",
    )
    suspend fun distinctCountFor(id: Long): Int

    @Query("SELECT COUNT(*) FROM known_devices WHERE macAddress = :mac")
    suspend fun macCount(mac: String): Int

    // ABORT (default): inside insertDeviceWithIdentifiers a violation must roll the transaction back.
    @Insert
    suspend fun insertDeviceRow(device: KnownDeviceEntity): Long

    @Insert
    suspend fun insertIdentifierRow(identifier: DeviceIdentifierEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertObservationRow(observation: DeviceObservationEntity): Long

    @Query("SELECT * FROM device_identifiers WHERE deviceId = :id ORDER BY id")
    suspend fun identifiersOf(id: Long): List<DeviceIdentifierEntity>

    @Query("SELECT * FROM device_sightings WHERE deviceId = :id ORDER BY networkKey")
    suspend fun sightingsOf(id: Long): List<DeviceSightingEntity>

    @Query("SELECT * FROM device_merge_decisions WHERE deviceIdLow = :low AND deviceIdHigh = :high")
    suspend fun decisionFor(low: Long, high: Long): DeviceMergeDecisionEntity?

    @Insert
    suspend fun insertMergeLogRow(entry: MergeLogEntity): Long

    // Keeps the newest MERGE_LOG_CAP rows, USER rows ranked ahead of every AUTO row, so a flood of
    // automatic merges evicts only AUTO history. Ordered by id: mergedAt can tie or go backwards.
    @Query(
        "DELETE FROM merge_log WHERE id NOT IN (SELECT id FROM merge_log " +
            "ORDER BY CASE kind WHEN 'USER' THEN 0 ELSE 1 END, id DESC LIMIT $MERGE_LOG_CAP)",
    )
    suspend fun pruneMergeLog()

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
     *
     * Once every check passes, and before anything moves, a `merge_log` row snapshots the loser, its
     * identifiers and sightings, the pair's decision and the survivor's user fields, then the log is
     * pruned to [MERGE_LOG_CAP]. A failure anywhere rolls the log row back with the merge.
     *
     * [isKnown] == null means an AUTOMATIC merge, whose rules are re-checked here inside the
     * transaction because the user may have edited or decided since the resolver looked: refused when
     * the loser touches any DISTINCT decision (its decisions would cascade away), when both rows carry
     * user details, or when `isKnown` differs. A user-confirmed merge passes an explicit [isKnown].
     * [kind] is what the log records; it must agree with [isKnown] (AUTO exactly when it is null).
     */
    @Transaction
    suspend fun mergeDevices(survivorId: Long, loserId: Long, isKnown: Boolean?, kind: MergeKind): Boolean {
        require((kind == MergeKind.AUTO) == (isKnown == null)) { "kind $kind contradicts isKnown $isKnown" }
        val survivor = deviceById(survivorId) ?: return false
        val loser = deviceById(loserId) ?: return false
        if (survivorId == loserId) return false
        if (isKnown == null && !automaticMergeAllowed(survivor, loser)) return false
        val decision = decisionFor(minOf(survivorId, loserId), maxOf(survivorId, loserId))
        insertMergeLogRow(
            mergeLogEntry(
                survivor, loser, identifiersOf(loserId), sightingsOf(loserId), decision, kind,
                mergedAt = System.currentTimeMillis(),
            ),
        )
        pruneMergeLog()
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

    private suspend fun automaticMergeAllowed(survivor: KnownDeviceEntity, loser: KnownDeviceEntity): Boolean =
        distinctCountFor(loser.id) == 0 &&
            !(survivor.hasUserDetails() && loser.hasUserDetails()) &&
            survivor.isKnown == loser.isKnown

    companion object {
        /** What `insertDeviceWithIdentifiers` returns when it inserted nothing. */
        const val DUPLICATE = -1L

        /** Most `merge_log` rows kept; see [pruneMergeLog]. */
        const val MERGE_LOG_CAP = 200
    }
}
