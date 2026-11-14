package com.ventouxlabs.netlens.core.scan.identity

import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceIdentifierDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceIdentityDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceMergeDecisionDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceObservationDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceSightingDao
import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.data.testing.FakeNetworkKeyAliasDao
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind
import com.ventouxlabs.netlens.core.scan.model.identity.ObservationSource
import com.ventouxlabs.netlens.core.scan.model.identity.Scope

const val NET = "net-home"
const val NOW = 1_000_000_000_000L

/** The resolver over the shared in-memory DAO fakes. */
class IdentityHarness {
    val known = FakeKnownDeviceDao()
    val identifiers = FakeDeviceIdentifierDao()
    val observations = FakeDeviceObservationDao()
    val sightings = FakeDeviceSightingDao()
    val decisions = FakeDeviceMergeDecisionDao()
    val aliases = FakeNetworkKeyAliasDao()
    private val tx = FakeDeviceIdentityDao(known, identifiers, observations, sightings, decisions)
    val resolver = DeviceIdentityResolver(known, identifiers, decisions, aliases, tx)

    /** Seeds a known_devices row (verbatim id); NETWORK-scoped identifiers are stored under [scopeKey], GLOBAL ones under ''. */
    suspend fun seedRow(
        id: Long,
        ip: String,
        ids: List<DeviceIdentifier> = emptyList(),
        mac: String? = null,
        scopeKey: String = "",
        firstSeen: Long = 1,
        lastSeen: Long = 1,
        isKnown: Boolean = false,
        customName: String? = null,
        tags: String? = null,
        notes: String? = null,
        location: String? = null,
        osOverride: String? = null,
        modelOverride: String? = null,
        deviceTypeOverride: String? = null,
        hostname: String? = null,
        model: String? = null,
        vendor: String? = null,
        deviceType: String? = null,
    ): KnownDeviceEntity {
        val row = KnownDeviceEntity(
            id = id, macAddress = mac, hostname = hostname, ip = ip, vendor = vendor, firstSeen = firstSeen,
            lastSeen = lastSeen, isKnown = isKnown, customName = customName, tags = tags, notes = notes,
            location = location, osOverride = osOverride, modelOverride = modelOverride,
            deviceTypeOverride = deviceTypeOverride, model = model, deviceType = deviceType,
        )
        known.seed(row)
        ids.forEach { identifiers.insertIfNew(entity(id, it, scopeKey)) }
        return row
    }

    private fun entity(deviceId: Long, id: DeviceIdentifier, scopeKey: String) = DeviceIdentifierEntity(
        deviceId = deviceId, kind = id.kind.name, value = id.value,
        scopeKey = if (id.kind.scope == Scope.GLOBAL) "" else scopeKey,
        source = id.source.name, proven = id.proven, firstSeen = 1, lastSeen = 1,
    )

    suspend fun resolve(vararg devices: LanDevice, network: String = NET, options: ResolveOptions = ResolveOptions()) =
        resolver.resolve(devices.toList(), network, NOW, options)
}

fun udn(value: String, proven: Boolean = false) =
    DeviceIdentifier(IdentifierKind.UPNP_UDN, value, ObservationSource.SSDP, proven)

fun smb(value: String) = DeviceIdentifier(IdentifierKind.SMB_SERVER_GUID, value, ObservationSource.SMB)

fun universalMac(value: String) = DeviceIdentifier(IdentifierKind.MAC_UNIVERSAL, value, ObservationSource.ARP)

fun localMac(value: String) = DeviceIdentifier(IdentifierKind.MAC_LOCAL, value, ObservationSource.ARP)

fun lan(ip: String, mac: String? = null, vararg ids: DeviceIdentifier, hostname: String? = null) =
    LanDevice(ip = ip, macAddress = mac, hostname = hostname, identifiers = ids.toSet())

/** Colon-separated uppercase form (as scanners emit) and its normalised identifier value. */
const val MAC1 = "00:BB:CC:00:00:01" // first octet 0x00: universally administered
const val MAC1_ID = "00bbcc000001"
const val MAC2 = "00:BB:CC:00:00:02"
const val MAC2_ID = "00bbcc000002"
const val MAC3 = "00:BB:CC:00:00:03"
const val MAC_LOCAL_RAW = "02:BB:CC:00:00:09" // U/L bit set: randomised
const val MAC_LOCAL_ID = "02bbcc000009"
