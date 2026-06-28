package com.ventouxlabs.netlens.feature.dns.engine

import com.ventouxlabs.netlens.feature.dns.model.DnsRecordType
import com.ventouxlabs.netlens.feature.dns.model.DnsResult

interface DnsResolver {
    suspend fun lookup(domain: String, types: Set<DnsRecordType>): Result<List<DnsResult>>
}
