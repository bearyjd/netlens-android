package us.beary.netlens.core.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "endpoint_checks",
    foreignKeys = [ForeignKey(
        entity = MonitoredEndpoint::class,
        parentColumns = ["id"],
        childColumns = ["endpointId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("endpointId")],
)
data class EndpointCheck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val endpointId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val statusCode: Int,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
)
