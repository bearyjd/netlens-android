package com.ventouxlabs.netlens.feature.wifi

import com.ventouxlabs.netlens.core.data.dao.WifiSurveyDao
import com.ventouxlabs.netlens.core.data.model.WifiSurveyPointEntity
import com.ventouxlabs.netlens.core.data.model.WifiSurveySessionEntity
import com.ventouxlabs.netlens.core.data.model.WifiSurveySessionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class InMemoryWifiSurveyDao : WifiSurveyDao {

    val sessions = MutableStateFlow<List<WifiSurveySessionEntity>>(emptyList())
    val points = MutableStateFlow<List<WifiSurveyPointEntity>>(emptyList())

    private var nextSessionId = 1L
    private var nextPointId = 1L

    override suspend fun insertSession(session: WifiSurveySessionEntity): Long {
        val id = nextSessionId++
        sessions.value = sessions.value + session.copy(id = id)
        return id
    }

    override suspend fun endSession(id: Long, endedAt: Long) {
        sessions.value = sessions.value.map { if (it.id == id) it.copy(endedAt = endedAt) else it }
    }

    override suspend fun getSession(id: Long): WifiSurveySessionEntity? =
        sessions.value.find { it.id == id }

    // Mirrors the GROUP BY projection: MIN/MAX of avgRssi are the worst/best spots, and a
    // session with no points reports nulls rather than dropping out of the list. Combined over
    // both flows because Room re-runs a joined query when either table changes.
    override fun observeSessionSummaries(): Flow<List<WifiSurveySessionSummary>> =
        combine(sessions, points) { list, allPoints ->
            list.sortedByDescending { it.startedAt }.map { session ->
                val own = allPoints.filter { it.sessionId == session.id }
                WifiSurveySessionSummary(
                    id = session.id,
                    name = session.name,
                    ssid = session.ssid,
                    startedAt = session.startedAt,
                    endedAt = session.endedAt,
                    pointCount = own.size,
                    worstRssi = own.minOfOrNull { it.avgRssi },
                    bestRssi = own.maxOfOrNull { it.avgRssi },
                )
            }
        }

    override suspend fun deleteSession(id: Long) {
        sessions.value = sessions.value.filterNot { it.id == id }
        points.value = points.value.filterNot { it.sessionId == id }
    }

    override suspend fun insertPoint(point: WifiSurveyPointEntity): Long {
        val id = nextPointId++
        points.value = points.value + point.copy(id = id)
        return id
    }

    override fun observePoints(sessionId: Long): Flow<List<WifiSurveyPointEntity>> =
        points.map { list -> list.filter { it.sessionId == sessionId }.sortedBy { it.capturedAt } }

    override suspend fun deletePoint(id: Long) {
        points.value = points.value.filterNot { it.id == id }
    }
}
