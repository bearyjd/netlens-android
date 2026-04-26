package com.ventoux.netlens.widget.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WidgetIpResponse(
    val query: String = "",
    val country: String = "",
    val countryCode: String = "",
    val isp: String = "",
    @SerialName("as") val asName: String = "",
)
