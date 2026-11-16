package com.ventouxlabs.netlens.core.data.model

import org.json.JSONArray
import org.json.JSONObject

/** Layout version of the JSON written by [mergeLogEntry]; bump when a snapshot's shape changes. */
internal const val MERGE_LOG_SNAPSHOT_VERSION = 1

/**
 * The audit row for folding [loser] into [survivor], from state read BEFORE anything moved.
 * Every string goes through org.json, so hostile text is escaped, never concatenated, and a null
 * stays JSON null (distinct from "").
 */
internal fun mergeLogEntry(
    survivor: KnownDeviceEntity,
    loser: KnownDeviceEntity,
    loserIdentifiers: List<DeviceIdentifierEntity>,
    loserSightings: List<DeviceSightingEntity>,
    decision: DeviceMergeDecisionEntity?,
    kind: MergeKind,
    mergedAt: Long,
) = MergeLogEntity(
    mergedAt = mergedAt,
    survivorId = survivor.id,
    loserId = loser.id,
    kind = kind.name,
    survivorIsKnownBefore = survivor.isKnown,
    decisionScore = decision?.score,
    decisionReasons = decision?.reasons,
    snapshotVersion = MERGE_LOG_SNAPSHOT_VERSION,
    loserRowJson = loser.toJson().toString(),
    loserIdentifiersJson = JSONArray(loserIdentifiers.map { it.toJson() }).toString(),
    loserSightingsJson = JSONArray(loserSightings.map { it.toJson() }).toString(),
    survivorUserFieldsBeforeJson = survivor.userFieldsJson().toString(),
)

private fun json(vararg fields: Pair<String, Any?>) = JSONObject().apply {
    fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
}

private fun KnownDeviceEntity.toJson() = json(
    "id" to id, "macAddress" to macAddress, "hostname" to hostname, "ip" to ip, "vendor" to vendor,
    "firstSeen" to firstSeen, "lastSeen" to lastSeen, "isKnown" to isKnown, "deviceType" to deviceType,
    "osGuess" to osGuess, "fingerprintConfidence" to fingerprintConfidence,
    "fingerprintEvidence" to fingerprintEvidence, "customName" to customName, "networkId" to networkId,
    "tags" to tags, "notes" to notes, "location" to location, "model" to model,
    "deviceTypeOverride" to deviceTypeOverride, "osOverride" to osOverride, "modelOverride" to modelOverride,
    "typeConfidence" to typeConfidence, "osConfidence" to osConfidence,
)

private fun KnownDeviceEntity.userFieldsJson() = json(
    "customName" to customName, "tags" to tags, "notes" to notes, "location" to location,
    "deviceTypeOverride" to deviceTypeOverride, "osOverride" to osOverride, "modelOverride" to modelOverride,
)

private fun DeviceIdentifierEntity.toJson() = json(
    "id" to id, "deviceId" to deviceId, "kind" to kind, "value" to value, "scopeKey" to scopeKey,
    "source" to source, "proven" to proven, "firstSeen" to firstSeen, "lastSeen" to lastSeen,
)

private fun DeviceSightingEntity.toJson() = json(
    "deviceId" to deviceId, "networkKey" to networkKey, "networkLabel" to networkLabel,
    "lastIp" to lastIp, "firstSeen" to firstSeen, "lastSeen" to lastSeen,
)
