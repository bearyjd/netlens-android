package com.ventouxlabs.netlens.feature.lanscan.engine

import com.ventouxlabs.netlens.core.scan.model.DiscoveryMethod
import com.ventouxlabs.netlens.core.scan.model.LanDevice

/**
 * Merges [existing] and [candidate] readings of the same device, keeping the higher-confidence
 * deviceType/osGuess/evidence rather than whichever arrived first.
 *
 * Concurrent discovery jobs (ping, mDNS, SSDP) can all resolve the same device with different
 * classification strength — see LanScanViewModel.mergeDevice, which used to take whichever result
 * landed first via `?:`. That let a weak hostname guess that happened to resolve early permanently
 * block a strong mDNS signal moments behind it.
 */
internal fun strongerFingerprint(existing: LanDevice, candidate: LanDevice): LanDevice {
    // Three-way, not a boolean: a genuine tie (including the common 0-vs-0 "neither side has
    // a real signal" case) must leave every field exactly as existing had it — see the
    // "equal confidence keeps existing" test. Only a STRICT winner gets to contribute a field
    // the other side lacks; ties get no fallback in either direction.
    val comparison = candidate.fingerprintConfidence.compareTo(existing.fingerprintConfidence)
    return existing.copy(
        hostname = existing.hostname ?: candidate.hostname,
        // Only escalate when the two readings actually came from different discovery
        // methods — not on every merge. LanScanViewModel also calls this to reconcile a
        // single device's own pre- and post-fingerprint() classification (same discovery
        // method, same event), where an unconditional MULTIPLE would mislabel a
        // ping-only device as multiply-discovered on its very first pass.
        discoveryMethod = if (existing.discoveryMethod == candidate.discoveryMethod) {
            existing.discoveryMethod
        } else {
            DiscoveryMethod.MULTIPLE
        },
        services = (existing.services + candidate.services).distinct(),
        // The stricter winner's value wins when it has one; if the winner has no opinion on
        // this specific field, fall back to the loser's value rather than losing it outright.
        // Symmetric in both directions: a high-confidence SSDP reading with no OS must not
        // erase a real hostname-derived osGuess (candidate-wins direction), and a real
        // hostname-derived osGuess must not be discarded just because SSDP later wins overall
        // on deviceType alone (existing-wins direction) — both directions independently
        // verified against Codex review, not assumed.
        deviceType = when {
            comparison > 0 -> candidate.deviceType ?: existing.deviceType
            comparison < 0 -> existing.deviceType ?: candidate.deviceType
            else -> existing.deviceType
        },
        osGuess = when {
            comparison > 0 -> candidate.osGuess ?: existing.osGuess
            comparison < 0 -> existing.osGuess ?: candidate.osGuess
            else -> existing.osGuess
        },
        fingerprintConfidence = maxOf(existing.fingerprintConfidence, candidate.fingerprintConfidence),
        fingerprintEvidence = (existing.fingerprintEvidence + candidate.fingerprintEvidence).distinct(),
        latencyMs = maxOf(existing.latencyMs, candidate.latencyMs),
        macAddress = existing.macAddress ?: candidate.macAddress,
        vendor = existing.vendor ?: candidate.vendor,
    )
}
