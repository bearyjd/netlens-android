package com.ventoux.netlens.core.data.model

sealed interface HistoryDetailData {
    data class Ping(val entry: PingHistoryEntry) : HistoryDetailData
    data class LanScan(val entry: LanScanHistoryEntry) : HistoryDetailData
    data class PortScan(val entry: PortScanHistoryEntry) : HistoryDetailData
    data class Dns(val entry: DnsHistoryEntry) : HistoryDetailData
    data class Whois(val entry: WhoisHistoryEntry) : HistoryDetailData
    data class IpInfo(val entry: IpInfoHistoryEntry) : HistoryDetailData
    data class Traceroute(val entry: TracerouteHistoryEntry) : HistoryDetailData
    data class Tls(val entry: TlsHistoryEntry) : HistoryDetailData
    data class HttpTest(val entry: HttpTesterHistoryEntry) : HistoryDetailData
    data class Mdns(val entry: MdnsHistoryEntry) : HistoryDetailData
    data class Wol(val entry: WolHistoryEntry) : HistoryDetailData
}
