package us.beary.netlens.core.data.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NetLensDatabase =
        Room.databaseBuilder(
            context,
            NetLensDatabase::class.java,
            "netlens.db",
        )
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
