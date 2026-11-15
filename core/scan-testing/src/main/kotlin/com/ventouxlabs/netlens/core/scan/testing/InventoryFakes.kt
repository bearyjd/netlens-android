package com.ventouxlabs.netlens.core.scan.testing

import com.ventouxlabs.netlens.core.data.dao.DeviceIdentityDao
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceIdentifierDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceIdentityDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceMergeDecisionDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceObservationDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceSightingDao
import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.data.testing.FakeNetworkKeyAliasDao
import com.ventouxlabs.netlens.core.data.testing.FakeTransactionRunner
import com.ventouxlabs.netlens.core.scan.AutoMergeSetting
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepositoryImpl
import com.ventouxlabs.netlens.core.scan.NewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.identity.DeviceIdentifierRecorder
import com.ventouxlabs.netlens.core.scan.identity.DeviceIdentityResolver

/**
 * Every in-memory DAO the inventory repository needs, wired around the caller's [known] fake so a
 * test can assert on the same instance it hands to a ViewModel. The fakes are not atomic.
 */
class InventoryFakes(val known: FakeKnownDeviceDao = FakeKnownDeviceDao()) {
    val identifiers = FakeDeviceIdentifierDao()
    val observations = FakeDeviceObservationDao()
    val sightings = FakeDeviceSightingDao()
    val decisions = FakeDeviceMergeDecisionDao()
    val aliases = FakeNetworkKeyAliasDao()
    val identity = FakeDeviceIdentityDao(known, identifiers, observations, sightings, decisions)

    /** [identityDao] lets a test wrap the transactional DAO (e.g. to inject a competing writer). */
    fun repository(
        notifier: NewDeviceNotifier,
        autoMerge: Boolean = false,
        identityDao: DeviceIdentityDao = identity,
    ) = DeviceInventoryRepositoryImpl(
        knownDeviceDao = known,
        newDeviceNotifier = notifier,
        resolver = DeviceIdentityResolver(known, identifiers, decisions, aliases, identityDao),
        recorder = DeviceIdentifierRecorder(identifiers),
        identityDao = identityDao,
        observationDao = observations,
        sightingDao = sightings,
        decisionDao = decisions,
        transactions = FakeTransactionRunner(),
        autoMergeSetting = AutoMergeSetting { autoMerge },
    )

    /**
     * Seeds a row as MIGRATION_18_19 leaves a pre-identity one: the row itself plus its stored MAC as
     * a `MAC_*` identifier with an empty scope key. Without it the resolver cannot see the row.
     */
    suspend fun seedLegacyRow(row: KnownDeviceEntity) {
        known.seed(row)
        val mac = row.macAddress?.replace(":", "")?.replace("-", "")?.lowercase() ?: return
        if (mac.length != 12 || !mac.all { it in '0'..'9' || it in 'a'..'f' }) return
        val kind = if (mac.substring(0, 2).toInt(16) and 0x02 != 0) "MAC_LOCAL" else "MAC_UNIVERSAL"
        identifiers.insertIfNew(
            DeviceIdentifierEntity(
                deviceId = row.id, kind = kind, value = mac, scopeKey = "", source = "ARP", firstSeen = row.firstSeen, lastSeen = row.lastSeen,
            ),
        )
    }
}
