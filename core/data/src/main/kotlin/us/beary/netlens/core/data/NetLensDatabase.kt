package us.beary.netlens.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import us.beary.netlens.core.data.dao.WolTargetDao
import us.beary.netlens.core.data.model.SavedHost
import us.beary.netlens.core.data.model.WolTarget

@Database(
    entities = [SavedHost::class, WolTarget::class],
    version = 2,
    exportSchema = true,
)
abstract class NetLensDatabase : RoomDatabase() {
    abstract fun wolTargetDao(): WolTargetDao
}
