package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.data.dao.DeviceIdentifierDao
import com.ventouxlabs.netlens.core.data.dao.DeviceIdentityDao
import com.ventouxlabs.netlens.core.data.dao.DeviceMergeDecisionDao
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.NetworkKeyAliasDao
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind
import com.ventouxlabs.netlens.core.scan.model.identity.ObservedField
import com.ventouxlabs.netlens.core.scan.model.identity.Scope
import com.ventouxlabs.netlens.core.scan.model.identity.Strength
import javax.inject.Inject

/**
 * Decides which known-devices row each scanned device is. It writes only what identity itself needs:
 * merges (one transaction in [DeviceIdentityDao]), legacy `MAC_LOCAL` rescoping, `SUGGESTED` decisions
 * between existing rows, and Conflict rows. Scan-derived columns, observations and identifiers of
 * Existing/New devices are the repository's job (Task 1.5), using [ResolvedDevice].
 *
 * Invariant: a claimed identifier never silences a new-device alert. Claims are unique, not
 * authenticated, so an unproven claim of a known identifier from a second simultaneous address is
 * resolved without that identifier (see [ScanGrouping]).
 */
class DeviceIdentityResolver @Inject constructor(
    private val knownDeviceDao: KnownDeviceDao,
    private val identifierDao: DeviceIdentifierDao,
    private val decisionDao: DeviceMergeDecisionDao,
    private val aliasDao: NetworkKeyAliasDao,
    private val identityDao: DeviceIdentityDao,
) {
    private class Candidate(val row: KnownDeviceEntity, val viaStrong: Boolean)

    private class Outcome(
        val resolved: ResolvedDevice,
        val suggestions: List<MergeSuggestion> = emptyList(),
        val conflictRow: KnownDeviceEntity? = null,
    )

    private class WeakPool(private val load: suspend () -> List<KnownDeviceEntity>) {
        private var rows: List<KnownDeviceEntity>? = null
        suspend fun get(): List<KnownDeviceEntity> = rows ?: load().also { rows = it }
    }

    suspend fun resolve(
        devices: List<LanDevice>,
        networkKey: String,
        now: Long,
        options: ResolveOptions = ResolveOptions(),
    ): ResolveResult {
        val canonical = canonicalKey(networkKey)
        val prepared = devices.map { Sighting(IdentityPolicy.prepare(it)) }
        val outcomes = if (options.autoMerge) resolveAll(prepared, canonical, now) else prepared.map { legacy(it, canonical) }
        val redirect = mergeRedirects(outcomes)
        return ResolveResult(
            devices = outcomes.map { it.resolved.copy(resolution = it.resolved.resolution.redirected(redirect)) },
            suggestions = outcomes.flatMap { it.suggestions }.mapNotNull { it.redirected(redirect) }.distinct(),
            newConflictRows = outcomes.mapNotNull { it.conflictRow },
        )
    }

    /**
     * Follows `network_key_aliases` to the canonical key: at most [MAX_ALIAS_HOPS] hops, and a cycle
     * resolves to its lexicographically smallest member so every entry point agrees.
     */
    suspend fun canonicalKey(key: String): String {
        val path = mutableListOf(key.ifBlank { UNKNOWN_NETWORK })
        repeat(MAX_ALIAS_HOPS) {
            val next = aliasDao.canonicalFor(path.last()) ?: return path.last()
            val seenAt = path.indexOf(next)
            if (seenAt >= 0) return path.subList(seenAt, path.size).min()
            path += next
        }
        return path.last()
    }

    /**
     * Persists a [MergeSuggestion.ForNewDevice] once the new row exists. Never touches a user decision,
     * and does nothing if either row has since gone (the decision table's foreign keys would throw).
     */
    suspend fun persistSuggestion(newDeviceId: Long, suggestion: MergeSuggestion.ForNewDevice, now: Long) {
        if (knownDeviceDao.getById(newDeviceId) == null || knownDeviceDao.getById(suggestion.existingDeviceId) == null) return
        val low = minOf(newDeviceId, suggestion.existingDeviceId)
        val high = maxOf(newDeviceId, suggestion.existingDeviceId)
        val reasons = suggestion.reasons.joinToString(",")
        val row = DeviceMergeDecisionEntity(low, high, SUGGESTED, suggestion.score, reasons, now)
        if (decisionDao.suggest(row) == INSERT_IGNORED) decisionDao.updateSuggestion(low, high, suggestion.score, reasons)
    }

    /** Sightings resolved earlier in the call may point at a row a later sighting merged away. */
    private fun mergeRedirects(outcomes: List<Outcome>): Map<Long, Long> =
        outcomes.map { it.resolved.resolution }.filterIsInstance<Resolution.Existing>()
            .flatMap { e -> e.mergedFrom.map { it to e.deviceId } }.toMap()

    // ---- scan-level flow -------------------------------------------------------------------

    private suspend fun resolveAll(prepared: List<Sighting>, canonical: String, now: Long): List<Outcome> {
        val sightings = ScanGrouping.groupAuthenticated(prepared)
        val lost = lostClaims(sightings)
        val sharing = ScanGrouping.contested(sightings).values.flatten().toSet()
        val pool = WeakPool { knownDeviceDao.getSeenSince(now - WEAK_WINDOW_MS) }
        return sightings.map { s ->
            resolveOne(s, lost[s].orEmpty(), s in sharing, canonical, now, pool)
        }
    }

    /** For every contested identifier, all carriers except the incumbent lose it. */
    private suspend fun lostClaims(sightings: List<Sighting>): Map<Sighting, Set<DeviceIdentifier>> {
        val losses = mutableMapOf<Sighting, Set<DeviceIdentifier>>()
        for ((key, carriers) in ScanGrouping.contested(sightings)) {
            val owner = identifierDao.find(key.first.name, key.second, GLOBAL_SCOPE)?.deviceId
            val keep = ScanGrouping.incumbent(carriers, tieTo(owner))
            for (loser in carriers - keep) {
                val claim = loser.identifiers.first { it.claimKey() == key }
                losses[loser] = losses[loser].orEmpty() + claim
            }
        }
        return losses
    }

    /** How firmly a sighting belongs to [ownerId]: 2 = shares one of its MACs, 1 = its last address, 0 = neither. */
    private suspend fun tieTo(ownerId: Long?): (Sighting) -> Int {
        if (ownerId == null) return { 0 }
        val owned = identifierDao.getForDevice(ownerId).map { it.kind to it.value }.toSet()
        val ownerIp = knownDeviceDao.getById(ownerId)?.ip
        return { s ->
            when {
                s.identifiers.any { IdentityPolicy.isMac(it.kind) && (it.kind.name to it.value) in owned } -> 2
                s.device.ip == ownerIp -> 1
                else -> 0
            }
        }
    }

    private suspend fun resolveOne(
        sighting: Sighting,
        lost: Set<DeviceIdentifier>,
        shares: Boolean,
        canonical: String,
        now: Long,
        pool: WeakPool,
    ): Outcome {
        val owned = sighting.identifiers - lost
        val device = sighting.device
        val candidates = lookup(owned, canonical)
        fun outcome(resolution: Resolution, suggestions: List<MergeSuggestion> = emptyList(), row: KnownDeviceEntity? = null) =
            Outcome(ResolvedDevice(device, resolution, owned, lost, shares), suggestions, row)

        if (candidates.size > 1) {
            val (resolution, suggestions) = mergeOrAmbiguous(candidates, now)
            return outcome(resolution, suggestions)
        }
        candidates.singleOrNull()?.let { return outcome(Resolution.Existing(it.row.id)) }
        legacyIpRow(device, owned)?.let { return outcome(Resolution.Existing(it.id)) }
        val claimed = lost.firstNotNullOfOrNull { identifierDao.find(it.kind.name, it.value, GLOBAL_SCOPE)?.deviceId }
        if (claimed != null && owned.none { it.kind == IdentifierKind.MAC_UNIVERSAL }) {
            return insertConflict(device, owned, lost, claimed, canonical, now)?.let { (resolution, row) -> outcome(resolution, row = row) }
                ?: outcome(Resolution.New)
        }
        return outcome(Resolution.New, weakSuggestions(device, pool))
    }

    // ---- candidate lookup (step 2) ---------------------------------------------------------

    private suspend fun lookup(identifiers: Set<DeviceIdentifier>, canonical: String): List<Candidate> {
        val strongByRow = linkedMapOf<Long, Boolean>()
        for (id in identifiers) {
            val hit = findOwner(id, canonical) ?: continue
            strongByRow[hit.deviceId] = strongByRow[hit.deviceId] == true || id.kind.strength == Strength.STRONG
        }
        return strongByRow.mapNotNull { (rowId, strong) -> knownDeviceDao.getById(rowId)?.let { Candidate(it, strong) } }
    }

    private suspend fun findOwner(id: DeviceIdentifier, canonical: String): DeviceIdentifierEntity? {
        val scope = if (id.kind.scope == Scope.GLOBAL) GLOBAL_SCOPE else canonical
        identifierDao.find(id.kind.name, id.value, scope)?.let { return it }
        if (id.kind != IdentifierKind.MAC_LOCAL) return null
        // Rows backfilled by MIGRATION_18_19 have no network key: match on any network, then pin to this one.
        return identifierDao.find(id.kind.name, id.value, GLOBAL_SCOPE)?.also { identifierDao.rescope(it.id, canonical) }
    }

    /** Today's fallback, kept for lite sweeps: only when the device offers nothing but MACs (or nothing). */
    private suspend fun legacyIpRow(device: LanDevice, identifiers: Set<DeviceIdentifier>): KnownDeviceEntity? =
        if (identifiers.all { IdentityPolicy.isMac(it.kind) }) knownDeviceDao.getByIpWithoutMac(device.ip) else null

    /** Kill switch off: exactly the MAC-or-IP lookup of the pre-identity repository. */
    private suspend fun legacy(sighting: Sighting, canonical: String): Outcome {
        val macs = sighting.identifiers.filterTo(linkedSetOf()) { IdentityPolicy.isMac(it.kind) }
        val row = lookup(macs, canonical).firstOrNull()?.row ?: knownDeviceDao.getByIpWithoutMac(sighting.device.ip)
        if (row != null) {
            return Outcome(ResolvedDevice(sighting.device, Resolution.Existing(row.id), sighting.identifiers, emptySet(), false))
        }
        // New: an identifier somebody already owns must not be offered as ownership (the insert would
        // be refused forever and the device would never alert), so it is only evidence.
        val (taken, free) = sighting.identifiers.partition { identifierDao.find(it.kind.name, it.value, scopeOf(it, canonical)) != null }
        return Outcome(ResolvedDevice(sighting.device, Resolution.New, free.toSet(), taken.toSet(), false))
    }

    private fun scopeOf(id: DeviceIdentifier, canonical: String): String =
        if (id.kind.scope == Scope.GLOBAL) GLOBAL_SCOPE else canonical

    // ---- several candidates (step 4) --------------------------------------------------------

    private suspend fun mergeOrAmbiguous(candidates: List<Candidate>, now: Long): Pair<Resolution, List<MergeSuggestion>> {
        val ids = candidates.map { it.row.id }
        val distinct = distinctPairs(ids)
        val views = candidates.map { MergeCandidate(it.row, it.viaStrong, identifierDao.getForDevice(it.row.id)) }
        val block = IdentityPolicy.mergeBlocker(views) { a, b -> (a to b) in distinct }
        if (block == null) return merge(candidates) to emptyList()
        val attachedTo = candidates.maxWith(compareBy({ it.row.lastSeen }, { it.row.id })).row.id
        val suggestions = ids.filter { it != attachedTo }.mapNotNull { other ->
            suggestBetween(minOf(attachedTo, other), maxOf(attachedTo, other), block, distinct, now)
        }
        return Resolution.Ambiguous(ids, attachedTo, block) to suggestions
    }

    private suspend fun distinctPairs(ids: List<Long>): Set<Pair<Long, Long>> =
        ids.flatMap { a -> ids.filter { it > a }.map { b -> a to b } }
            .filter { (a, b) -> decisionDao.get(a, b)?.decision == DISTINCT }
            .toSet()

    private suspend fun merge(candidates: List<Candidate>): Resolution.Existing {
        val rows = candidates.map { it.row }
        val survivor = rows.minWith(compareBy({ it.firstSeen }, { it.id }))
        val merged = rows.filter { it.id != survivor.id }.sortedBy { it.id }
            .filter { identityDao.mergeDevices(survivor.id, it.id, isKnown = null) }
            .map { it.id }
        return Resolution.Existing(survivor.id, merged)
    }

    private suspend fun suggestBetween(
        low: Long,
        high: Long,
        block: MergeBlock,
        distinct: Set<Pair<Long, Long>>,
        now: Long,
    ): MergeSuggestion? {
        if ((low to high) in distinct) return null
        val reasons = listOf(SHARED_IDENTIFIER, block.code)
        decisionDao.suggest(DeviceMergeDecisionEntity(low, high, SUGGESTED, SHARED_IDENTIFIER_SCORE, reasons.joinToString(","), now))
        return MergeSuggestion.BetweenRows(low, high, SHARED_IDENTIFIER_SCORE, reasons)
    }

    // ---- Conflict rows ----------------------------------------------------------------------

    /**
     * Persists the claimant as its own row. It owns only identifiers nobody else holds (e.g. its MAC);
     * what it claimed is stored as weight-0 evidence. Null when another writer won a race, in which
     * case the caller falls back to New and the repository re-resolves.
     */
    private suspend fun insertConflict(
        device: LanDevice,
        owned: Set<DeviceIdentifier>,
        lost: Set<DeviceIdentifier>,
        claimedDeviceId: Long,
        canonical: String,
        now: Long,
    ): Pair<Resolution.Conflict, KnownDeviceEntity>? {
        val row = KnownDeviceEntity(
            macAddress = device.macAddress, hostname = device.hostname, ip = device.ip, vendor = device.vendor,
            firstSeen = now, lastSeen = now, isKnown = false, deviceType = device.deviceType, osGuess = device.osGuess,
            fingerprintConfidence = device.fingerprintConfidence, model = device.model,
        )
        val identifiers = owned.map {
            DeviceIdentifierEntity(
                deviceId = 0, kind = it.kind.name, value = it.value, scopeKey = scopeOf(it, canonical), source = it.source.name,
                proven = it.proven, firstSeen = now, lastSeen = now,
            )
        }
        val claims = lost.take(IdentityPolicy.MAX_IDENTIFIERS_PER_KIND).map { claimEvidence(it, now) }
        val id = identityDao.insertDeviceWithIdentifiers(row, identifiers, claims, dedupeByMac = false)
        if (id == DeviceIdentityDao.DUPLICATE) return null
        return Resolution.Conflict(id, claimedDeviceId, inserted = true) to row.copy(id = id)
    }

    /** An identifier claim as evidence only: weight 0, flagged in the detail text, never an identifier row. */
    private fun claimEvidence(claim: DeviceIdentifier, now: Long) = DeviceObservationEntity(
        deviceId = 0, source = claim.source.name, field = ObservedField.PRODUCT.name,
        value = "${claim.kind.name}:${claim.value}", weight = 0, detail = CLAIM_DETAIL, lastSeen = now,
    )

    // ---- weak scoring (step 6) ---------------------------------------------------------------

    private suspend fun weakSuggestions(device: LanDevice, pool: WeakPool): List<MergeSuggestion> =
        pool.get()
            .map { it to IdentityPolicy.weakScore(device, it) }
            .filter { (_, s) -> s.score >= IdentityPolicy.WEAK_MATCH_THRESHOLD }
            .sortedWith(compareBy({ -it.second.score }, { -it.first.lastSeen }))
            .take(IdentityPolicy.MAX_WEAK_SUGGESTIONS)
            .map { (row, s) -> MergeSuggestion.ForNewDevice(device, row.id, s.score, s.reasons) }

    companion object {
        const val MAX_ALIAS_HOPS = 8
        private const val UNKNOWN_NETWORK = "unknown-network"
        private const val WEAK_WINDOW_MS = 180L * 24 * 60 * 60 * 1000
        private const val SUGGESTED = "SUGGESTED"
        private const val DISTINCT = "DISTINCT"
        private const val INSERT_IGNORED = -1L
        private const val SHARED_IDENTIFIER = "shared_identifier"
        private const val SHARED_IDENTIFIER_SCORE = 100
        private const val CLAIM_DETAIL = "unverified identifier claim"
    }
}
