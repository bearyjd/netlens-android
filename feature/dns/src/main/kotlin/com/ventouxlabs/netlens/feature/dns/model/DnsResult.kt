package com.ventouxlabs.netlens.feature.dns.model

data class DnsResult(
    val type: DnsRecordType,
    val name: String,
    val value: String,
    val ttl: Long,
)
