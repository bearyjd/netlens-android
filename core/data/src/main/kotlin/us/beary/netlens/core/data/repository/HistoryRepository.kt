package us.beary.netlens.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import us.beary.netlens.core.data.dao.DnsHistoryDao
import us.beary.netlens.core.data.dao.IpInfoHistoryDao
import us.beary.netlens.core.data.dao.LanScanHistoryDao
import us.beary.netlens.core.data.dao.PingHistoryDao
import us.beary.netlens.core.data.dao.PortScanHistoryDao
import us.beary.netlens.core.data.dao.WhoisHistoryDao
import us.beary.netlens.core.data.model.DnsHistoryEntry
import us.beary.netlens.core.data.model.IpInfoHistoryEntry
import us.beary.netlens.core.data.model.LanScanHistoryEntry
import us.beary.netlens.core.data.model.PingHistoryEntry
import us.beary.netlens.core.data.model.PortScanHistoryEntry
import us.beary.netlens.core.data.model.WhoisHistoryEntry
import androidx.room.withTransaction
import us.beary.netlens.core.data.NetLensDatabase
import javax.inject.Inject
import javax.inject.Singleton

data class CombinedHistoryResults(
    val pings: List<PingHistoryEntry> = emptyList(),
    val lanScans: List<LanScanHistoryEntry> = emptyList(),
    val portScans: List<PortScanHistoryEntry> = emptyList(),
    val dnsLookups: List<DnsHistoryEntry> = emptyList(),
    val whoisLookups: List<WhoisHistoryEntry> = emptyList(),
    val ipInfoLookups: List<IpInfoHistoryEntry> = emptyList(),
)

@Singleton
class HistoryRepository @Inject constructor(
    private val database: NetLensDatabase,
    private val pingDao: PingHistoryDao,
    private val lanScanDao: LanScanHistoryDao,
    private val portScanDao: PortScanHistoryDao,
    private val dnsDao: DnsHistoryDao,
    private val whoisDao: WhoisHistoryDao,
    private val ipInfoDao: IpInfoHistoryDao,
) {
    fun allRecent(limit: Int = 50): Flow<CombinedHistoryResults> {
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
        }
    }

    fun searchAll(query: String): Flow<CombinedHistoryResults> {
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
        }
    }

    suspend fun clearAll() {
        database.withTransaction {
            pingDao.deleteAll()
            lanScanDao.deleteAll()
            portScanDao.deleteAll()
            dnsDao.deleteAll()
            whoisDao.deleteAll()
            ipInfoDao.deleteAll()
        }
    }

    suspend fun clearOlderThan(days: Int) {
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        database.withTransaction {
            pingDao.deleteOlderThan(cutoff)
            lanScanDao.deleteOlderThan(cutoff)
            portScanDao.deleteOlderThan(cutoff)
            dnsDao.deleteOlderThan(cutoff)
            whoisDao.deleteOlderThan(cutoff)
            ipInfoDao.deleteOlderThan(cutoff)
        }
    }
}
