package com.ventouxlabs.netlens.core.scan.network

import com.ventouxlabs.netlens.core.network.DisplayText
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.identity.DeviceIdentifier
import com.ventouxlabs.netlens.core.scan.model.identity.IdentifierKind
import com.ventouxlabs.netlens.core.scan.model.identity.MacAddresses
import com.ventouxlabs.netlens.core.scan.model.identity.Strength
import java.security.MessageDigest

/**
 * One way of recognising a network. Only [STRONG] kinds identify it across scans: `l3` (gateway IP +
 * subnet) is shared by countless home and hotel networks, so it is a same-session fallback hint and
 * never takes part in alias lookup, alias registration or background matching. BSSID and SSID are
 * labels only: they need the foreground and location permission, so a key built from them would
 * differ between a foreground scan and a background watch of the same network.
 */
data class NetworkKey(val kind: Kind, val value: String) {
    enum class Kind(val prefix: String, val strong: Boolean) {
        GATEWAY_MAC("gw-mac:", true),
        GATEWAY_ID("gw-id:", true),
        L3("l3:", false),
    }

    val key: String get() = kind.prefix + value
    val isStrong: Boolean get() = kind.strong

    companion object {
        /** The `gw-mac:` key for a gateway MAC as ARP or `watched_networks` spells it; null for junk or multicast. */
        fun gatewayMac(raw: String?): NetworkKey? {
            val mac = MacAddresses.normalize(raw)?.takeUnless(MacAddresses::isMulticast) ?: return null
            return NetworkKey(Kind.GATEWAY_MAC, mac)
        }
    }
}

/** What persistScan needs to know about the network a scan ran on. */
data class NetworkContext(val keys: List<NetworkKey> = emptyList(), val label: String? = null) {
    companion object {
        val NONE = NetworkContext()
    }
}

/** Builds the keys and label from what the platform reports. Pure: every input is untrusted text. */
object NetworkKeys {
    const val MAX_GATEWAY_ID_LENGTH = 128
    const val MAX_LABEL_LENGTH = 64

    /** Strongest first: `gw-mac`, `gw-id`, `l3`. [scanned] is searched for the device at the gateway IP. */
    fun build(gatewayMac: String?, gatewayIp: String?, subnet: String?, scanned: List<LanDevice>): List<NetworkKey> =
        listOfNotNull(NetworkKey.gatewayMac(gatewayMac), gatewayId(gatewayIp, scanned), layer3(gatewayIp, subnet))

    /** The SSID when the platform will tell us, else the subnet; flattened and capped for display. */
    fun label(ssid: String?, subnet: String?): String? =
        DisplayText.flatten(ssid)?.take(MAX_LABEL_LENGTH) ?: DisplayText.flatten(subnet)?.take(MAX_LABEL_LENGTH)

    private fun gatewayId(gatewayIp: String?, scanned: List<LanDevice>): NetworkKey? {
        if (gatewayIp == null) return null
        val gateway = scanned.firstOrNull { it.ip == gatewayIp || gatewayIp in it.additionalIps } ?: return null
        // A MAC is already the `gw-mac` key; every other strong global kind is a device-supplied claim.
        val id = gateway.identifiers
            .filter { it.kind.strength == Strength.STRONG && !it.kind.isMac() }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.value }))
            .firstNotNullOfOrNull { it.clean() } ?: return null
        return NetworkKey(NetworkKey.Kind.GATEWAY_ID, id)
    }

    private fun DeviceIdentifier.clean(): String? {
        val value = DisplayText.flatten(value)?.lowercase()?.take(MAX_GATEWAY_ID_LENGTH)?.trim().orEmpty()
        return if (value.isEmpty()) null else "${kind.name.lowercase()}:$value"
    }

    private fun IdentifierKind.isMac() = this == IdentifierKind.MAC_UNIVERSAL || this == IdentifierKind.MAC_LOCAL

    private fun layer3(gatewayIp: String?, subnet: String?): NetworkKey? {
        if (gatewayIp == null || subnet == null) return null
        val digest = MessageDigest.getInstance("SHA-256").digest("$gatewayIp|$subnet".toByteArray(Charsets.UTF_8))
        return NetworkKey(NetworkKey.Kind.L3, digest.joinToString("") { "%02x".format(it) })
    }
}
