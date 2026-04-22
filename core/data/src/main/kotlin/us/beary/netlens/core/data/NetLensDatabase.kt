package us.beary.netlens.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import us.beary.netlens.core.data.dao.EndpointDao
import us.beary.netlens.core.data.dao.NetworkEventDao
import us.beary.netlens.core.data.dao.WolTargetDao
import us.beary.netlens.core.data.model.EndpointCheck
import us.beary.netlens.core.data.model.MonitoredEndpoint
import us.beary.netlens.core.data.model.NetworkEvent
import us.beary.netlens.core.data.model.SavedHost
import us.beary.netlens.core.data.model.WolTarget

@Database(
    entities = [
        SavedHost::class,
        WolTarget::class,
        NetworkEvent::class,
        MonitoredEndpoint::class,
        EndpointCheck::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class NetLensDatabase : RoomDatabase() {
    abstract fun wolTargetDao(): WolTargetDao
    abstract fun networkEventDao(): NetworkEventDao
    abstract fun endpointDao(): EndpointDao
}
