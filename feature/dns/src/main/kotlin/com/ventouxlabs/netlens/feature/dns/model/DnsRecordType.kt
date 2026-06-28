package com.ventouxlabs.netlens.feature.dns.model

enum class DnsRecordType(val displayName: String, val dnsType: Int) {
    A("A", 1),
    AAAA("AAAA", 28),
    MX("MX", 15),
    TXT("TXT", 16),
    CNAME("CNAME", 5),
    NS("NS", 2),
    SOA("SOA", 6),
    PTR("PTR", 12),
    SRV("SRV", 33),
}
