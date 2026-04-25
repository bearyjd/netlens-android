package us.beary.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import us.beary.netlens.core.data.model.PingHistoryEntry

@Dao
interface PingHistoryDao {
    @Query("SELECT * FROM history_ping ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<PingHistoryEntry>>

    @Query("SELECT * FROM history_ping WHERE host LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<PingHistoryEntry>>

    @Insert
    suspend fun insert(entry: PingHistoryEntry)

    @Query("DELETE FROM history_ping WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_ping WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_ping")
    suspend fun deleteAll()
}
