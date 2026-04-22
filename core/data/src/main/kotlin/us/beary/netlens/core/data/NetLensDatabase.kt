package us.beary.netlens.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import us.beary.netlens.core.data.model.SavedHost

@Database(
    entities = [SavedHost::class],
    version = 1,
    exportSchema = true,
)
abstract class NetLensDatabase : RoomDatabase()
