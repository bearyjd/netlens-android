package us.beary.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import us.beary.netlens.core.data.model.IpInfoHistoryEntry

@Dao
interface IpInfoHistoryDao {
    @Query("SELECT * FROM history_ipinfo ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<IpInfoHistoryEntry>>

    @Query("SELECT * FROM history_ipinfo WHERE ip LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<IpInfoHistoryEntry>>

    @Insert
    suspend fun insert(entry: IpInfoHistoryEntry)

    @Query("DELETE FROM history_ipinfo WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_ipinfo WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_ipinfo")
    suspend fun deleteAll()
}
