package com.ventouxlabs.netlens.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.ventouxlabs.netlens.core.data.dao.DnsHistoryDao
import com.ventouxlabs.netlens.core.data.dao.IpInfoHistoryDao
import com.ventouxlabs.netlens.core.data.dao.LanScanHistoryDao
import com.ventouxlabs.netlens.core.data.dao.PingHistoryDao
import com.ventouxlabs.netlens.core.data.dao.PortScanHistoryDao
import com.ventouxlabs.netlens.core.data.dao.WhoisHistoryDao
import com.ventouxlabs.netlens.core.data.dao.TracerouteHistoryDao
import com.ventouxlabs.netlens.core.data.dao.TlsHistoryDao
import com.ventouxlabs.netlens.core.data.dao.HttpTesterHistoryDao
import com.ventouxlabs.netlens.core.data.dao.MdnsHistoryDao
import com.ventouxlabs.netlens.core.data.dao.WolHistoryDao
import com.ventouxlabs.netlens.core.data.model.DnsHistoryEntry
import com.ventouxlabs.netlens.core.data.model.IpInfoHistoryEntry
import com.ventouxlabs.netlens.core.data.model.LanScanHistoryEntry
import com.ventouxlabs.netlens.core.data.model.PingHistoryEntry
import com.ventouxlabs.netlens.core.data.model.PortScanHistoryEntry
import com.ventouxlabs.netlens.core.data.model.WhoisHistoryEntry
import com.ventouxlabs.netlens.core.data.model.TracerouteHistoryEntry
import com.ventouxlabs.netlens.core.data.model.TlsHistoryEntry
import com.ventouxlabs.netlens.core.data.model.HttpTesterHistoryEntry
import com.ventouxlabs.netlens.core.data.model.MdnsHistoryEntry
import com.ventouxlabs.netlens.core.data.model.WolHistoryEntry
import com.ventouxlabs.netlens.core.data.model.HistoryDetailData
import androidx.room.withTransaction
import com.ventouxlabs.netlens.core.data.NetLensDatabase
import javax.inject.Inject
import javax.inject.Singleton

data class CombinedHistoryResults(
    val pings: List<PingHistoryEntry> = emptyList(),
    val lanScans: List<LanScanHistoryEntry> = emptyList(),
    val portScans: List<PortScanHistoryEntry> = emptyList(),
    val dnsLookups: List<DnsHistoryEntry> = emptyList(),
    val whoisLookups: List<WhoisHistoryEntry> = emptyList(),
    val ipInfoLookups: List<IpInfoHistoryEntry> = emptyList(),
    val traceroutes: List<TracerouteHistoryEntry> = emptyList(),
    val tlsInspections: List<TlsHistoryEntry> = emptyList(),
    val httpTests: List<HttpTesterHistoryEntry> = emptyList(),
    val mdnsScans: List<MdnsHistoryEntry> = emptyList(),
    val wolSends: List<WolHistoryEntry> = emptyList(),
)

/**
 * Read/clear access to every tool's history, combined.
 *
 * An interface so `HistoryViewModel` can be tested at all. The implementation wraps eleven Room
 * DAOs plus `NetLensDatabase.withTransaction`, none of which can be stood up in a JVM unit test —
 * depending on it directly left `:feature:history` as the last module in the repo with no tests.
 * Consumers depend on this; only Hilt sees [HistoryRepositoryImpl].
 */
interface HistoryRepository {
    fun allRecent(limit: Int = 50): Flow<CombinedHistoryResults>
    fun searchAll(query: String): Flow<CombinedHistoryResults>
    suspend fun clearAll()
    suspend fun clearOlderThan(days: Int)
    suspend fun getEntry(toolFilter: String, id: Long): HistoryDetailData?
}

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val database: NetLensDatabase,
    private val pingDao: PingHistoryDao,
    private val lanScanDao: LanScanHistoryDao,
    private val portScanDao: PortScanHistoryDao,
    private val dnsDao: DnsHistoryDao,
    private val whoisDao: WhoisHistoryDao,
    private val ipInfoDao: IpInfoHistoryDao,
    private val tracerouteDao: TracerouteHistoryDao,
    private val tlsDao: TlsHistoryDao,
    private val httpTesterDao: HttpTesterHistoryDao,
    private val mdnsDao: MdnsHistoryDao,
    private val wolHistoryDao: WolHistoryDao,
) : HistoryRepository {

    override fun allRecent(limit: Int): Flow<CombinedHistoryResults> {
        return combine(
            pingDao.getRecent(limit),
            lanScanDao.getRecent(limit),
            portScanDao.getRecent(limit),
            dnsDao.getRecent(limit),
            whoisDao.getRecent(limit),
        ) { pings, lans, ports, dns, whois ->
            CombinedHistoryResults(
                pings = pings,
                lanScans = lans,
                portScans = ports,
                dnsLookups = dns,
                whoisLookups = whois,
            )
        }.combine(ipInfoDao.getRecent(limit)) { partial, ipInfo ->
            partial.copy(ipInfoLookups = ipInfo)
        }.combine(tracerouteDao.getRecent(limit)) { partial, traceroutes ->
            partial.copy(traceroutes = traceroutes)
        }.combine(tlsDao.getRecent(limit)) { partial, tls ->
            partial.copy(tlsInspections = tls)
        }.combine(httpTesterDao.getRecent(limit)) { partial, http ->
            partial.copy(httpTests = http)
        }.combine(mdnsDao.getRecent(limit)) { partial, mdns ->
            partial.copy(mdnsScans = mdns)
        }.combine(wolHistoryDao.getRecent(limit)) { partial, wol ->
            partial.copy(wolSends = wol)
        }
    }

    override fun searchAll(query: String): Flow<CombinedHistoryResults> {
        return combine(
            pingDao.search(query),
            lanScanDao.search(query),
            portScanDao.search(query),
            dnsDao.search(query),
            whoisDao.search(query),
        ) { pings, lans, ports, dns, whois ->
            CombinedHistoryResults(
                pings = pings,
                lanScans = lans,
                portScans = ports,
                dnsLookups = dns,
                whoisLookups = whois,
            )
        }.combine(ipInfoDao.search(query)) { partial, ipInfo ->
            partial.copy(ipInfoLookups = ipInfo)
        }.combine(tracerouteDao.search(query)) { partial, traceroutes ->
            partial.copy(traceroutes = traceroutes)
        }.combine(tlsDao.search(query)) { partial, tls ->
            partial.copy(tlsInspections = tls)
        }.combine(httpTesterDao.search(query)) { partial, http ->
            partial.copy(httpTests = http)
        }.combine(mdnsDao.search(query)) { partial, mdns ->
            partial.copy(mdnsScans = mdns)
        }.combine(wolHistoryDao.search(query)) { partial, wol ->
            partial.copy(wolSends = wol)
        }
    }

    override suspend fun clearAll() {
        database.withTransaction {
            pingDao.deleteAll()
            lanScanDao.deleteAll()
            portScanDao.deleteAll()
            dnsDao.deleteAll()
            whoisDao.deleteAll()
            ipInfoDao.deleteAll()
            tracerouteDao.deleteAll()
            tlsDao.deleteAll()
            httpTesterDao.deleteAll()
            mdnsDao.deleteAll()
            wolHistoryDao.deleteAll()
        }
    }

    override suspend fun clearOlderThan(days: Int) {
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        database.withTransaction {
            pingDao.deleteOlderThan(cutoff)
            lanScanDao.deleteOlderThan(cutoff)
            portScanDao.deleteOlderThan(cutoff)
            dnsDao.deleteOlderThan(cutoff)
            whoisDao.deleteOlderThan(cutoff)
            ipInfoDao.deleteOlderThan(cutoff)
            tracerouteDao.deleteOlderThan(cutoff)
            tlsDao.deleteOlderThan(cutoff)
            httpTesterDao.deleteOlderThan(cutoff)
            mdnsDao.deleteOlderThan(cutoff)
            wolHistoryDao.deleteOlderThan(cutoff)
        }
    }

    override suspend fun getEntry(toolFilter: String, id: Long): HistoryDetailData? {
        return when (toolFilter) {
            "Ping" -> pingDao.getById(id)?.let { HistoryDetailData.Ping(it) }
            "LanScan" -> lanScanDao.getById(id)?.let { HistoryDetailData.LanScan(it) }
            "PortScan" -> portScanDao.getById(id)?.let { HistoryDetailData.PortScan(it) }
            "Dns" -> dnsDao.getById(id)?.let { HistoryDetailData.Dns(it) }
            "Whois" -> whoisDao.getById(id)?.let { HistoryDetailData.Whois(it) }
            "IpInfo" -> ipInfoDao.getById(id)?.let { HistoryDetailData.IpInfo(it) }
            "Traceroute" -> tracerouteDao.getById(id)?.let { HistoryDetailData.Traceroute(it) }
            "Tls" -> tlsDao.getById(id)?.let { HistoryDetailData.Tls(it) }
            "HttpTester" -> httpTesterDao.getById(id)?.let { HistoryDetailData.HttpTest(it) }
            "Mdns" -> mdnsDao.getById(id)?.let { HistoryDetailData.Mdns(it) }
            "Wol" -> wolHistoryDao.getById(id)?.let { HistoryDetailData.Wol(it) }
            else -> null
        }
    }
}
