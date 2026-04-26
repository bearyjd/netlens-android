package com.ventoux.netlens.feature.history.model

import androidx.annotation.StringRes
import com.ventoux.netlens.feature.history.R

enum class ToolFilter(@StringRes val labelRes: Int) {
    All(R.string.history_filter_all),
    Ping(R.string.history_filter_ping),
    LanScan(R.string.history_filter_lan),
    PortScan(R.string.history_filter_ports),
    Dns(R.string.history_filter_dns),
    Whois(R.string.history_filter_whois),
    IpInfo(R.string.history_filter_ipinfo),
    Traceroute(R.string.history_filter_traceroute),
    Tls(R.string.history_filter_tls),
    HttpTester(R.string.history_filter_http),
    Mdns(R.string.history_filter_mdns),
    Wol(R.string.history_filter_wol),
}
