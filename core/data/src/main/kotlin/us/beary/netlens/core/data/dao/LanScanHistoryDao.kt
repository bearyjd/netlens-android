package us.beary.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import us.beary.netlens.core.data.model.LanScanHistoryEntry

@Dao
interface LanScanHistoryDao {
    @Query("SELECT * FROM history_lanscan ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<LanScanHistoryEntry>>

    @Query("SELECT * FROM history_lanscan WHERE subnet LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<LanScanHistoryEntry>>

    @Insert
    suspend fun insert(entry: LanScanHistoryEntry)

    @Query("DELETE FROM history_lanscan WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_lanscan WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_lanscan")
    suspend fun deleteAll()
}
