package us.beary.netlens.feature.dns.engine

import us.beary.netlens.feature.dns.model.DnsRecordType
import us.beary.netlens.feature.dns.model.DnsResult

interface DnsResolver {
    suspend fun lookup(domain: String, types: Set<DnsRecordType>): Result<List<DnsResult>>
}
