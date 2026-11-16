package com.ventouxlabs.netlens.core.data.testing

import com.ventouxlabs.netlens.core.data.dao.DeviceMergeDecisionDao
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [DeviceMergeDecisionDao]. Scan paths ([suggest], [updateSuggestion]) never touch a pair
 * the user already decided; only [setDistinct] replaces a stored decision. The fake does
 * NOT normalise `low < high` — the database does not either, so callers must.
 */
class FakeDeviceMergeDecisionDao : DeviceMergeDecisionDao {
    private val rows = MutableStateFlow<List<DeviceMergeDecisionEntity>>(emptyList())

    val all: List<DeviceMergeDecisionEntity> get() = rows.value

    override fun observeSuggestions(): Flow<List<DeviceMergeDecisionEntity>> = rows.map { list ->
        list.filter { it.decision == SUGGESTED }.sortedByDescending { it.score ?: Int.MIN_VALUE }.take(SUGGESTION_LIMIT)
    }

    override suspend fun get(low: Long, high: Long): DeviceMergeDecisionEntity? =
        rows.value.find { it.deviceIdLow == low && it.deviceIdHigh == high }

    override suspend fun distinctCountTouching(ids: List<Long>): Int =
        rows.value.count { it.decision == "DISTINCT" && (it.deviceIdLow in ids || it.deviceIdHigh in ids) }

    override suspend fun suggest(decision: DeviceMergeDecisionEntity): Long {
        if (get(decision.deviceIdLow, decision.deviceIdHigh) != null) return -1L
        rows.value = rows.value + decision
        return rows.value.size.toLong()
    }

    override suspend fun updateSuggestion(low: Long, high: Long, score: Int?, reasons: String?): Int {
        if (get(low, high)?.decision != SUGGESTED) return 0
        replace(low, high) { it.copy(score = score, reasons = reasons) }
        return 1
    }

    override suspend fun setDistinct(low: Long, high: Long, decidedAt: Long) =
        put(DeviceMergeDecisionEntity(low, high, "DISTINCT", null, null, decidedAt))

    internal fun removeForDevice(deviceId: Long) {
        rows.value = rows.value.filter { it.deviceIdLow != deviceId && it.deviceIdHigh != deviceId }
    }

    private fun put(decision: DeviceMergeDecisionEntity) {
        rows.value = rows.value.filterNot {
            it.deviceIdLow == decision.deviceIdLow && it.deviceIdHigh == decision.deviceIdHigh
        } + decision
    }

    private inline fun replace(low: Long, high: Long, transform: (DeviceMergeDecisionEntity) -> DeviceMergeDecisionEntity) {
        rows.value = rows.value.map { if (it.deviceIdLow == low && it.deviceIdHigh == high) transform(it) else it }
    }

    private companion object {
        const val SUGGESTED = "SUGGESTED"
        const val SUGGESTION_LIMIT = 50
    }
}
