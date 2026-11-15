package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.dao.DeviceObservationDao
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity

/**
 * In-memory [DeviceObservationDao]. [upsert] is `REPLACE`: a row colliding on the dedupe key
 * (deviceId, source, field, value) is deleted and the new row is inserted with a NEW id.
 */
class FakeDeviceObservationDao : DeviceObservationDao {
    private val rows = mutableListOf<DeviceObservationEntity>()
    private var nextId = 1L

    val all: List<DeviceObservationEntity> get() = rows.toList()

    override suspend fun getForDevice(deviceId: Long): List<DeviceObservationEntity> =
        rows.filter { it.deviceId == deviceId }.sortedByDescending { it.lastSeen }

    override suspend fun upsert(observation: DeviceObservationEntity): Long {
        rows.removeAll { (observation.id != 0L && it.id == observation.id) || sameKey(it, observation) }
        val withId = observation.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    override suspend fun trimToNewest(deviceId: Long, keep: Int) {
        val kept = rows.filter { it.deviceId == deviceId }
            .sortedWith(compareByDescending<DeviceObservationEntity> { it.lastSeen }.thenByDescending { it.id })
            .take(keep)
            .toSet()
        rows.removeAll { it.deviceId == deviceId && it !in kept }
    }

    override suspend fun trimToStrongest(deviceId: Long, keep: Int) {
        val kept = rows.filter { it.deviceId == deviceId }
            .sortedWith(
                compareByDescending<DeviceObservationEntity> { it.weight }
                    .thenByDescending { it.lastSeen }
                    .thenByDescending { it.id },
            )
            .take(keep)
            .toSet()
        rows.removeAll { it.deviceId == deviceId && it !in kept }
    }

    /** `INSERT OR IGNORE` for [FakeDeviceIdentityDao]'s transactional insert. */
    internal fun insertIgnoring(observation: DeviceObservationEntity): Long {
        if (rows.any { sameKey(it, observation) }) return -1L
        val withId = observation.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    /** `UPDATE OR IGNORE ... SET deviceId`: rows that would collide stay with [from]. */
    internal fun moveAll(from: Long, to: Long) {
        for (row in rows.filter { it.deviceId == from }) {
            val moved = row.copy(deviceId = to)
            if (rows.none { sameKey(it, moved) }) rows[rows.indexOf(row)] = moved
        }
    }

    internal fun removeForDevice(deviceId: Long) {
        rows.removeAll { it.deviceId == deviceId }
    }

    private fun sameKey(a: DeviceObservationEntity, b: DeviceObservationEntity) =
        a.deviceId == b.deviceId && a.source == b.source && a.field == b.field && a.value == b.value
}
