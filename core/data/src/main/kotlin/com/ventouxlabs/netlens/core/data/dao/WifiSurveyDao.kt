package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ventouxlabs.netlens.core.data.model.WifiSurveyPointEntity
import com.ventouxlabs.netlens.core.data.model.WifiSurveySessionEntity
import com.ventouxlabs.netlens.core.data.model.WifiSurveySessionSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiSurveyDao {

    @Insert
    suspend fun insertSession(session: WifiSurveySessionEntity): Long

    @Query("UPDATE wifi_survey_sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun endSession(id: Long, endedAt: Long)

    @Query("SELECT * FROM wifi_survey_sessions WHERE id = :id")
    suspend fun getSession(id: Long): WifiSurveySessionEntity?

    // MIN/MAX read as "worst/best" because RSSI is negative: -85 dBm is the weak end.
    @Query(
        "SELECT s.id AS id, s.name AS name, s.ssid AS ssid, s.startedAt AS startedAt, " +
            "s.endedAt AS endedAt, COUNT(p.id) AS pointCount, MIN(p.avgRssi) AS worstRssi, " +
            "MAX(p.avgRssi) AS bestRssi FROM wifi_survey_sessions s " +
            "LEFT JOIN wifi_survey_points p ON p.sessionId = s.id " +
            "GROUP BY s.id ORDER BY s.startedAt DESC",
    )
    fun observeSessionSummaries(): Flow<List<WifiSurveySessionSummary>>

    @Query("DELETE FROM wifi_survey_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insertPoint(point: WifiSurveyPointEntity): Long

    @Query("SELECT * FROM wifi_survey_points WHERE sessionId = :sessionId ORDER BY capturedAt ASC")
    fun observePoints(sessionId: Long): Flow<List<WifiSurveyPointEntity>>

    @Query("DELETE FROM wifi_survey_points WHERE id = :id")
    suspend fun deletePoint(id: Long)
}
