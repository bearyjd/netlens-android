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
import com.ventoux.netlens.core.data.dao.TracerouteHistoryDao
import com.ventoux.netlens.core.data.dao.TlsHistoryDao
import com.ventoux.netlens.core.data.dao.HttpTesterHistoryDao
import com.ventoux.netlens.core.data.dao.MdnsHistoryDao
import com.ventoux.netlens.core.data.dao.SpeedTestHistoryDao
import com.ventoux.netlens.core.data.dao.WolHistoryDao
import com.ventoux.netlens.core.data.model.TracerouteHistoryEntry
import com.ventoux.netlens.core.data.model.TlsHistoryEntry
import com.ventoux.netlens.core.data.model.HttpTesterHistoryEntry
import com.ventoux.netlens.core.data.model.MdnsHistoryEntry
import com.ventoux.netlens.core.data.model.SpeedTestHistoryEntry
import com.ventoux.netlens.core.data.model.WolHistoryEntry

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
        TracerouteHistoryEntry::class,
        TlsHistoryEntry::class,
        HttpTesterHistoryEntry::class,
        MdnsHistoryEntry::class,
        WolHistoryEntry::class,
        SpeedTestHistoryEntry::class,
    ],
    version = 9,
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
    abstract fun tracerouteHistoryDao(): TracerouteHistoryDao
    abstract fun tlsHistoryDao(): TlsHistoryDao
    abstract fun httpTesterHistoryDao(): HttpTesterHistoryDao
    abstract fun mdnsHistoryDao(): MdnsHistoryDao
    abstract fun wolHistoryDao(): WolHistoryDao
    abstract fun speedTestHistoryDao(): SpeedTestHistoryDao
}
