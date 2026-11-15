package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceObservationEntity
import com.ventouxlabs.netlens.core.data.model.DeviceSightingEntity
import com.ventouxlabs.netlens.core.data.model.FingerprintEvidence
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.identity.GLOBAL_SCOPE
import com.ventouxlabs.netlens.core.scan.identity.IdentityPolicy
import com.ventouxlabs.netlens.core.scan.identity.ResolvedDevice
import com.ventouxlabs.netlens.core.scan.identity.claimEvidence
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.MAX_MODEL_LENGTH
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind
import com.ventouxlabs.netlens.core.scan.model.identity.Scope

/** Pure translation of one resolved scan reading into row values. No I/O, no mutation. */

/** A (value, confidence) pair for one detected field. */
private data class Detected(val value: String?, val confidence: Int?)

/**
 * Applies [device] to this row: scan-derived columns only, "higher confidence wins" per field. A
 * reading that says nothing about a field leaves it and its confidence alone; user-authored columns
 * and `isKnown` are never touched.
 */
internal fun KnownDeviceEntity.withScan(device: LanDevice, now: Long, networkId: Long?): KnownDeviceEntity {
    // Legacy rows carry one confidence for both fields: freeze it into each field that has a value
    // before the aggregate can rise, or a later reading for the other field is judged against the wrong number.
    val legacy = fingerprintConfidence ?: 0
    val type = pick(Detected(deviceType, typeConfidence ?: legacy.takeIf { deviceType != null }), device.deviceType, device.fingerprintConfidence)
    val os = pick(Detected(osGuess, osConfidence ?: legacy.takeIf { osGuess != null }), device.osGuess, device.fingerprintConfidence)
    return copy(
        macAddress = chosenMac(macAddress, device.macAddress),
        hostname = device.hostname ?: hostname,
        ip = device.ip,
        vendor = device.vendor ?: vendor,
        lastSeen = now,
        deviceType = type.value,
        typeConfidence = type.confidence,
        osGuess = os.value,
        osConfidence = os.confidence,
        fingerprintConfidence = maxOf(device.fingerprintConfidence, fingerprintConfidence ?: 0),
        fingerprintEvidence = FingerprintEvidence.merge(fingerprintEvidence, device.fingerprintEvidence),
        model = device.model?.take(MAX_MODEL_LENGTH) ?: model,
        networkId = networkId ?: this.networkId,
    )
}

private fun pick(existing: Detected, candidate: String?, candidateConfidence: Int): Detected = when {
    candidate == null -> existing
    existing.value == null -> Detected(candidate, candidateConfidence)
    candidateConfidence > (existing.confidence ?: 0) -> Detected(candidate, candidateConfidence)
    else -> existing
}

/**
 * The display MAC column: the most recent universal MAC, or the most recent local one when no
 * universal MAC is stored. A local MAC never replaces a stored universal one; junk is ignored.
 */
internal fun chosenMac(stored: String?, scanned: String?): String? {
    val scannedKind = kindOf(scanned) ?: return stored
    return if (stored == null || scannedKind == IdentifierKind.MAC_UNIVERSAL || kindOf(stored) != IdentifierKind.MAC_UNIVERSAL) scanned else stored
}

private fun kindOf(mac: String?): IdentifierKind? =
    IdentityPolicy.macIdentifier(mac)?.kind

internal fun ResolvedDevice.newRow(now: Long, networkId: Long?): KnownDeviceEntity = device.let { d ->
    KnownDeviceEntity(
        macAddress = d.macAddress, hostname = d.hostname, ip = d.ip, vendor = d.vendor,
        firstSeen = now, lastSeen = now, isKnown = false,
        deviceType = d.deviceType, osGuess = d.osGuess,
        fingerprintConfidence = d.fingerprintConfidence,
        typeConfidence = d.fingerprintConfidence.takeIf { d.deviceType != null },
        osConfidence = d.fingerprintConfidence.takeIf { d.osGuess != null },
        fingerprintEvidence = FingerprintEvidence.format(d.fingerprintEvidence),
        model = d.model?.take(MAX_MODEL_LENGTH),
        networkId = networkId,
    )
}

internal fun ResolvedDevice.identifierEntities(canonicalKey: String, now: Long): List<DeviceIdentifierEntity> =
    ownedIdentifiers.map { it.toEntity(canonicalKey, now) }

private fun DeviceIdentifier.toEntity(canonicalKey: String, now: Long) = DeviceIdentifierEntity(
    deviceId = 0, kind = kind.name, value = value,
    scopeKey = if (kind.scope == Scope.GLOBAL) GLOBAL_SCOPE else canonicalKey,
    source = source.name, proven = proven, firstSeen = now, lastSeen = now,
)

/** The device's own evidence plus every untrusted claim as weight-0 evidence (never as ownership). */
internal fun ResolvedDevice.observationEntities(now: Long): List<DeviceObservationEntity> =
    device.observations.map {
        DeviceObservationEntity(
            deviceId = 0, source = it.source.name, field = it.field.name, value = it.value,
            weight = it.weight, detail = it.detail, lastSeen = now,
        )
    } + untrustedClaims.take(IdentityPolicy.MAX_IDENTIFIERS_PER_KIND).map { claimEvidence(it, now) }

internal fun ResolvedDevice.sighting(deviceId: Long, canonicalKey: String, now: Long) = DeviceSightingEntity(
    deviceId = deviceId, networkKey = canonicalKey, networkLabel = null, lastIp = device.ip, firstSeen = now, lastSeen = now,
)

