package com.ventouxlabs.netlens.core.scan.model.identity

/**
 * MAC handling shared by the scan pipeline and the resolver. The normalised form is 12 lowercase hex
 * digits with no separators — exactly what MIGRATION_18_19 backfilled into `device_identifiers`.
 */
object MacAddresses {
    private const val MAC_HEX_LENGTH = 12
    private const val EUI64_HEX_LENGTH = 16
    private const val MAX_RAW_LENGTH = 32
    private const val BROADCAST = "ffffffffffff"
    private const val ZERO = "000000000000"
    private const val UL_BIT = 0x02
    private const val MULTICAST_BIT = 0x01
    private val SEPARATORS = Regex("[:.-]")
    private val HEX = Regex("^[0-9a-f]+$")

    /** Normalises any common spelling; null for junk, all-zero or broadcast. */
    fun normalize(raw: String?): String? {
        if (raw == null || raw.length > MAX_RAW_LENGTH) return null
        val bare = raw.trim(' ').lowercase().replace(SEPARATORS, "")
        if (bare.length != MAC_HEX_LENGTH || !HEX.matches(bare)) return null
        return bare.takeUnless { it == ZERO || it == BROADCAST }
    }

    /** Locally administered (randomised) when the U/L bit of the first octet is set. [normalized] must come from [normalize]. */
    fun isLocallyAdministered(normalized: String): Boolean = firstOctet(normalized) and UL_BIT != 0

    fun isMulticast(normalized: String): Boolean = firstOctet(normalized) and MULTICAST_BIT != 0

    fun kindOf(normalized: String): IdentifierKind =
        if (isLocallyAdministered(normalized)) IdentifierKind.MAC_LOCAL else IdentifierKind.MAC_UNIVERSAL

    /** Modified EUI-64 interface id (e.g. an IPv6 SLAAC suffix) back to a MAC: strip `ff:fe`, flip U/L. Null if not EUI-64. */
    fun fromEui64(interfaceId: String): String? {
        val bare = interfaceId.trim().lowercase().replace(SEPARATORS, "")
        if (bare.length != EUI64_HEX_LENGTH || !HEX.matches(bare) || bare.substring(6, 10) != "fffe") return null
        val first = (bare.substring(0, 2).toInt(16) xor UL_BIT).toString(16).padStart(2, '0')
        return normalize(first + bare.substring(2, 6) + bare.substring(10))
    }

    /** Inverse of [fromEui64]: 16 hex digits, no separators. */
    fun toEui64(normalized: String): String {
        val first = (firstOctet(normalized) xor UL_BIT).toString(16).padStart(2, '0')
        return first + normalized.substring(2, 6) + "fffe" + normalized.substring(6)
    }

    private fun firstOctet(normalized: String): Int = normalized.substring(0, 2).toInt(16)
}
