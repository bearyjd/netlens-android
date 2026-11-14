package com.ventouxlabs.netlens.core.scan.model.identity

import com.ventouxlabs.netlens.core.scan.model.identity.Scope.GLOBAL
import com.ventouxlabs.netlens.core.scan.model.identity.Scope.NETWORK
import com.ventouxlabs.netlens.core.scan.model.identity.Strength.MEDIUM
import com.ventouxlabs.netlens.core.scan.model.identity.Strength.STRONG

enum class IdentifierKind(val strength: Strength, val scope: Scope) {
    MAC_UNIVERSAL(STRONG, GLOBAL), // IEEE-assigned MAC (U/L bit clear)
    MAC_LOCAL(MEDIUM, NETWORK), // randomized / locally administered: valid on one network only
    UPNP_UDN(STRONG, GLOBAL), // uuid:... from SSDP description / USN
    UPNP_SERIAL(STRONG, GLOBAL), // <serialNumber>, keyed with manufacturer+model
    TLS_KEY_SHA256(STRONG, GLOBAL), // SHA-256 of SubjectPublicKeyInfo, self-signed only
    SSH_HOSTKEY_SHA256(STRONG, GLOBAL), // SHA-256 of host key blob
    SMB_SERVER_GUID(STRONG, GLOBAL),
    IPP_PRINTER_UUID(STRONG, GLOBAL),
    MDNS_DEVICE_ID(STRONG, GLOBAL), // AirPlay deviceid, Cast id, HAP id, Matter/ESPHome mac ...
    SNMP_ENGINE_ID(STRONG, GLOBAL),
}

// Hostname is deliberately NOT an identifier: device_identifiers has a UNIQUE (kind, value, scopeKey)
// index, and hostnames such as "iPhone" or "android-..." collide between devices on one network. The
// resolver reads known_devices.hostname directly for weak scoring instead.
enum class Strength { STRONG, MEDIUM, WEAK }

enum class Scope { GLOBAL, NETWORK }

/**
 * Unique, not authenticated: a hostile device can announce another device's identifier. Only
 * [proven] ones (completed TLS handshake / verified SSH signature) demonstrate key possession.
 */
data class DeviceIdentifier(
    val kind: IdentifierKind,
    val value: String, // normalised (lowercase hex, no separators, etc.)
    val source: ObservationSource,
    val proven: Boolean = false,
)

enum class ObservationSource {
    PING, ARP, OUI, MDNS, MDNS_TXT, SSDP, NETBIOS, PORTS, BANNER, HTTP, TLS, SSH, SMB, SNMP, IPP, RTSP, TTL, IPV6,
}

enum class ObservedField { DEVICE_TYPE, OS, OS_VERSION, VENDOR, MODEL, PRODUCT }

data class Observation(
    val source: ObservationSource,
    val field: ObservedField,
    val value: String, // flattened + capped
    val weight: Int, // 0..100 contribution, set by the rule that produced it
    val detail: String?, // human-readable evidence, e.g. "port 631 (IPP)"
)
