package com.ventoux.netlens.feature.ipinfo.model

import kotlinx.serialization.Serializable

@Serializable
data class IpInfoResponse(
    val ip: String = "",
    val hostname: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val loc: String = "",
    val org: String = "",
    val postal: String = "",
    val timezone: String = "",
) {
    val latitude: Double get() = loc.split(",").getOrNull(0)?.toDoubleOrNull() ?: 0.0
    val longitude: Double get() = loc.split(",").getOrNull(1)?.toDoubleOrNull() ?: 0.0
    val asNumber: String get() = org.substringBefore(" ").takeIf { it.startsWith("AS") } ?: ""
    val orgName: String get() = org.substringAfter(" ").ifBlank { org }
    val countryFlag: String get() {
        if (country.length != 2) return ""
        return country.uppercase().map { char ->
            String(Character.toChars(0x1F1E6 - 'A'.code + char.code))
        }.joinToString("")
    }
}
