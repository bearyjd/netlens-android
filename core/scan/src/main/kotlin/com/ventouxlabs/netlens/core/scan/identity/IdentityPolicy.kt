package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.effectiveDeviceType
import com.ventouxlabs.netlens.core.data.model.effectiveModel
import com.ventouxlabs.netlens.core.data.model.hasUserDetails
import com.ventouxlabs.netlens.core.network.DisplayText
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.MAX_MODEL_LENGTH
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind
import com.ventouxlabs.netlens.core.scan.model.identity.MacAddresses
import com.ventouxlabs.netlens.core.scan.model.identity.Observation
import com.ventouxlabs.netlens.core.scan.model.identity.ObservationSource
import com.ventouxlabs.netlens.core.scan.model.identity.Scope
import com.ventouxlabs.netlens.core.scan.model.identity.Strength

/** A candidate row plus what the merge rules need to know about it. */
data class MergeCandidate(
    val row: KnownDeviceEntity,
    val viaStrong: Boolean,
    val identifiers: List<DeviceIdentifierEntity>,
)

/** Pure decisions of the resolver: sanitising, merge eligibility and weak scoring. No I/O. */
object IdentityPolicy {
    const val MAX_IDENTIFIERS_PER_KIND = 32
    const val MAX_IDENTIFIER_LENGTH = 256
    const val MAX_DISPLAY_LENGTH = 256
    const val MAX_OBSERVATIONS = 64
    const val WEAK_MATCH_THRESHOLD = 60
    const val MAX_WEAK_SUGGESTIONS = 3

    private const val MODEL_POINTS = 40
    private const val HOSTNAME_POINTS = 30
    private const val VENDOR_POINTS = 10
    private const val DEVICE_TYPE_POINTS = 10

    // Services are not persisted anywhere (known_devices has no services column), so the plan's
    // "service-type Jaccard x 20" term has nothing to compare against. Kept at 0 until they are.
    const val SERVICE_JACCARD_POINTS = 0

    private val MAC_KINDS = setOf(IdentifierKind.MAC_UNIVERSAL, IdentifierKind.MAC_LOCAL)

    // One value per physical machine. UDN, TLS and SSH keys are deliberately absent: embedded UPnP
    // devices carry several UDNs and a host has several TLS/SSH keys, so differing values there
    // prove nothing. Blocking too much only turns an auto-merge into a user-confirmed suggestion.
    private val ONE_PER_MACHINE = setOf(
        IdentifierKind.SMB_SERVER_GUID,
        IdentifierKind.SNMP_ENGINE_ID,
        IdentifierKind.UPNP_SERIAL,
        IdentifierKind.IPP_PRINTER_UUID,
    )

    fun isMac(kind: IdentifierKind): Boolean = kind in MAC_KINDS

    /** Strong, global, non-MAC: the identifiers a hostile device can announce about another device. */
    fun isClaimable(id: DeviceIdentifier): Boolean =
        id.kind.strength == Strength.STRONG && id.kind.scope == Scope.GLOBAL &&
            !isMac(id.kind)

    /** Flattens a hostile string and caps it; null when nothing survives. */
    fun display(value: String?, max: Int = MAX_DISPLAY_LENGTH): String? = DisplayText.flatten(value)?.take(max)

    /**
     * Drops values that are blank, over [MAX_IDENTIFIER_LENGTH] or change under flattening (control
     * characters) — dropped, not truncated: truncation would make distinct long values equal. Collapses
     * duplicates on (kind, value) with `proven` only if every report agreed, and keeps at most
     * [MAX_IDENTIFIERS_PER_KIND] per kind, lowest value first so the choice is deterministic.
     */
    fun sanitizeIdentifiers(identifiers: Collection<DeviceIdentifier>): Set<DeviceIdentifier> =
        identifiers
            .filter { it.value.isNotBlank() && it.value.length <= MAX_IDENTIFIER_LENGTH && DisplayText.flatten(it.value) == it.value }
            .groupBy { it.kind to it.value }
            .map { (_, same) -> same.first().copy(proven = same.all { it.proven }) }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.value }))
            .groupBy { it.kind }
            .flatMap { (_, ofKind) -> ofKind.take(MAX_IDENTIFIERS_PER_KIND) }
            .toCollection(linkedSetOf())

    /** The MAC identifier derived from a scanner-supplied MAC string; null for junk or multicast. */
    fun macIdentifier(raw: String?): DeviceIdentifier? {
        val mac = MacAddresses.normalize(raw)?.takeUnless(MacAddresses::isMulticast) ?: return null
        return DeviceIdentifier(MacAddresses.kindOf(mac), mac, ObservationSource.ARP)
    }

    /** Flattened, capped copy of a scanned device with its identifiers sanitised and its MAC derived. */
    fun prepare(device: LanDevice): LanDevice {
        val ids = sanitizeIdentifiers(device.identifiers + listOfNotNull(macIdentifier(device.macAddress)))
        return device.copy(
            hostname = display(device.hostname),
            vendor = display(device.vendor),
            deviceType = display(device.deviceType),
            osGuess = display(device.osGuess),
            model = display(device.model, MAX_MODEL_LENGTH),
            services = device.services.mapNotNull { display(it) },
            fingerprintEvidence = device.fingerprintEvidence.mapNotNull { display(it) },
            observations = device.observations.mapNotNull(::cleanObservation).take(MAX_OBSERVATIONS),
            identifiers = ids,
        )
    }

    private fun cleanObservation(o: Observation): Observation? {
        val value = display(o.value) ?: return null
        return o.copy(value = value, detail = display(o.detail), weight = o.weight.coerceIn(0, 100))
    }

    /** Lowercase, `.local` and a trailing `-<digits>` (mDNS conflict suffix) removed. */
    fun normalizeHostname(raw: String?): String? {
        val flat = display(raw)?.lowercase() ?: return null
        val noDomain = flat.removeSuffix(".local")
        val stripped = noDomain.replace(Regex("-\\d+$"), "")
        return stripped.ifEmpty { noDomain }.ifEmpty { null }
    }

    /** Null when [candidates] may be merged automatically; otherwise the first rule that forbids it. */
    fun mergeBlocker(candidates: List<MergeCandidate>, isDistinct: (Long, Long) -> Boolean): MergeBlock? = when {
        !candidates.all { it.viaStrong } -> MergeBlock.WEAK_LINK
        hasContradictoryIdentifiers(candidates) -> MergeBlock.CONTRADICTORY_IDENTIFIER
        anyDistinctPair(candidates, isDistinct) -> MergeBlock.DISTINCT_DECISION
        candidates.count { it.row.hasUserDetails() } > 1 -> MergeBlock.USER_DETAILS
        candidates.map { it.row.isKnown }.distinct().size > 1 -> MergeBlock.TRUST_DIFFERS
        else -> null
    }

    private fun hasContradictoryIdentifiers(candidates: List<MergeCandidate>): Boolean =
        ONE_PER_MACHINE.any { kind ->
            candidates.count { c -> c.identifiers.any { it.kind == kind.name } } > 1
        }

    private fun anyDistinctPair(candidates: List<MergeCandidate>, isDistinct: (Long, Long) -> Boolean): Boolean {
        val ids = candidates.map { it.row.id }
        return ids.any { a -> ids.any { b -> a < b && isDistinct(a, b) } }
    }

    data class WeakScore(val score: Int, val reasons: List<String>)

    /** Plan step 6 weights; a score of at least [WEAK_MATCH_THRESHOLD] earns a suggestion. */
    fun weakScore(device: LanDevice, row: KnownDeviceEntity): WeakScore {
        val hits = buildList {
            if (device.model != null && device.model == row.effectiveModel()) add("model" to MODEL_POINTS)
            val host = normalizeHostname(device.hostname)
            if (host != null && host == normalizeHostname(row.hostname)) add("hostname" to HOSTNAME_POINTS)
            if (device.vendor != null && device.vendor.equals(row.vendor, ignoreCase = true)) add("vendor" to VENDOR_POINTS)
            if (device.deviceType != null && device.deviceType == row.effectiveDeviceType()) add("device_type" to DEVICE_TYPE_POINTS)
        }
        return WeakScore(hits.sumOf { it.second } + SERVICE_JACCARD_POINTS, hits.map { it.first })
    }
}
