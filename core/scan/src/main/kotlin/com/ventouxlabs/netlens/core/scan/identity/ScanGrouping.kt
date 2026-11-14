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
        for (i in sightings.indices) {
            for (j in i + 1 until sightings.size) {
                if (sharesProvenIdentifier(sightings[i], sightings[j])) parent[find(j)] = find(i)
            }
        }
        return sightings.indices.groupBy(::find).values.map { members -> combine(members.map { sightings[it] }) }
    }

    private fun sharesProvenIdentifier(a: Sighting, b: Sighting): Boolean {
        val proven = a.identifiers.filter { it.proven && IdentityPolicy.isClaimable(it) }.map { it.claimKey() }.toSet()
        return b.identifiers.any { it.proven && it.claimKey() in proven }
    }

    private fun combine(members: List<Sighting>): Sighting {
        if (members.size == 1) return members.single()
        val primary = members.sortedWith(compareBy({ -it.identifiers.size }, { it.device.ip })).first()
        val others = members - primary
        val identifiers = members.flatMap { it.identifiers }
            .groupBy { it.claimKey() }
            .map { (_, same) -> same.first().copy(proven = same.all { it.proven }) }
            .toCollection(linkedSetOf())
        return Sighting(
            primary.device.copy(
                identifiers = identifiers,
                additionalIps = primary.device.additionalIps + others.flatMap { it.device.additionalIps + it.device.ip },
                observations = members.flatMap { it.device.observations }.distinct(),
                hostname = primary.device.hostname ?: others.firstNotNullOfOrNull { it.device.hostname },
                macAddress = primary.device.macAddress ?: others.firstNotNullOfOrNull { it.device.macAddress },
            ),
        )
    }

    /** Claimable identifiers carried by two or more sightings, with the sightings that carry them. */
    fun contested(sightings: List<Sighting>): Map<ClaimKey, List<Sighting>> =
        sightings
            .flatMap { s -> s.identifiers.filter(IdentityPolicy::isClaimable).map { it.claimKey() to s } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }

    /**
     * The sighting that keeps a contested identifier: highest [tie] to the row that already owns it,
     * then most identifiers, then lowest address, so the outcome never depends on scan order.
     */
    fun incumbent(carriers: List<Sighting>, tie: (Sighting) -> Int): Sighting =
        carriers.sortedWith(compareBy({ -tie(it) }, { -it.identifiers.size }, { it.device.ip })).first()
}
