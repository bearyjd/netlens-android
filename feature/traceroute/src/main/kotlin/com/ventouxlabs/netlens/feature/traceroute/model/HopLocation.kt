package com.ventouxlabs.netlens.feature.traceroute.model

import kotlinx.serialization.Serializable

@Serializable
data class HopLocation(
    val city: String = "",
    val country: String = "",
    val countryCode: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val org: String = "",
)
