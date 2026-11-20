package com.ventouxlabs.netlens

import android.util.Log
import com.ventouxlabs.netlens.core.data.dao.NetworkEventDao
import com.ventouxlabs.netlens.core.data.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the fixed on-device retention period to tool history and the network event log, so
 * neither grows without bound. Each prune is isolated: a failure in one is logged and does not
 * stop the other or the caller.
 */
@Singleton
class RetentionPruner internal constructor(
    private val historyRepository: HistoryRepository,
    private val networkEventDao: NetworkEventDao,
    private val now: () -> Long,
    private val logFailure: (String, Throwable) -> Unit,
) {
    @Inject constructor(
        historyRepository: HistoryRepository,
        networkEventDao: NetworkEventDao,
    ) : this(
        historyRepository,
        networkEventDao,
        System::currentTimeMillis,
        { message, error -> Log.w(TAG, message, error) },
    )

    suspend fun prune() {
        runCatching { historyRepository.clearOlderThan(RETENTION_DAYS) }
            .onFailure { logFailure("History retention prune failed", it) }
        runCatching { networkEventDao.deleteOlderThan(now() - RETENTION_DAYS * DAY_MS) }
            .onFailure { logFailure("Network event retention prune failed", it) }
    }

    companion object {
        const val RETENTION_DAYS = 90
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val TAG = "RetentionPruner"
    }
}
