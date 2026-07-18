package com.ventouxlabs.netlens.feature.speedtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ventouxlabs.netlens.core.data.dao.SpeedTestHistoryDao
import com.ventouxlabs.netlens.core.data.model.SpeedTestHistoryEntry
import com.ventouxlabs.netlens.feature.speedtest.engine.SpeedTestEngine
import com.ventouxlabs.netlens.feature.speedtest.model.SpeedTestPhase
import com.ventouxlabs.netlens.feature.speedtest.model.SpeedTestUiState
import javax.inject.Inject

@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    private val engine: SpeedTestEngine,
    private val historyDao: SpeedTestHistoryDao,
) : ViewModel() {

    private val _state = MutableStateFlow(SpeedTestUiState())
    val state: StateFlow<SpeedTestUiState> = _state.asStateFlow()

    val history: StateFlow<List<SpeedTestHistoryEntry>> = historyDao.getRecent(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var testJob: Job? = null

    fun startTest() {
        testJob?.cancel()
        _state.update {
            SpeedTestUiState(
                phase = SpeedTestPhase.LATENCY,
                isRunning = true,
            )
        }

        testJob = viewModelScope.launch {
            try {
                // Phase 1: Latency
                val latency = engine.measureLatency()
                _state.update {
                    it.copy(
                        latencyMs = latency,
                        phase = SpeedTestPhase.DOWNLOAD,
                        progress = 0f,
                    )
                }

                // Phase 2: Download
                engine.measureDownload()
                    .catch { e ->
                        _state.update {
                            it.copy(
                                error = e.message ?: "Download test failed",
                                isRunning = false,
                                phase = SpeedTestPhase.IDLE,
                            )
                        }
                        return@catch
                    }
                    .collect { progress ->
                        _state.update {
                            it.copy(
                                downloadMbps = progress.speedMbps,
                                progress = windowProgress(progress.elapsedMs),
                            )
                        }
                    }

                if (_state.value.error != null) return@launch

                _state.update {
                    it.copy(
                        phase = SpeedTestPhase.UPLOAD,
                        progress = 0f,
                    )
                }

                // Phase 3: Upload
                engine.measureUpload()
                    .catch { e ->
                        _state.update {
                            it.copy(
                                error = e.message ?: "Upload test failed",
                                isRunning = false,
                                phase = SpeedTestPhase.IDLE,
                            )
                        }
                        return@catch
                    }
                    .collect { progress ->
                        _state.update {
                            it.copy(
                                uploadMbps = progress.speedMbps,
                                progress = windowProgress(progress.elapsedMs),
                            )
                        }
                    }

                if (_state.value.error != null) return@launch

                _state.update {
                    it.copy(
                        phase = SpeedTestPhase.COMPLETE,
                        isRunning = false,
                        progress = 1f,
                    )
                }

                withContext(NonCancellable) {
                    saveToHistory()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        error = e.message ?: "Speed test failed",
                        isRunning = false,
                        phase = SpeedTestPhase.IDLE,
                    )
                }
            }
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        _state.update {
            it.copy(
                isRunning = false,
                phase = if (it.downloadMbps > 0f || it.uploadMbps > 0f) {
                    SpeedTestPhase.COMPLETE
                } else {
                    SpeedTestPhase.IDLE
                },
            )
        }
    }

    fun buildExportText(): String {
        val current = _state.value
        val sb = StringBuilder()
        sb.appendLine("Speed Test Results:")
        sb.appendLine("Download: %.1f Mbps".format(current.downloadMbps))
        sb.appendLine("Upload: %.1f Mbps".format(current.uploadMbps))
        sb.appendLine("Latency: %d ms".format(current.latencyMs))
        return sb.toString().trimEnd()
    }

    private suspend fun saveToHistory() {
        val current = _state.value
        if (current.downloadMbps <= 0f && current.uploadMbps <= 0f) return
        historyDao.insert(
            SpeedTestHistoryEntry(
                downloadMbps = current.downloadMbps,
                uploadMbps = current.uploadMbps,
                latencyMs = current.latencyMs,
                serverName = SERVER_NAME,
            ),
        )
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
    }

    companion object {
        private const val SERVER_NAME = "Cloudflare"

        /** Fraction of [SpeedTestEngine.MEASURE_WINDOW_MS] elapsed, clamped to 0f..1f for the progress gauge. */
        internal fun windowProgress(elapsedMs: Long): Float =
            (elapsedMs.toFloat() / SpeedTestEngine.MEASURE_WINDOW_MS).coerceIn(0f, 1f)
    }
}
