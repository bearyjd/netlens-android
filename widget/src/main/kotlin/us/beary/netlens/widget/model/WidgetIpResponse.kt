package us.beary.netlens.widget.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WidgetIpResponse(
    val ip: String = "",
    @SerialName("country_code") val countryCode: String = "",
    val org: String = "",
)
