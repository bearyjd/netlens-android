package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.dao.DeviceIdentityDao
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.MergeLogEntity

/**
 * [DeviceIdentityDao] over the other in-memory fakes. Only the building-block statements are
 * implemented here; `insertDeviceWithIdentifiers` and `mergeDevices` are the interface's own default
 * methods, so the orchestration (check order, move-before-delete) is the production code, not a copy.
 *
 * Not atomic: a failure mid-transaction is not rolled back. That property is proven against real
 * SQLite in `DeviceIdentityDaoTest`. [deleteDevice] emulates `ON DELETE CASCADE` across the fakes.
 * `merge_log` rows live in [FakeKnownDeviceDao.mergeLog].
 */
class FakeDeviceIdentityDao(
    private val devices: FakeKnownDeviceDao,
    private val identifiers: FakeDeviceIdentifierDao,
    private val observations: FakeDeviceObservationDao,
    private val sightings: FakeDeviceSightingDao,
    private val decisions: FakeDeviceMergeDecisionDao,
) : DeviceIdentityDao {

    override suspend fun deviceById(id: Long): KnownDeviceEntity? = devices.byId(id)

    override suspend fun distinctCountFor(id: Long): Int = decisions.distinctCountTouching(listOf(id))

    override suspend fun identifierCount(kind: String, value: String, scopeKey: String): Int =
        identifiers.countOf(kind, value, scopeKey)

    override suspend fun macCount(mac: String): Int = devices.allDevices.count { it.macAddress == mac }

    override suspend fun insertDeviceRow(device: KnownDeviceEntity): Long = devices.insertRaw(device)

    override suspend fun insertIdentifierRow(identifier: DeviceIdentifierEntity): Long =
        identifiers.insertStrict(identifier)

    override suspend fun insertObservationRow(observation: DeviceObservationEntity): Long =
        observations.insertIgnoring(observation)

    override suspend fun identifiersOf(id: Long): List<DeviceIdentifierEntity> =
        identifiers.getForDevice(id).sortedBy { it.id }

    override suspend fun sightingsOf(id: Long): List<DeviceSightingEntity> =
        sightings.getForDevice(id).sortedBy { it.networkKey }

    override suspend fun decisionFor(low: Long, high: Long): DeviceMergeDecisionEntity? = decisions.get(low, high)

    override suspend fun insertMergeLogRow(entry: MergeLogEntity): Long = devices.insertMergeLog(entry)

    override suspend fun pruneMergeLog() = devices.pruneMergeLog(DeviceIdentityDao.MERGE_LOG_CAP)

    override suspend fun moveIdentifiers(from: Long, to: Long) = identifiers.moveAll(from, to)

    override suspend fun moveObservations(from: Long, to: Long) = observations.moveAll(from, to)

    override suspend fun moveSightings(from: Long, to: Long) = sightings.moveAll(from, to)

    override suspend fun writeUserFields(
        id: Long,
        customName: String?,
        tags: String?,
        notes: String?,
        location: String?,
        deviceTypeOverride: String?,
        osOverride: String?,
        modelOverride: String?,
    ) = devices.updateUserDetails(id, customName, tags, notes, location, deviceTypeOverride, osOverride, modelOverride)

    override suspend fun writeKnown(id: Long, isKnown: Boolean) = devices.writeKnown(id, isKnown)

    // The raw row delete, like the real `DELETE FROM known_devices`: a merge never touches merge_log.
    override suspend fun deleteDevice(id: Long) {
        devices.deleteRow(id)
        identifiers.removeForDevice(id)
        observations.removeForDevice(id)
        sightings.removeForDevice(id)
        decisions.removeForDevice(id)
    }
}
