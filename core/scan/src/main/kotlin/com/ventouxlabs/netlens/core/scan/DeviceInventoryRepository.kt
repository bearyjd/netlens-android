package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.TransactionRunner
import com.ventouxlabs.netlens.core.data.dao.DeviceIdentityDao
import com.ventouxlabs.netlens.core.data.dao.DeviceMergeDecisionDao
import com.ventouxlabs.netlens.core.data.dao.DeviceObservationDao
import com.ventouxlabs.netlens.core.data.dao.DeviceSightingDao
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.scan.identity.DeviceIdentifierRecorder
import com.ventouxlabs.netlens.core.scan.identity.DeviceIdentityResolver
import com.ventouxlabs.netlens.core.scan.identity.IdentityPolicy
import com.ventouxlabs.netlens.core.scan.identity.MergeSuggestion
import com.ventouxlabs.netlens.core.scan.identity.ResolveOptions
import com.ventouxlabs.netlens.core.scan.identity.ResolvedDevice
import com.ventouxlabs.netlens.core.scan.identity.Resolution
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.network.NetworkContext
import com.ventouxlabs.netlens.core.scan.network.NetworkKeyCanonicalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceInventoryRepository {
    /**
     * Persists a scan's devices into the known-device inventory, tagging each row with
     * [networkId] (null for ad-hoc foreground scans). Fires [NewDeviceNotifier] once per genuinely
     * new row (a new device, or a newly inserted identifier-conflict row) and never for a re-seen
     * device or a merge. Shared by foreground LanScan and the background watch.
     *
     * [network] says which network the scan ran on: sightings and NETWORK-scoped identifiers are
     * stored under its canonical key and its label. With no keys every scan shares one fallback key.
     */
    suspend fun persistScan(devices: List<LanDevice>, networkId: Long?, network: NetworkContext = NetworkContext.NONE)

    /** Possible duplicates the resolver or the user's scans found, strongest first; read from `SUGGESTED` decisions. */
    val pendingMergeSuggestions: Flow<List<MergeSuggestion.BetweenRows>>

    /**
     * Merges the suggested pair as a user-confirmed merge: the older row survives, its user-authored
     * fields win, the other row's fill only empty ones. [isKnown] is the user's trust choice and
     * defaults to the survivor's. False (nothing changed) when the pair is no longer a pending
     * suggestion, a row is gone, the merge would delete a row the user marked DISTINCT, or the rows'
     * `isKnown` differ and no [isKnown] choice was passed (trust is never decided for the user).
     */
    suspend fun acceptSuggestion(pair: MergeSuggestion.BetweenRows, isKnown: Boolean? = null): Boolean

    /**
     * Records the pair (low id first) as DISTINCT: never suggested or auto-merged again. False, writing
     * nothing, unless the pair is currently a pending suggestion between two existing rows.
     */
    suspend fun rejectSuggestion(pair: MergeSuggestion.BetweenRows): Boolean
}

@Singleton
class DeviceInventoryRepositoryImpl @Inject constructor(
    private val knownDeviceDao: KnownDeviceDao,
    private val newDeviceNotifier: NewDeviceNotifier,
    private val resolver: DeviceIdentityResolver,
    private val recorder: DeviceIdentifierRecorder,
    private val identityDao: DeviceIdentityDao,
    private val observationDao: DeviceObservationDao,
    private val sightingDao: DeviceSightingDao,
    private val decisionDao: DeviceMergeDecisionDao,
    private val transactions: TransactionRunner,
    private val autoMergeSetting: AutoMergeSetting,
    private val networkKeys: NetworkKeyCanonicalizer,
) : DeviceInventoryRepository {

    // Foreground scan, background watch and the user's merge decisions share one process and one
    // inventory; every write below runs under this lock.
    private val writeLock = Mutex()

    override val pendingMergeSuggestions: Flow<List<MergeSuggestion.BetweenRows>> =
        decisionDao.observeSuggestions().map { rows ->
            rows.map {
                MergeSuggestion.BetweenRows(it.deviceIdLow, it.deviceIdHigh, it.score, it.reasons.orEmpty().split(',').filter(String::isNotEmpty))
            }
        }

    override suspend fun persistScan(devices: List<LanDevice>, networkId: Long?, network: NetworkContext) = writeLock.withLock {
        if (devices.isEmpty()) return@withLock
        val now = System.currentTimeMillis()
        val canonical = networkKeys.canonicalize(network.keys, now)
        val run = ScanRun(networkId, now, canonical, network.label, ResolveOptions(autoMergeSetting.isEnabled()))
        val first = resolver.resolve(devices, canonical, run.now, run.options)
        first.newConflictRows.forEach(newDeviceNotifier::notify)
        for (resolved in first.devices) {
            persist(resolved, first.suggestions.forNewDevice(resolved), run)
        }
    }

    private class ScanRun(val networkId: Long?, val now: Long, val canonicalKey: String, val networkLabel: String?, val options: ResolveOptions)

    /**
     * Applies one resolved device. A lost insert race (another writer owns one of its identifiers)
     * re-resolves it, at most [MAX_ATTEMPTS] times in all, and then gives up on the device for this
     * scan: it is never inserted by another route, so a failure can cost an alert for one scan but
     * never a duplicate row.
     */
    private suspend fun persist(initial: ResolvedDevice, initialSuggestions: List<MergeSuggestion.ForNewDevice>, run: ScanRun) {
        var resolved = initial
        var suggestions = initialSuggestions
        for (attempt in 1..MAX_ATTEMPTS) {
            if (apply(resolved, suggestions, run)) return
            if (attempt < MAX_ATTEMPTS) {
                val again = reResolve(resolved, run)
                resolved = again.first
                suggestions = again.second
            }
        }
        // Neither text nor ids of the device go in the log: they are hostile input.
        logger.warning("Skipped a scanned device after $MAX_ATTEMPTS lost insert races")
    }

    private suspend fun apply(resolved: ResolvedDevice, suggestions: List<MergeSuggestion.ForNewDevice>, run: ScanRun): Boolean =
        when (val resolution = resolved.resolution) {
            Resolution.New -> insertNew(resolved, suggestions, run)
            is Resolution.Existing -> update(resolution.deviceId, resolved, run)
            is Resolution.Ambiguous -> update(resolution.attachedTo, resolved, run)
            is Resolution.Conflict -> update(resolution.deviceId, resolved, run)
        }

    /**
     * Resolves [resolved]'s device again alone. The claims the first pass refused stay refused (the
     * device is re-offered only what it owned), otherwise a claim contested within the scan could
     * now match the winner's row and silently absorb the device.
     */
    private suspend fun reResolve(resolved: ResolvedDevice, run: ScanRun): Pair<ResolvedDevice, List<MergeSuggestion.ForNewDevice>> {
        val device = resolved.device.copy(identifiers = resolved.ownedIdentifiers)
        val result = resolver.resolve(listOf(device), run.canonicalKey, run.now, run.options)
        result.newConflictRows.forEach(newDeviceNotifier::notify)
        val again = result.devices.single()
        return again.copy(untrustedClaims = again.untrustedClaims + resolved.untrustedClaims) to result.suggestions.forNewDevice(again)
    }

    private suspend fun update(deviceId: Long, resolved: ResolvedDevice, run: ScanRun): Boolean = transactions.run {
        val row = knownDeviceDao.getById(deviceId) ?: return@run false // deleted since resolve: resolve again
        val locked = resolved.ownershipLocked
        // A locked row keeps its display MAC too: it is derived from identifiers the row must not gain.
        val scanned = row.withScan(resolved.device, run.now, run.networkId).let { if (locked) it.copy(macAddress = row.macAddress) else it }
        knownDeviceDao.updateScanDetails(
            id = deviceId, macAddress = scanned.macAddress, hostname = scanned.hostname, ip = scanned.ip,
            vendor = scanned.vendor, lastSeen = scanned.lastSeen, deviceType = scanned.deviceType,
            osGuess = scanned.osGuess, fingerprintConfidence = scanned.fingerprintConfidence,
            typeConfidence = scanned.typeConfidence, osConfidence = scanned.osConfidence,
            fingerprintEvidence = scanned.fingerprintEvidence, model = scanned.model, networkId = scanned.networkId,
        )
        recorder.record(deviceId, resolved.ownedIdentifiers, run.canonicalKey, run.now, insertNew = !locked)
        resolved.observationEntities(run.now).forEach { observationDao.upsert(it.copy(deviceId = deviceId)) }
        observationDao.trimToStrongest(deviceId, IdentityPolicy.MAX_OBSERVATIONS)
        sightingDao.upsert(resolved.sighting(deviceId, run.canonicalKey, run.networkLabel, run.now))
        true
    }

    private suspend fun insertNew(resolved: ResolvedDevice, suggestions: List<MergeSuggestion.ForNewDevice>, run: ScanRun): Boolean {
        val row = resolved.newRow(run.now, run.networkId)
        // The resolver's IP-only fallback applies to a device offering nothing but MACs (always, with the kill switch off).
        val ipFallback = !run.options.autoMerge || resolved.ownedIdentifiers.all { IdentityPolicy.isMac(it.kind) }
        val id = transactions.run {
            // An earlier reading in this same scan may have just created the row this fallback would now find.
            if (ipFallback && knownDeviceDao.getByIpWithoutMac(row.ip) != null) return@run DeviceIdentityDao.DUPLICATE
            val inserted = identityDao.insertDeviceWithIdentifiers(
                row, resolved.identifierEntities(run.canonicalKey, run.now), resolved.observationEntities(run.now), dedupeByMac = false,
            )
            if (inserted != DeviceIdentityDao.DUPLICATE) {
                observationDao.trimToStrongest(inserted, IdentityPolicy.MAX_OBSERVATIONS)
                sightingDao.upsert(resolved.sighting(inserted, run.canonicalKey, run.networkLabel, run.now))
            }
            inserted
        }
        if (id == DeviceIdentityDao.DUPLICATE) return false
        newDeviceNotifier.notify(row.copy(id = id))
        suggestions.forEach { resolver.persistSuggestion(id, it, run.now) }
        return true
    }

    private fun List<MergeSuggestion>.forNewDevice(resolved: ResolvedDevice) =
        filterIsInstance<MergeSuggestion.ForNewDevice>().filter { it.device == resolved.device }

    override suspend fun acceptSuggestion(pair: MergeSuggestion.BetweenRows, isKnown: Boolean?): Boolean = writeLock.withLock {
        transactions.run {
            if (decisionDao.get(pair.deviceIdLow, pair.deviceIdHigh)?.decision != SUGGESTED) return@run false
            val low = knownDeviceDao.getById(pair.deviceIdLow) ?: return@run false
            val high = knownDeviceDao.getById(pair.deviceIdHigh) ?: return@run false
            val (survivor, loser) = if (compareValuesBy(low, high, { it.firstSeen }, { it.id }) <= 0) low to high else high to low
            // Deleting the loser would cascade away its DISTINCT edges to third rows: refuse instead.
            if (decisionDao.touchesDistinct(listOf(loser.id))) return@run false
            // Differing trust is never decided for the user.
            if (isKnown == null && survivor.isKnown != loser.isKnown) return@run false
            val merged = identityDao.mergeDevices(survivor.id, loser.id, isKnown ?: survivor.isKnown)
            if (merged) observationDao.trimToStrongest(survivor.id, IdentityPolicy.MAX_OBSERVATIONS)
            merged
        }
    }

    override suspend fun rejectSuggestion(pair: MergeSuggestion.BetweenRows): Boolean = writeLock.withLock {
        if (pair.deviceIdLow >= pair.deviceIdHigh) return@withLock false
        transactions.run {
            if (decisionDao.get(pair.deviceIdLow, pair.deviceIdHigh)?.decision != SUGGESTED) return@run false
            if (knownDeviceDao.getById(pair.deviceIdLow) == null || knownDeviceDao.getById(pair.deviceIdHigh) == null) return@run false
            decisionDao.setDistinct(pair.deviceIdLow, pair.deviceIdHigh, System.currentTimeMillis())
            true
        }
    }

    private companion object {
        val logger: Logger = Logger.getLogger("DeviceInventory")
        const val MAX_ATTEMPTS = 3
        const val SUGGESTED = "SUGGESTED"
    }
}
