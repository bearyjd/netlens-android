package com.ventoux.netlens.feature.dns.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.xbill.DNS.ARecord
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.MXRecord
import org.xbill.DNS.Message
import org.xbill.DNS.NSRecord
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.SOARecord
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.Section
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.TXTRecord
import com.ventoux.netlens.feature.dns.model.DnsRecordType
import com.ventoux.netlens.feature.dns.model.DnsResult
import javax.inject.Inject

class DnsResolverImpl @Inject constructor() : DnsResolver {

    override suspend fun lookup(
        domain: String,
        types: Set<DnsRecordType>,
    ): Result<List<DnsResult>> = try {
        require(domain.isNotBlank()) { "Domain must not be blank" }
        val results = withContext(Dispatchers.IO) {
            types.map { type ->
                async { resolveType(domain, type) }
            }.awaitAll().flatten()
        }
        Result.success(results)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun resolveType(domain: String, type: DnsRecordType): List<DnsResult> {
        val resolver = SimpleResolver(DEFAULT_DNS_SERVER).apply {
            setTimeout(java.time.Duration.ofSeconds(TIMEOUT_SECONDS))
        }
        val name = Name.fromString(if (domain.endsWith(".")) domain else "$domain.")
        val query = Message.newQuery(Record.newRecord(name, type.dnsType, DClass.IN))
        val response = resolver.send(query)

        val rcode = response.header.rcode
        if (rcode != Rcode.NOERROR && rcode != Rcode.NXDOMAIN) {
            throw java.io.IOException("DNS query failed: ${Rcode.string(rcode)}")
        }

        return response.getSection(Section.ANSWER)
            .mapNotNull { record -> mapRecord(record, type) }
    }

    companion object {
        private const val DEFAULT_DNS_SERVER = "8.8.8.8"
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
