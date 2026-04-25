package com.ventoux.netlens.feature.dns.engine

import com.ventoux.netlens.feature.dns.model.DnsRecordType
import com.ventoux.netlens.feature.dns.model.DnsResult

interface DnsResolver {
    suspend fun lookup(domain: String, types: Set<DnsRecordType>): Result<List<DnsResult>>
}
