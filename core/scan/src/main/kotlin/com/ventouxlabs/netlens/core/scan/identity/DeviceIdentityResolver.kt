package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.data.dao.DeviceIdentifierDao
import com.ventouxlabs.netlens.core.data.dao.DeviceIdentityDao
import com.ventouxlabs.netlens.core.data.dao.DeviceMergeDecisionDao
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.NetworkKeyAliasDao
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind
import com.ventouxlabs.netlens.core.scan.model.identity.Scope
import com.ventouxlabs.netlens.core.scan.model.identity.Strength
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** A row reached by [hits] (the scanned device's identifiers that point at it). */
    private class Candidate(val row: KnownDeviceEntity, val hits: List<DeviceIdentifier>) {
        val viaProof get() = hits.any { it.proven || it.kind == IdentifierKind.MAC_UNIVERSAL }

        /** Reached only by claims anybody on the LAN could have announced. */
        val claimOnly get() = hits.all { IdentityPolicy.isClaimable(it) && !it.proven }
    }

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
    ): ResolveResult = resolveLock.withLock {
        // Process-wide: foreground scan and background watch share one inventory, and a resolve that
        // merges rows must not interleave with another that has already read them.
        val canonical = canonicalKey(networkKey)
        val prepared = devices.map { Sighting(IdentityPolicy.prepare(it)) }
        val outcomes = if (options.autoMerge) resolveAll(prepared, canonical, now) else prepared.map { legacy(it, canonical) }
        val redirect = mergeRedirects(outcomes)
        ResolveResult(
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
        val carriersByKey = sightings
            .flatMap { s -> s.identifiers.filter(IdentityPolicy::isClaimable).map { it.claimKey() to s } }
            .groupBy({ it.first }, { it.second })
        val lost = lostClaims(sightings, carriersByKey)
        val contested = carriersByKey.values.filter { it.size > 1 }.flatten().toSet()
        val pool = WeakPool { knownDeviceDao.getSeenSince(now - WEAK_WINDOW_MS) }
        return sightings.map { s -> resolveOne(s, lost[s].orEmpty(), s in contested, canonical, now, pool) }
    }

    /**
     * Which claimable identifiers each sighting must give up. A claim is kept only by the single
     * carrier that is MAC-confirmed as the owning row. Everyone else loses it when the claim is
     * contested (two carriers) or when the owner is confirmed by a MAC elsewhere in this scan
     * (an owner whose probe missed X must not let a newcomer inherit it). A lone, unconfirmed carrier
     * keeps it: the owner may simply be offline (the plan's accepted residual; see [unverifiedCandidates]).
     */
    private suspend fun lostClaims(
        sightings: List<Sighting>,
        carriersByKey: Map<ClaimKey, List<Sighting>>,
    ): Map<Sighting, Set<DeviceIdentifier>> {
        val confirmedByOwner = mutableMapOf<Long, List<Sighting>>()
        val losses = mutableMapOf<Sighting, Set<DeviceIdentifier>>()
        for ((key, carriers) in carriersByKey) {
            val owner = identifierDao.find(key.first.name, key.second, GLOBAL_SCOPE)?.deviceId
            val confirmed = owner?.let { confirmedByOwner.getOrPut(it) { macConfirmed(it, sightings) } }.orEmpty()
            if (carriers.size == 1 && confirmed.isEmpty()) continue
            val keeper = ScanGrouping.keeper(carriers, confirmed)
            for (c in carriers.filter { it !== keeper }) {
                val claim = c.identifiers.first { it.claimKey() == key }
                losses[c] = losses[c].orEmpty() + claim
            }
        }
        return losses
    }

    /** Sightings reporting a MAC the row [ownerId] owns. */
    private suspend fun macConfirmed(ownerId: Long, sightings: List<Sighting>): List<Sighting> {
        val owned = identifierDao.getForDevice(ownerId).map { it.kind to it.value }.toSet()
        return sightings.filter { s -> s.identifiers.any { IdentityPolicy.isMac(it.kind) && (it.kind.name to it.value) in owned } }
    }

    private suspend fun resolveOne(
        sighting: Sighting,
        lost0: Set<DeviceIdentifier>,
        contested: Boolean,
        canonical: String,
        now: Long,
        pool: WeakPool,
    ): Outcome {
        val device = sighting.device
        val found = lookup(sighting.identifiers - lost0, canonical)
        val dropped = unverifiedCandidates(found, sighting.identifiers - lost0)
        val candidates = found - dropped.toSet()
        val lost = lost0 + dropped.flatMap { it.hits }
        val owned = sighting.identifiers - lost
        fun outcome(resolution: Resolution, suggestions: List<MergeSuggestion> = emptyList(), row: KnownDeviceEntity? = null) =
            Outcome(ResolvedDevice(device, resolution, owned, lost, contested || lost.isNotEmpty()), suggestions, row)

        if (candidates.size > 1) {
            val (resolution, suggestions) = mergeOrAmbiguous(candidates, now)
            return outcome(resolution, suggestions)
        }
        candidates.singleOrNull()?.let { return outcome(Resolution.Existing(it.row.id)) }
        legacyIpRow(device, owned)?.let { return outcome(Resolution.Existing(it.id)) }
        val claimed = dropped.firstOrNull()?.row?.id
            ?: lost.firstNotNullOfOrNull { identifierDao.find(it.kind.name, it.value, GLOBAL_SCOPE)?.deviceId }
        val conflict = dropped.isNotEmpty() || (claimed != null && owned.none { it.kind == IdentifierKind.MAC_UNIVERSAL })
        if (conflict && claimed != null) {
            insertConflict(device, owned, lost, claimed, canonical, now)?.let { (resolution, row) -> return outcome(resolution, row = row) }
            return outcome(Resolution.New)
        }
        return outcome(Resolution.New, weakSuggestions(device, pool))
    }

    /**
     * Candidates that must not be trusted as the scanned device: rows reached only by unproven
     * claims whose universal MAC contradicts the sighting's own (a copied UDN), and — when no
     * candidate is backed by proof at all — every row an unproven claimant points at.
     */
    private suspend fun unverifiedCandidates(found: List<Candidate>, owned: Set<DeviceIdentifier>): List<Candidate> {
        val macs = owned.filter { it.kind == IdentifierKind.MAC_UNIVERSAL }.map { it.value }.toSet()
        val contradicted = if (macs.isEmpty()) emptyList() else found.filter { c ->
            c.claimOnly && identifierDao.getForDevice(c.row.id)
                .filter { it.kind == IdentifierKind.MAC_UNIVERSAL.name }.map { it.value }
                .let { rowMacs -> rowMacs.isNotEmpty() && rowMacs.none { it in macs } }
        }
        val rest = found - contradicted.toSet()
        return if (rest.size > 1 && rest.all { it.claimOnly }) contradicted + rest else contradicted
    }

    // ---- candidate lookup (step 2) ---------------------------------------------------------

    private suspend fun lookup(identifiers: Set<DeviceIdentifier>, canonical: String): List<Candidate> {
        val hitsByRow = linkedMapOf<Long, List<DeviceIdentifier>>()
        for (id in identifiers) {
            val hit = findOwner(id, canonical) ?: continue
            hitsByRow[hit.deviceId] = hitsByRow[hit.deviceId].orEmpty() + id
        }
        return hitsByRow.mapNotNull { (rowId, hits) -> knownDeviceDao.getById(rowId)?.let { Candidate(it, hits) } }
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
        val views = candidates.map { MergeCandidate(it.row, it.viaProof, identifierDao.getForDevice(it.row.id)) }
        val survivor = candidates.map { it.row }.minWith(compareBy({ it.firstSeen }, { it.id }))
        val loserIds = ids.filter { it != survivor.id }
        val block = IdentityPolicy.mergeBlocker(views) { a, b -> (a to b) in distinct }
            ?: MergeBlock.DISTINCT_DECISION.takeIf { decisionDao.touchesDistinct(loserIds) }
        if (block == null) {
            val merged = merge(survivor.id, loserIds)
            if (merged.mergedFrom.size == loserIds.size) return merged to emptyList()
            return ambiguous(ids - merged.mergedFrom.toSet(), distinct, MergeBlock.CHANGED_SINCE_READ, candidates, now)
        }
        return ambiguous(ids, distinct, block, candidates, now)
    }

    private suspend fun ambiguous(
        ids: List<Long>,
        distinct: Set<Pair<Long, Long>>,
        block: MergeBlock,
        candidates: List<Candidate>,
        now: Long,
    ): Pair<Resolution, List<MergeSuggestion>> {
        val attachedTo = candidates.filter { it.row.id in ids }.maxWith(compareBy({ it.row.lastSeen }, { it.row.id })).row.id
        val suggestions = ids.filter { it != attachedTo }.mapNotNull { other ->
            suggestBetween(minOf(attachedTo, other), maxOf(attachedTo, other), block, distinct, now)
        }
        return Resolution.Ambiguous(ids, attachedTo, block) to suggestions
    }

    private suspend fun distinctPairs(ids: List<Long>): Set<Pair<Long, Long>> =
        ids.flatMap { a -> ids.filter { it > a }.map { b -> a to b } }
            .filter { (a, b) -> decisionDao.get(a, b)?.decision == DISTINCT }
            .toSet()

    /** Folds [loserIds] into [survivorId]; the DAO re-checks the rules, so a refusal just means "not merged". */
    private suspend fun merge(survivorId: Long, loserIds: List<Long>): Resolution.Existing {
        val merged = loserIds.sorted().filter { identityDao.mergeDevices(survivorId, it, isKnown = null) }
        return Resolution.Existing(survivorId, merged)
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
        private val resolveLock = Mutex()
        private const val UNKNOWN_NETWORK = "unknown-network"
        private const val WEAK_WINDOW_MS = 180L * 24 * 60 * 60 * 1000
        private const val SUGGESTED = "SUGGESTED"
        private const val DISTINCT = "DISTINCT"
        private const val INSERT_IGNORED = -1L
        private const val SHARED_IDENTIFIER = "shared_identifier"
        private const val SHARED_IDENTIFIER_SCORE = 100
    }
}
