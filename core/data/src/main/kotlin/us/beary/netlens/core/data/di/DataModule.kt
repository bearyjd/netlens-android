package us.beary.netlens.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import us.beary.netlens.core.data.NetLensDatabase
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
}
