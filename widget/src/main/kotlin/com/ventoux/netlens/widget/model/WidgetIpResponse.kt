package com.ventoux.netlens.widget.model

import kotlinx.serialization.Serializable

@Serializable
data class WidgetIpResponse(
    val ip: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val org: String = "",
    val loc: String = "",
)
