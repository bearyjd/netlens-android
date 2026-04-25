package us.beary.netlens.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import us.beary.netlens.core.data.dao.DnsHistoryDao
import us.beary.netlens.core.data.dao.EndpointDao
import us.beary.netlens.core.data.dao.IpInfoHistoryDao
import us.beary.netlens.core.data.dao.LanScanHistoryDao
import us.beary.netlens.core.data.dao.NetworkEventDao
import us.beary.netlens.core.data.dao.PingHistoryDao
import us.beary.netlens.core.data.dao.PortScanHistoryDao
import us.beary.netlens.core.data.dao.WhoisHistoryDao
import us.beary.netlens.core.data.dao.WolTargetDao
import us.beary.netlens.core.data.model.DnsHistoryEntry
import us.beary.netlens.core.data.model.EndpointCheck
import us.beary.netlens.core.data.model.IpInfoHistoryEntry
import us.beary.netlens.core.data.model.LanScanHistoryEntry
import us.beary.netlens.core.data.model.MonitoredEndpoint
import us.beary.netlens.core.data.model.NetworkEvent
import us.beary.netlens.core.data.model.PingHistoryEntry
import us.beary.netlens.core.data.model.PortScanHistoryEntry
import us.beary.netlens.core.data.model.SavedHost
import us.beary.netlens.core.data.model.WhoisHistoryEntry
import us.beary.netlens.core.data.model.WolTarget

@Database(
    entities = [
        SavedHost::class,
        WolTarget::class,
        NetworkEvent::class,
        MonitoredEndpoint::class,
        EndpointCheck::class,
        PingHistoryEntry::class,
        LanScanHistoryEntry::class,
        PortScanHistoryEntry::class,
        DnsHistoryEntry::class,
        WhoisHistoryEntry::class,
        IpInfoHistoryEntry::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class NetLensDatabase : RoomDatabase() {
    abstract fun wolTargetDao(): WolTargetDao
    abstract fun networkEventDao(): NetworkEventDao
    abstract fun endpointDao(): EndpointDao
    abstract fun pingHistoryDao(): PingHistoryDao
    abstract fun lanScanHistoryDao(): LanScanHistoryDao
    abstract fun portScanHistoryDao(): PortScanHistoryDao
    abstract fun dnsHistoryDao(): DnsHistoryDao
    abstract fun whoisHistoryDao(): WhoisHistoryDao
    abstract fun ipInfoHistoryDao(): IpInfoHistoryDao
}
