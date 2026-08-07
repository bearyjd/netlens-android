package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/**
 * In-memory [KnownDeviceDao] that reproduces the real DAO's observable behaviour.
 *
 * Replaces four separate doubles that had drifted apart — two near-identical `InMemory*` copies
 * (`core:scan`, `feature:lanscan`) and two weaker `Fake*` ones (`feature:devices`, and an inert
 * one in `feature:lanscan` whose every write was a no-op). The inert copy is why this matters:
 * a double looser than production turns a red test green, so any test written against it would
 * have passed regardless of the real `@Query`.
 *
 * Two ways in, deliberately:
 *  - [seed] inserts **verbatim**, preserving the caller's id. Test setup, for when a test needs
 *    to assert on a known id.
 *  - [insertIfNew] follows **production semantics**: it dedupes on MAC, assigns the next id and
 *    returns `-1L` when the row already exists.
 *
 * Do not "simplify" [insertIfNew] into [seed]. `DeviceInventoryRepository` relies on the `-1L`
 * duplicate signal, and a fake that always inserts would hide a re-scan creating duplicate rows.
 */
class FakeKnownDeviceDao : KnownDeviceDao {

    private val devices = mutableListOf<KnownDeviceEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<KnownDeviceEntity>>(emptyList())

    /** Rows currently stored, in insertion order. */
    val allDevices: List<KnownDeviceEntity> get() = devices.toList()

    /** Inserts verbatim, preserving each entity's own id. Bypasses [insertIfNew]'s dedup. */
    fun seed(vararg entities: KnownDeviceEntity) {
        devices += entities
        publish()
    }

    fun byId(id: Long): KnownDeviceEntity? = devices.find { it.id == id }

    override fun getAllDevices(): Flow<List<KnownDeviceEntity>> = flow

    override suspend fun getByMac(mac: String): KnownDeviceEntity? =
        devices.find { it.macAddress == mac }

    override suspend fun getByIpWithoutMac(ip: String): KnownDeviceEntity? =
        devices.find { it.ip == ip && it.macAddress == null }

    override fun getUnknownDevices(): Flow<List<KnownDeviceEntity>> =
        flowOf(devices.filter { !it.isKnown })

    override suspend fun insertIfNew(device: KnownDeviceEntity): Long {
        if (device.macAddress != null && devices.any { it.macAddress == device.macAddress }) {
            return DUPLICATE_ROW
        }
        val withId = device.copy(id = nextId++)
        devices += withId
        publish()
        return withId.id
    }

    override suspend fun updateLastSeen(
        id: Long,
        hostname: String?,
        ip: String,
        vendor: String?,
        lastSeen: Long,
        deviceType: String?,
        osGuess: String?,
    ) = mutate(id) {
        it.copy(
            hostname = hostname,
            ip = ip,
            vendor = vendor,
            lastSeen = lastSeen,
            deviceType = deviceType,
            osGuess = osGuess,
        )
    }

    override suspend fun setMacAddress(id: Long, mac: String) = mutate(id) { it.copy(macAddress = mac) }

    override suspend fun setKnown(id: Long, isKnown: Boolean) = mutate(id) { it.copy(isKnown = isKnown) }

    override suspend fun setCustomName(id: Long, customName: String?) =
        mutate(id) { it.copy(customName = customName) }

    override suspend fun getById(id: Long): KnownDeviceEntity? = byId(id)

    override suspend fun updateUserDetails(
        id: Long,
        customName: String?,
        tags: String?,
        notes: String?,
        location: String?,
    ) = mutate(id) {
        it.copy(customName = customName, tags = tags, notes = notes, location = location)
    }

    override suspend fun setNetworkId(id: Long, networkId: Long?) =
        mutate(id) { it.copy(networkId = networkId) }

    override fun search(query: String): Flow<List<KnownDeviceEntity>> =
        flowOf(devices.filter { it.hostname?.contains(query) == true || it.ip.contains(query) })

    override suspend fun delete(id: Long) {
        devices.removeAll { it.id == id }
        publish()
    }

    override suspend fun deleteAll() {
        devices.clear()
        publish()
    }

    private inline fun mutate(id: Long, transform: (KnownDeviceEntity) -> KnownDeviceEntity) {
        val i = devices.indexOfFirst { it.id == id }
        if (i < 0) return
        devices[i] = transform(devices[i])
        publish()
    }

    private fun publish() = flow.update { devices.toList() }

    companion object {
        /** What the real `@Insert(onConflict = IGNORE)` returns when the row already exists. */
        const val DUPLICATE_ROW = -1L
    }
}
