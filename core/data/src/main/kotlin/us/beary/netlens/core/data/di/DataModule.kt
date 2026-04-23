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
import us.beary.netlens.core.data.dao.EndpointDao
import us.beary.netlens.core.data.dao.NetworkEventDao
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NetLensDatabase =
        Room.databaseBuilder(
            context,
            NetLensDatabase::class.java,
            "netlens.db",
        )
            .addMigrations(MIGRATION_4_5)
            .fallbackToDestructiveMigration()
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
}
