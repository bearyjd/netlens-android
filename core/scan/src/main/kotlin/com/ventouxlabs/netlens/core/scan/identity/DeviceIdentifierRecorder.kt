package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.data.dao.DeviceIdentifierDao
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.Scope
import javax.inject.Inject

/**
 * Write path for identifiers of a device that already has an id. Bumps `lastSeen` on ones the device
 * owns, adds new ones up to [IdentityPolicy.MAX_IDENTIFIERS_PER_KIND] per kind, and never takes an
 * identifier another device owns.
 */
class DeviceIdentifierRecorder @Inject constructor(
    private val identifierDao: DeviceIdentifierDao,
) {
    suspend fun record(deviceId: Long, identifiers: Set<DeviceIdentifier>, canonicalNetworkKey: String, now: Long) {
        for (id in IdentityPolicy.sanitizeIdentifiers(identifiers)) {
            val scopeKey = if (id.kind.scope == Scope.GLOBAL) GLOBAL_SCOPE else canonicalNetworkKey
            val owner = identifierDao.find(id.kind.name, id.value, scopeKey)
            when {
                owner != null && owner.deviceId == deviceId -> identifierDao.touch(owner.id, now)
                owner != null -> Unit // owned by another device: unique, not authenticated, never stolen
                else -> identifierDao.insertBelowCap(
                    DeviceIdentifierEntity(
                        deviceId = deviceId, kind = id.kind.name, value = id.value, scopeKey = scopeKey,
                        source = id.source.name, proven = id.proven, firstSeen = now, lastSeen = now,
                    ),
                    IdentityPolicy.MAX_IDENTIFIERS_PER_KIND,
                )
            }
        }
    }
}

/** `device_identifiers.scopeKey` of a GLOBAL identifier. */
internal const val GLOBAL_SCOPE = ""
