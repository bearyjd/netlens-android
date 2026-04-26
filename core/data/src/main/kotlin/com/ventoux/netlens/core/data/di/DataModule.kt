package com.ventoux.netlens.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.ventoux.netlens.core.data.NetLensDatabase
import com.ventoux.netlens.core.data.dao.DnsHistoryDao
import com.ventoux.netlens.core.data.dao.EndpointDao
import com.ventoux.netlens.core.data.dao.IpInfoHistoryDao
import com.ventoux.netlens.core.data.dao.LanScanHistoryDao
import com.ventoux.netlens.core.data.dao.NetworkEventDao
import com.ventoux.netlens.core.data.dao.PingHistoryDao
import com.ventoux.netlens.core.data.dao.PortScanHistoryDao
import com.ventoux.netlens.core.data.dao.WhoisHistoryDao
import com.ventoux.netlens.core.data.dao.WolTargetDao
import com.ventoux.netlens.core.data.dao.TracerouteHistoryDao
import com.ventoux.netlens.core.data.dao.TlsHistoryDao
import com.ventoux.netlens.core.data.dao.HttpTesterHistoryDao
import com.ventoux.netlens.core.data.dao.MdnsHistoryDao
import com.ventoux.netlens.core.data.dao.WolHistoryDao
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NetLensDatabase =
        Room.databaseBuilder(
            context,
            NetLensDatabase::class.java,
            "netlens.db",
        )
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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
}
