package us.beary.netlens.feature.history.model

import androidx.annotation.StringRes
import us.beary.netlens.feature.history.R

enum class ToolFilter(@StringRes val labelRes: Int) {
    All(R.string.history_filter_all),
    Ping(R.string.history_filter_ping),
    LanScan(R.string.history_filter_lan),
    PortScan(R.string.history_filter_ports),
    Dns(R.string.history_filter_dns),
    Whois(R.string.history_filter_whois),
    IpInfo(R.string.history_filter_ipinfo),
}
