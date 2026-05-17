package us.beary.netlens.widget

data class IpWidgetState(
    val ip: String = "",
    val isp: String = "",
    val isVpn: Boolean = false,
    val lanIp: String = "",
    val lastUpdatedEpochMs: Long = 0L,
)
