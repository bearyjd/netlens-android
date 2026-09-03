package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceInventoryRepository {
    /**
     * Persists a scan's devices into the known-device inventory, tagging each row with
     * [networkId] (null for ad-hoc foreground scans). Fires [NewDeviceNotifier] exactly
     * once per genuinely new row. Shared by foreground LanScan and the background watch.
     */
    suspend fun persistScan(devices: List<LanDevice>, networkId: Long?)
}

@Singleton
class DeviceInventoryRepositoryImpl @Inject constructor(
    private val knownDeviceDao: KnownDeviceDao,
    private val newDeviceNotifier: NewDeviceNotifier,
) : DeviceInventoryRepository {

    override suspend fun persistScan(devices: List<LanDevice>, networkId: Long?) {
        val now = System.currentTimeMillis()
        for (device in devices) {
            val mac = device.macAddress
            // Devices with no resolvable MAC still get an inventory row keyed by IP;
            // if a MAC later resolves for that IP, upgrade the row instead of duplicating.
            val existing = mac?.let { knownDeviceDao.getByMac(it) }
                ?: knownDeviceDao.getByIpWithoutMac(device.ip)
            if (existing != null) {
                if (mac != null && existing.macAddress == null) {
                    knownDeviceDao.setMacAddress(existing.id, mac)
                }
                val existingConfidence = existing.fingerprintConfidence ?: 0
                val candidateWins = device.fingerprintConfidence > existingConfidence
                knownDeviceDao.updateLastSeen(
                    id = existing.id,
                    hostname = device.hostname ?: existing.hostname,
                    ip = device.ip,
                    vendor = device.vendor ?: existing.vendor,
                    lastSeen = now,
                    deviceType = if (candidateWins) device.deviceType else existing.deviceType ?: device.deviceType,
                    osGuess = if (candidateWins) device.osGuess else existing.osGuess ?: device.osGuess,
                    fingerprintConfidence = maxOf(device.fingerprintConfidence, existingConfidence),
                    fingerprintEvidence = mergeEvidence(existing.fingerprintEvidence, device.fingerprintEvidence),
                )
                if (networkId != null && existing.networkId != networkId) {
                    knownDeviceDao.setNetworkId(existing.id, networkId)
                }
            } else {
                val entity = KnownDeviceEntity(
                    macAddress = mac,
                    hostname = device.hostname,
                    ip = device.ip,
                    vendor = device.vendor,
                    firstSeen = now,
                    lastSeen = now,
                    isKnown = false,
                    deviceType = device.deviceType,
                    osGuess = device.osGuess,
                    fingerprintConfidence = device.fingerprintConfidence,
                    fingerprintEvidence = device.fingerprintEvidence.joinToString(", ").ifEmpty { null },
                    networkId = networkId,
                )
                val insertResult = knownDeviceDao.insertIfNew(entity)
                if (insertResult != -1L) {
                    newDeviceNotifier.notify(entity.copy(id = insertResult))
                }
            }
        }
    }
}

/** Joins persisted (", "-delimited) evidence with a fresh scan's evidence list, de-duplicated. */
private fun mergeEvidence(existingJoined: String?, freshList: List<String>): String? {
    val existingList = existingJoined?.split(", ")?.filter { it.isNotEmpty() }.orEmpty()
    val merged = (existingList + freshList).distinct()
    return merged.joinToString(", ").ifEmpty { null }
}
