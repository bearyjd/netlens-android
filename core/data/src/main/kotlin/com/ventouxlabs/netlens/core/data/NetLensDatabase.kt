package com.ventouxlabs.netlens.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ventouxlabs.netlens.core.data.dao.DnsHistoryDao
import com.ventouxlabs.netlens.core.data.dao.EndpointDao
import com.ventouxlabs.netlens.core.data.dao.IpInfoHistoryDao
import com.ventouxlabs.netlens.core.data.dao.LanScanHistoryDao
import com.ventouxlabs.netlens.core.data.dao.NetworkEventDao
import com.ventouxlabs.netlens.core.data.dao.PingHistoryDao
import com.ventouxlabs.netlens.core.data.dao.PortScanHistoryDao
import com.ventouxlabs.netlens.core.data.dao.WhoisHistoryDao
import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.data.dao.WolTargetDao
import com.ventouxlabs.netlens.core.data.model.DnsHistoryEntry
import com.ventouxlabs.netlens.core.data.model.EndpointCheck
import com.ventouxlabs.netlens.core.data.model.IpInfoHistoryEntry
import com.ventouxlabs.netlens.core.data.model.LanScanHistoryEntry
import com.ventouxlabs.netlens.core.data.model.MonitoredEndpoint
import com.ventouxlabs.netlens.core.data.model.NetworkEvent
import com.ventouxlabs.netlens.core.data.model.PingHistoryEntry
import com.ventouxlabs.netlens.core.data.model.PortScanHistoryEntry
import com.ventouxlabs.netlens.core.data.model.SavedHost
import com.ventouxlabs.netlens.core.data.model.WhoisHistoryEntry
import com.ventouxlabs.netlens.core.data.model.WolTarget
import com.ventouxlabs.netlens.core.data.dao.TracerouteHistoryDao
import com.ventouxlabs.netlens.core.data.dao.TlsHistoryDao
import com.ventouxlabs.netlens.core.data.dao.HttpTesterHistoryDao
import com.ventouxlabs.netlens.core.data.dao.MdnsHistoryDao
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.SpeedTestHistoryDao
import com.ventouxlabs.netlens.core.data.dao.WolHistoryDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import com.ventouxlabs.netlens.core.data.model.TracerouteHistoryEntry
import com.ventouxlabs.netlens.core.data.model.TlsHistoryEntry
import com.ventouxlabs.netlens.core.data.model.HttpTesterHistoryEntry
import com.ventouxlabs.netlens.core.data.model.MdnsHistoryEntry
import com.ventouxlabs.netlens.core.data.model.SpeedTestHistoryEntry
import com.ventouxlabs.netlens.core.data.model.WolHistoryEntry

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
        KnownDeviceEntity::class,
        WatchedNetworkEntity::class,
    ],
    version = 14,
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
    abstract fun knownDeviceDao(): KnownDeviceDao
    abstract fun watchedNetworkDao(): WatchedNetworkDao
}
