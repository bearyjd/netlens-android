package us.beary.netlens.feature.dns.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.xbill.DNS.ARecord
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.Lookup
import org.xbill.DNS.MXRecord
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.NSRecord
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.SOARecord
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.TXTRecord
import us.beary.netlens.feature.dns.model.DnsRecordType
import us.beary.netlens.feature.dns.model.DnsResult
import javax.inject.Inject

class DnsResolverImpl @Inject constructor() : DnsResolver {

    override suspend fun lookup(
        domain: String,
        types: Set<DnsRecordType>,
    ): Result<List<DnsResult>> = withContext(Dispatchers.IO) {
        runCatching {
            types.map { type ->
                async { resolveType(domain, type) }
            }.awaitAll().flatten()
        }
    }

    private fun resolveType(domain: String, type: DnsRecordType): List<DnsResult> {
        val resolver = SimpleResolver().apply {
            setTimeout(java.time.Duration.ofSeconds(TIMEOUT_SECONDS))
        }
        val lookup = Lookup(domain, type.dnsType)
        lookup.setResolver(resolver)
        val records = lookup.run()
        if (records == null) {
            val nonErrorResults = setOf(Lookup.SUCCESSFUL, Lookup.HOST_NOT_FOUND, Lookup.TYPE_NOT_FOUND)
            if (lookup.result !in nonErrorResults) {
                throw java.io.IOException(lookup.errorString)
            }
            return emptyList()
        }
        return records.mapNotNull { record -> mapRecord(record, type) }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 10L
    }

    private fun mapRecord(
        record: org.xbill.DNS.Record,
        type: DnsRecordType,
    ): DnsResult? {
        val value = when (type) {
            DnsRecordType.A -> (record as? ARecord)?.address?.hostAddress
            DnsRecordType.AAAA -> (record as? AAAARecord)?.address?.hostAddress
            DnsRecordType.MX -> (record as? MXRecord)?.let { "${it.priority} ${it.target}" }
            DnsRecordType.TXT -> (record as? TXTRecord)?.strings?.joinToString(" ")
            DnsRecordType.CNAME -> (record as? CNAMERecord)?.target?.toString()
            DnsRecordType.NS -> (record as? NSRecord)?.target?.toString()
            DnsRecordType.SOA -> (record as? SOARecord)?.let { soa ->
                "${soa.host} ${soa.admin} ${soa.serial} ${soa.refresh} ${soa.retry} ${soa.expire} ${soa.minimum}"
            }
            DnsRecordType.PTR -> (record as? PTRRecord)?.target?.toString()
            DnsRecordType.SRV -> (record as? SRVRecord)?.let { srv ->
                "${srv.priority} ${srv.weight} ${srv.port} ${srv.target}"
            }
        } ?: return null

        return DnsResult(
            type = type,
            name = record.name.toString(),
            value = value,
            ttl = record.ttl,
        )
    }
}
