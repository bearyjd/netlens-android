package com.ventouxlabs.netlens.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.ventouxlabs.netlens.core.data.NetLensDatabase
import com.ventouxlabs.netlens.core.data.dao.DnsHistoryDao
import com.ventouxlabs.netlens.core.data.dao.EndpointDao
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.IpInfoHistoryDao
import com.ventouxlabs.netlens.core.data.dao.LanScanHistoryDao
import com.ventouxlabs.netlens.core.data.dao.NetworkEventDao
import com.ventouxlabs.netlens.core.data.dao.PingHistoryDao
import com.ventouxlabs.netlens.core.data.dao.PortScanHistoryDao
import com.ventouxlabs.netlens.core.data.dao.WhoisHistoryDao
import com.ventouxlabs.netlens.core.data.dao.WolTargetDao
import com.ventouxlabs.netlens.core.data.dao.TracerouteHistoryDao
import com.ventouxlabs.netlens.core.data.dao.TlsHistoryDao
import com.ventouxlabs.netlens.core.data.dao.HttpTesterHistoryDao
import com.ventouxlabs.netlens.core.data.dao.MdnsHistoryDao
import com.ventouxlabs.netlens.core.data.dao.SpeedTestHistoryDao
import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.data.dao.WolHistoryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_network_events_timestamp` ON `network_events` (`timestamp`)",
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE history_ping ADD COLUMN mode TEXT NOT NULL DEFAULT 'FIXED'")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_traceroute` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `host` TEXT NOT NULL, `hopCount` INTEGER NOT NULL, `hopsJson` TEXT NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_traceroute_timestamp` ON `history_traceroute` (`timestamp`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_tls` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `host` TEXT NOT NULL, `port` INTEGER NOT NULL, `issuer` TEXT NOT NULL, `subject` TEXT NOT NULL, `expiresAt` TEXT NOT NULL, `protocol` TEXT NOT NULL, `isValid` INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_tls_timestamp` ON `history_tls` (`timestamp`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_http` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `url` TEXT NOT NULL, `method` TEXT NOT NULL, `statusCode` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `responseSize` INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_http_timestamp` ON `history_http` (`timestamp`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_mdns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `serviceCount` INTEGER NOT NULL, `servicesJson` TEXT NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_mdns_timestamp` ON `history_mdns` (`timestamp`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_wol` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `mac` TEXT NOT NULL, `label` TEXT, `broadcastIp` TEXT NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_wol_timestamp` ON `history_wol` (`timestamp`)")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_ping` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `host` TEXT NOT NULL, `sentCount` INTEGER NOT NULL, `receivedCount` INTEGER NOT NULL, `minMs` REAL NOT NULL, `avgMs` REAL NOT NULL, `maxMs` REAL NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_ping_timestamp` ON `history_ping` (`timestamp`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_lanscan` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `ssid` TEXT, `subnet` TEXT, `deviceCount` INTEGER NOT NULL, `devicesJson` TEXT NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_lanscan_timestamp` ON `history_lanscan` (`timestamp`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_portscan` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `host` TEXT NOT NULL, `openPorts` TEXT NOT NULL, `totalScanned` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_portscan_timestamp` ON `history_portscan` (`timestamp`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_dns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `query` TEXT NOT NULL, `recordType` TEXT NOT NULL, `resultsJson` TEXT NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_dns_timestamp` ON `history_dns` (`timestamp`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_whois` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `query` TEXT NOT NULL, `rawResponse` TEXT NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_whois_timestamp` ON `history_whois` (`timestamp`)")

            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_ipinfo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `ip` TEXT NOT NULL, `isp` TEXT, `org` TEXT, `countryCode` TEXT, `city` TEXT, `isVpn` INTEGER NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_ipinfo_timestamp` ON `history_ipinfo` (`timestamp`)")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `history_speedtest` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `downloadMbps` REAL NOT NULL, `uploadMbps` REAL NOT NULL, `latencyMs` INTEGER NOT NULL, `serverName` TEXT NOT NULL)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_speedtest_timestamp` ON `history_speedtest` (`timestamp`)")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `known_devices` (`macAddress` TEXT NOT NULL, `hostname` TEXT, `ip` TEXT NOT NULL, `vendor` TEXT, `firstSeen` INTEGER NOT NULL, `lastSeen` INTEGER NOT NULL, `isKnown` INTEGER NOT NULL DEFAULT 0, `deviceType` TEXT, `osGuess` TEXT, PRIMARY KEY(`macAddress`))""",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_devices_lastSeen` ON `known_devices` (`lastSeen`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_devices_isKnown` ON `known_devices` (`isKnown`)")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE monitored_endpoints ADD COLUMN latencyThresholdMs INTEGER NOT NULL DEFAULT 1000",
            )
        }
    }

    // known_devices was keyed by macAddress, so any device the ARP-table
    // enrichment couldn't resolve a MAC for (common for mDNS/SSDP-only
    // devices, or devices absent from /proc/net/arp) was silently dropped
    // and never persisted to inventory. Switches identity to an autoGenerate
    // id with macAddress nullable, falling back to IP-based matching.
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `known_devices_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `macAddress` TEXT, `hostname` TEXT, `ip` TEXT NOT NULL, `vendor` TEXT, `firstSeen` INTEGER NOT NULL, `lastSeen` INTEGER NOT NULL, `isKnown` INTEGER NOT NULL DEFAULT 0, `deviceType` TEXT, `osGuess` TEXT)""",
            )
            db.execSQL(
                """INSERT INTO `known_devices_new` (macAddress, hostname, ip, vendor, firstSeen, lastSeen, isKnown, deviceType, osGuess)
                    SELECT macAddress, hostname, ip, vendor, firstSeen, lastSeen, isKnown, deviceType, osGuess FROM `known_devices`""",
            )
            db.execSQL("DROP TABLE `known_devices`")
            db.execSQL("ALTER TABLE `known_devices_new` RENAME TO `known_devices`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_devices_lastSeen` ON `known_devices` (`lastSeen`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_devices_isKnown` ON `known_devices` (`isKnown`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_known_devices_macAddress` ON `known_devices` (`macAddress`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_devices_ip` ON `known_devices` (`ip`)")
        }
    }

    // v13: custom device names + watched-network identity (gateway MAC).
    // Additive only — new columns are nullable, new table is independent.
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE known_devices ADD COLUMN customName TEXT")
            db.execSQL("ALTER TABLE known_devices ADD COLUMN networkId INTEGER")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `watched_networks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `displayName` TEXT, `gatewayMac` TEXT NOT NULL, `subnet` TEXT NOT NULL, `watchEnabled` INTEGER NOT NULL DEFAULT 1, `addedAt` INTEGER NOT NULL)""",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_watched_networks_gatewayMac` ON `watched_networks` (`gatewayMac`)")
        }
    }

    // Latency methodology changed in v14: old rows timed a full HTTPS HEAD, new rows time a raw
    // TCP connect. The DEFAULT tags every pre-existing row as LEGACY_HTTP; Room writes the explicit
    // TCP_CONNECT value on all inserts after this migration.
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE history_speedtest ADD COLUMN latencyMethod TEXT NOT NULL DEFAULT 'LEGACY_HTTP'")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NetLensDatabase =
        Room.databaseBuilder(
            context,
            NetLensDatabase::class.java,
            "netlens.db",
        )
            .addMigrations(
                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                MIGRATION_13_14,
            )
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideWolTargetDao(database: NetLensDatabase): WolTargetDao =
        database.wolTargetDao()

    @Provides
    fun provideNetworkEventDao(database: NetLensDatabase): NetworkEventDao =
        database.networkEventDao()

    @Provides
    fun provideEndpointDao(database: NetLensDatabase): EndpointDao =
        database.endpointDao()

    @Provides
    fun providePingHistoryDao(database: NetLensDatabase): PingHistoryDao =
        database.pingHistoryDao()

    @Provides
    fun provideLanScanHistoryDao(database: NetLensDatabase): LanScanHistoryDao =
        database.lanScanHistoryDao()

    @Provides
    fun providePortScanHistoryDao(database: NetLensDatabase): PortScanHistoryDao =
        database.portScanHistoryDao()

    @Provides
    fun provideDnsHistoryDao(database: NetLensDatabase): DnsHistoryDao =
        database.dnsHistoryDao()

    @Provides
    fun provideWhoisHistoryDao(database: NetLensDatabase): WhoisHistoryDao =
        database.whoisHistoryDao()

    @Provides
    fun provideIpInfoHistoryDao(database: NetLensDatabase): IpInfoHistoryDao =
        database.ipInfoHistoryDao()

    @Provides
    fun provideTracerouteHistoryDao(database: NetLensDatabase): TracerouteHistoryDao =
        database.tracerouteHistoryDao()

    @Provides
    fun provideTlsHistoryDao(database: NetLensDatabase): TlsHistoryDao =
        database.tlsHistoryDao()

    @Provides
    fun provideHttpTesterHistoryDao(database: NetLensDatabase): HttpTesterHistoryDao =
        database.httpTesterHistoryDao()

    @Provides
    fun provideMdnsHistoryDao(database: NetLensDatabase): MdnsHistoryDao =
        database.mdnsHistoryDao()

    @Provides
    fun provideWolHistoryDao(database: NetLensDatabase): WolHistoryDao =
        database.wolHistoryDao()

    @Provides
    fun provideSpeedTestHistoryDao(database: NetLensDatabase): SpeedTestHistoryDao =
        database.speedTestHistoryDao()

    @Provides
    fun provideKnownDeviceDao(database: NetLensDatabase): KnownDeviceDao =
        database.knownDeviceDao()

    @Provides
    fun provideWatchedNetworkDao(database: NetLensDatabase): WatchedNetworkDao =
        database.watchedNetworkDao()
}
