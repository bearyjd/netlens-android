package com.ventoux.netlens.feature.dns.engine

import com.ventoux.netlens.feature.dns.model.DnsRecordType
import com.ventoux.netlens.feature.dns.model.DnsResult

class FakeDnsResolver : DnsResolver {
    var result: Result<List<DnsResult>> = Result.success(emptyList())
    var lastDomain: String? = null
    var lastTypes: Set<DnsRecordType>? = null

    override suspend fun lookup(domain: String, types: Set<DnsRecordType>): Result<List<DnsResult>> {
        lastDomain = domain
        lastTypes = types
        return result
    }
}
