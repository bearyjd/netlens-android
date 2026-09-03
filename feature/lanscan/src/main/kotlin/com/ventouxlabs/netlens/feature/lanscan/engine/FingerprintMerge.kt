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
    val candidateWins = candidate.fingerprintConfidence > existing.fingerprintConfidence
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
        deviceType = if (candidateWins) candidate.deviceType else existing.deviceType,
        osGuess = if (candidateWins) candidate.osGuess else existing.osGuess,
        fingerprintConfidence = maxOf(existing.fingerprintConfidence, candidate.fingerprintConfidence),
        fingerprintEvidence = (existing.fingerprintEvidence + candidate.fingerprintEvidence).distinct(),
        latencyMs = maxOf(existing.latencyMs, candidate.latencyMs),
        macAddress = existing.macAddress ?: candidate.macAddress,
        vendor = existing.vendor ?: candidate.vendor,
    )
}
