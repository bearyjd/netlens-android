package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind

/** An identifier as the identity key it contends for. */
internal typealias ClaimKey = Pair<IdentifierKind, String>

internal fun DeviceIdentifier.claimKey(): ClaimKey = kind to value

/** One address seen in the scan: the sanitised device and its identifiers (MAC included). */
internal data class Sighting(val device: LanDevice) {
    val identifiers: Set<DeviceIdentifier> get() = device.identifiers
}

/** Within-scan policy (plan step 1), pure. Only authenticated sightings may be treated as one device. */
internal object ScanGrouping {

    /**
     * Joins sightings that both PROVE the same claimable identifier (completed handshake / verified
     * signature) into one multi-homed device. Sharing an unproven identifier never groups: a hostile
     * device can announce any UDN or key hash.
     */
    fun groupAuthenticated(sightings: List<Sighting>): List<Sighting> {
        val parent = IntArray(sightings.size) { it }
        fun find(i: Int): Int = if (parent[i] == i) i else find(parent[i]).also { parent[i] = it }
        val firstProver = HashMap<ClaimKey, Int>()
        sightings.forEachIndexed { i, s ->
            for (id in s.identifiers.filter { it.proven && IdentityPolicy.isClaimable(it) }) {
                val j = firstProver.getOrPut(id.claimKey()) { i }
                if (j != i) parent[find(i)] = find(j)
            }
        }
        return sightings.indices.groupBy(::find).values.map { members -> combine(members.map { sightings[it] }) }
    }

    private fun combine(members: List<Sighting>): Sighting {
        if (members.size == 1) return members.single()
        val primary = members.sortedWith(compareBy({ -it.identifiers.size }, { it.device.ip })).first()
        val others = members - primary
        // The union of several sanitised sets can exceed the per-kind cap again: re-apply it.
        val identifiers = IdentityPolicy.sanitizeIdentifiers(members.flatMap { it.identifiers })
        return Sighting(
            primary.device.copy(
                identifiers = identifiers,
                additionalIps = (primary.device.additionalIps + others.flatMap { it.device.additionalIps + it.device.ip })
                    .mapNotNull { IdentityPolicy.display(it) }.sorted().take(IdentityPolicy.MAX_ADDITIONAL_IPS).toSet(),
                observations = members.flatMap { it.device.observations }.distinct().take(IdentityPolicy.MAX_OBSERVATIONS),
                hostname = primary.device.hostname ?: others.firstNotNullOfOrNull { it.device.hostname },
                macAddress = primary.device.macAddress ?: others.firstNotNullOfOrNull { it.device.macAddress },
            ),
        )
    }

    /**
     * The only sighting allowed to keep a claimable identifier that is contested or whose owner is
     * confirmed elsewhere: the single carrier that is MAC-confirmed as the owner. Nothing else is
     * evidence of ownership — not address, not how many identifiers it reports (padding costs nothing).
     */
    fun keeper(carriers: List<Sighting>, macConfirmed: List<Sighting>): Sighting? =
        carriers.filter { it in macConfirmed }.singleOrNull()
}
