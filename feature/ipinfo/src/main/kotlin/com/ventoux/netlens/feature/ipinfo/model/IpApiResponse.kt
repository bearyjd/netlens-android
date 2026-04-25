package com.ventoux.netlens.feature.ipinfo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IpApiResponse(
    val query: String,
    val isp: String,
    val org: String,
    @SerialName("as") val asNumber: String,
    val country: String,
    val countryCode: String = "",
    val regionName: String,
    val city: String,
    val lat: Double,
    val lon: Double,
    val proxy: Boolean,
    val hosting: Boolean,
)
