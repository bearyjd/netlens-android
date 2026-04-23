package us.beary.netlens.widget

data class IpWidgetState(
    val publicIp: String = "",
    val isp: String = "",
    val countryCode: String = "",
    val isVpn: Boolean = false,
    val isConnected: Boolean = false,
    val ssid: String? = null,
    val localIp: String? = null,
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
)
