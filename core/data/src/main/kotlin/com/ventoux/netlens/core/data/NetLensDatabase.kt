package com.ventoux.netlens.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ventoux.netlens.core.data.dao.DnsHistoryDao
import com.ventoux.netlens.core.data.dao.EndpointDao
import com.ventoux.netlens.core.data.dao.IpInfoHistoryDao
import com.ventoux.netlens.core.data.dao.LanScanHistoryDao
import com.ventoux.netlens.core.data.dao.NetworkEventDao
import com.ventoux.netlens.core.data.dao.PingHistoryDao
import com.ventoux.netlens.core.data.dao.PortScanHistoryDao
import com.ventoux.netlens.core.data.dao.WhoisHistoryDao
import com.ventoux.netlens.core.data.dao.WolTargetDao
import com.ventoux.netlens.core.data.model.DnsHistoryEntry
import com.ventoux.netlens.core.data.model.EndpointCheck
import com.ventoux.netlens.core.data.model.IpInfoHistoryEntry
import com.ventoux.netlens.core.data.model.LanScanHistoryEntry
import com.ventoux.netlens.core.data.model.MonitoredEndpoint
import com.ventoux.netlens.core.data.model.NetworkEvent
import com.ventoux.netlens.core.data.model.PingHistoryEntry
import com.ventoux.netlens.core.data.model.PortScanHistoryEntry
import com.ventoux.netlens.core.data.model.SavedHost
import com.ventoux.netlens.core.data.model.WhoisHistoryEntry
import com.ventoux.netlens.core.data.model.WolTarget

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
    version = 7,
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
