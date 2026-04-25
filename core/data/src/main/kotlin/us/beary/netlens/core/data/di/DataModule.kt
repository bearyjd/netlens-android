package us.beary.netlens.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.core.data.NetLensDatabase
import us.beary.netlens.core.data.dao.DnsHistoryDao
import us.beary.netlens.core.data.dao.EndpointDao
import us.beary.netlens.core.data.dao.IpInfoHistoryDao
import us.beary.netlens.core.data.dao.LanScanHistoryDao
import us.beary.netlens.core.data.dao.NetworkEventDao
import us.beary.netlens.core.data.dao.PingHistoryDao
import us.beary.netlens.core.data.dao.PortScanHistoryDao
import us.beary.netlens.core.data.dao.WhoisHistoryDao
import us.beary.netlens.core.data.dao.WolTargetDao
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
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
}
