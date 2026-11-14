package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.dao.DeviceIdentifierDao
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity

/**
 * In-memory [DeviceIdentifierDao] honouring the real contracts: `UNIQUE(kind, value, scopeKey)`
 * makes [insertIfNew] return -1, and [rescope] is `UPDATE OR IGNORE` (0 rows on a collision).
 * Foreign keys are not enforced; cascades are emulated by [FakeDeviceIdentityDao].
 */
class FakeDeviceIdentifierDao : DeviceIdentifierDao {
    private val rows = mutableListOf<DeviceIdentifierEntity>()
    private var nextId = 1L

    val all: List<DeviceIdentifierEntity> get() = rows.toList()

    override suspend fun find(kind: String, value: String, scopeKey: String): DeviceIdentifierEntity? =
        rows.find { it.kind == kind && it.value == value && it.scopeKey == scopeKey }

    override suspend fun getForDevice(deviceId: Long): List<DeviceIdentifierEntity> =
        rows.filter { it.deviceId == deviceId }

    override suspend fun countByKind(deviceId: Long, kind: String): Int =
        rows.count { it.deviceId == deviceId && it.kind == kind }

    override suspend fun insertIfNew(identifier: DeviceIdentifierEntity): Long {
        if (rows.any { sameKey(it, identifier) }) return DUPLICATE
        val withId = identifier.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    override suspend fun touch(id: Long, lastSeen: Long) {
        replace(id) { it.copy(lastSeen = lastSeen) }
    }

    override suspend fun rescope(id: Long, scopeKey: String): Int {
        val row = rows.find { it.id == id } ?: return 0
        val moved = row.copy(scopeKey = scopeKey)
        if (rows.any { it.id != id && sameKey(it, moved) }) return 0
        replace(id) { moved }
        return 1
    }

    /** `INSERT` with ABORT semantics, for [FakeDeviceIdentityDao]'s transactional insert. */
    internal fun insertStrict(identifier: DeviceIdentifierEntity): Long {
        check(rows.none { sameKey(it, identifier) }) { "UNIQUE constraint failed: device_identifiers" }
        val withId = identifier.copy(id = nextId++)
        rows += withId
        return withId.id
    }

    internal fun countOf(kind: String, value: String, scopeKey: String): Int =
        rows.count { it.kind == kind && it.value == value && it.scopeKey == scopeKey }

    internal fun moveAll(from: Long, to: Long) {
        rows.replaceAll { if (it.deviceId == from) it.copy(deviceId = to) else it }
    }

    internal fun removeForDevice(deviceId: Long) {
        rows.removeAll { it.deviceId == deviceId }
    }

    private fun sameKey(a: DeviceIdentifierEntity, b: DeviceIdentifierEntity) =
        a.kind == b.kind && a.value == b.value && a.scopeKey == b.scopeKey

    private inline fun replace(id: Long, transform: (DeviceIdentifierEntity) -> DeviceIdentifierEntity) {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) rows[i] = transform(rows[i])
    }

    companion object {
        /** What the real `@Insert(onConflict = IGNORE)` returns on a unique-index collision. */
        const val DUPLICATE = -1L
    }
}
