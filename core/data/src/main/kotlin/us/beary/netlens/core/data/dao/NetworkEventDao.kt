package us.beary.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import us.beary.netlens.core.data.model.NetworkEvent

@Dao
interface NetworkEventDao {

    @Query("SELECT * FROM network_events ORDER BY timestamp DESC")
    fun getAll(): Flow<List<NetworkEvent>>

    @Query("SELECT * FROM network_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<NetworkEvent>>

    @Insert
    suspend fun insert(event: NetworkEvent)

    @Query("DELETE FROM network_events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM network_events")
    suspend fun deleteAll()
}
