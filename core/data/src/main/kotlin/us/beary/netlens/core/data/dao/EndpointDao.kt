package us.beary.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import us.beary.netlens.core.data.model.EndpointCheck
import us.beary.netlens.core.data.model.MonitoredEndpoint

@Dao
interface EndpointDao {

    @Query("SELECT * FROM monitored_endpoints ORDER BY createdAt DESC")
    fun getAllEndpoints(): Flow<List<MonitoredEndpoint>>

    @Query("SELECT * FROM monitored_endpoints WHERE id = :id")
    suspend fun getEndpointById(id: Long): MonitoredEndpoint?

    @Insert
    suspend fun insertEndpoint(endpoint: MonitoredEndpoint): Long

    @Delete
    suspend fun deleteEndpoint(endpoint: MonitoredEndpoint)

    @Query(
        "SELECT * FROM endpoint_checks WHERE endpointId = :endpointId ORDER BY timestamp DESC LIMIT :limit",
    )
    fun getChecksForEndpoint(endpointId: Long, limit: Int = 50): Flow<List<EndpointCheck>>

    @Insert
    suspend fun insertCheck(check: EndpointCheck)

    @Query("DELETE FROM endpoint_checks WHERE timestamp < :before")
    suspend fun deleteChecksOlderThan(before: Long)
}
