package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.dao.DeviceSightingDao
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity

/**
 * In-memory [DeviceSightingDao]; primary key (deviceId, networkKey). `upsert` is inherited from the
 * interface, so it keeps the original `firstSeen` exactly as the real DAO does.
 */
class FakeDeviceSightingDao : DeviceSightingDao {
    private val rows = mutableListOf<DeviceSightingEntity>()

    val all: List<DeviceSightingEntity> get() = rows.toList()

    override suspend fun getForDevice(deviceId: Long): List<DeviceSightingEntity> =
        rows.filter { it.deviceId == deviceId }.sortedByDescending { it.lastSeen }

    override suspend fun insertIfNew(sighting: DeviceSightingEntity): Long {
        if (rows.any { sameKey(it, sighting) }) return -1L
        rows += sighting
        return rows.size.toLong()
    }

    override suspend fun updateSeen(
        deviceId: Long,
        networkKey: String,
        networkLabel: String?,
        lastIp: String,
        lastSeen: Long,
    ) {
        rows.replaceAll {
            if (it.deviceId == deviceId && it.networkKey == networkKey) {
                it.copy(networkLabel = networkLabel, lastIp = lastIp, lastSeen = lastSeen)
            } else {
                it
            }
        }
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

    private fun sameKey(a: DeviceSightingEntity, b: DeviceSightingEntity) =
        a.deviceId == b.deviceId && a.networkKey == b.networkKey
}
