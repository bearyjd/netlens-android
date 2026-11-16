package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.MergeKind
import com.ventouxlabs.netlens.core.data.model.MergeLogEntity
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
 *  - [insertIfNew] follows **production semantics**: it assigns the next id and ignores the
 *    caller's. Since v19 dropped the unique MAC index it no longer dedupes — two inserts of the same
 *    MAC both succeed, exactly like `INSERT OR IGNORE` against a non-unique index. Duplicate
 *    protection is `DeviceIdentityDao.insertDeviceWithIdentifiers`' job.
 *
 * Also holds the `merge_log` rows ([mergeLog]) that [FakeDeviceIdentityDao] writes, because the
 * real [delete] / [deleteAll] clear them in the same transaction; those two are the interface's own
 * default methods, run over the building blocks below.
 */
class FakeKnownDeviceDao : KnownDeviceDao {

    private val devices = mutableListOf<KnownDeviceEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<KnownDeviceEntity>>(emptyList())
    private val mergeLogRows = mutableListOf<MergeLogEntity>()
    private var nextMergeLogId = 1L

    /** `merge_log` rows currently stored, oldest first. */
    val mergeLog: List<MergeLogEntity> get() = mergeLogRows.toList()

    /** Rows currently stored, in insertion order. */
    val allDevices: List<KnownDeviceEntity> get() = devices.toList()

    /** Inserts verbatim, preserving each entity's own id (later inserts continue after the highest). Bypasses [insertIfNew]'s dedup. */
    fun seed(vararg entities: KnownDeviceEntity) {
        devices += entities
        nextId = maxOf(nextId, (entities.maxOfOrNull { it.id } ?: 0L) + 1)
        publish()
    }

    fun byId(id: Long): KnownDeviceEntity? = devices.find { it.id == id }

    override fun getAllDevices(): Flow<List<KnownDeviceEntity>> = flow

    override suspend fun getByMac(mac: String): KnownDeviceEntity? =
        devices.find { it.macAddress == mac }

    override suspend fun getByIpWithoutMac(ip: String): KnownDeviceEntity? =
        devices.find { it.ip == ip && it.macAddress == null }

    override suspend fun getSeenSince(since: Long): List<KnownDeviceEntity> =
        devices.filter { it.lastSeen >= since }.sortedByDescending { it.lastSeen }.take(SEEN_SINCE_LIMIT)

    /** Same as [insertIfNew]; kept for [FakeDeviceIdentityDao]'s `insertDeviceRow`. */
    internal fun insertRaw(device: KnownDeviceEntity): Long {
        val withId = device.copy(id = nextId++)
        devices += withId
        publish()
        return withId.id
    }

    override fun getUnknownDevices(): Flow<List<KnownDeviceEntity>> =
        flowOf(devices.filter { !it.isKnown })

    override suspend fun insertIfNew(device: KnownDeviceEntity): Long {
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
        fingerprintConfidence: Int?,
        fingerprintEvidence: String?,
        model: String?,
    ) = mutate(id) {
        it.copy(
            hostname = hostname,
            ip = ip,
            vendor = vendor,
            lastSeen = lastSeen,
            deviceType = deviceType,
            osGuess = osGuess,
            fingerprintConfidence = fingerprintConfidence,
            fingerprintEvidence = fingerprintEvidence,
            model = model,
        )
    }

    override suspend fun updateScanDetails(
        id: Long,
        macAddress: String?,
        hostname: String?,
        ip: String,
        vendor: String?,
        lastSeen: Long,
        deviceType: String?,
        osGuess: String?,
        fingerprintConfidence: Int?,
        typeConfidence: Int?,
        osConfidence: Int?,
        fingerprintEvidence: String?,
        model: String?,
        networkId: Long?,
    ) = mutate(id) {
        it.copy(
            macAddress = macAddress, hostname = hostname, ip = ip, vendor = vendor, lastSeen = lastSeen,
            deviceType = deviceType, osGuess = osGuess, fingerprintConfidence = fingerprintConfidence,
            typeConfidence = typeConfidence, osConfidence = osConfidence,
            fingerprintEvidence = fingerprintEvidence, model = model, networkId = networkId,
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
        deviceTypeOverride: String?,
        osOverride: String?,
        modelOverride: String?,
    ) = mutate(id) {
        it.copy(
            customName = customName,
            tags = tags,
            notes = notes,
            location = location,
            deviceTypeOverride = deviceTypeOverride,
            osOverride = osOverride,
            modelOverride = modelOverride,
        )
    }

    override suspend fun setNetworkId(id: Long, networkId: Long?) =
        mutate(id) { it.copy(networkId = networkId) }

    override fun search(query: String): Flow<List<KnownDeviceEntity>> =
        flowOf(devices.filter { it.hostname?.contains(query) == true || it.ip.contains(query) })

    override suspend fun deleteRow(id: Long) {
        devices.removeAll { it.id == id }
        publish()
    }

    /** Same closure as the real recursive query: [id] plus every loser reachable from it. */
    override suspend fun deleteMergeLogChain(id: Long) {
        val chain = mutableSetOf(id)
        var frontier = setOf(id)
        while (frontier.isNotEmpty()) {
            frontier = mergeLogRows.filter { it.survivorId in frontier }.map { it.loserId }.toSet() - chain
            chain += frontier
        }
        mergeLogRows.removeAll { it.survivorId in chain }
    }

    override suspend fun deleteAllRows() {
        devices.clear()
        publish()
    }

    override suspend fun deleteAllMergeLog() = mergeLogRows.clear()

    /** For [FakeDeviceIdentityDao.insertMergeLogRow]: assigns the next AUTOINCREMENT id. */
    internal fun insertMergeLog(entry: MergeLogEntity): Long {
        val id = nextMergeLogId++
        mergeLogRows += entry.copy(id = id)
        return id
    }

    /** Same rule as the real `pruneMergeLog` query: keep the newest [cap], USER rows ranked first. */
    internal fun pruneMergeLog(cap: Int) {
        val keep = mergeLogRows
            .sortedWith(compareBy<MergeLogEntity> { if (it.kind == MergeKind.USER.name) 0 else 1 }.thenByDescending { it.id })
            .take(cap)
            .map { it.id }
            .toSet()
        mergeLogRows.removeAll { it.id !in keep }
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

        /** Mirrors the `LIMIT` on the real `getSeenSince` query. */
        private const val SEEN_SINCE_LIMIT = 500
    }
}
