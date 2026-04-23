package us.beary.netlens.widget.util

object Deeplink {
    const val SCHEME = "netlens"
    const val HOST = "feature"

    const val IPINFO = "$SCHEME://$HOST/ipinfo"
    const val LAN_SCAN = "$SCHEME://$HOST/lanscan"
    const val DNS = "$SCHEME://$HOST/dns"

    fun lanScanForDevice(ip: String): String = "$LAN_SCAN?device=$ip"
    fun dnsWithServer(server: String): String = "$DNS?server=$server"
}
