package us.beary.netlens.feature.ping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import us.beary.netlens.feature.ping.engine.Pinger
import us.beary.netlens.feature.ping.model.PingSummary
import us.beary.netlens.feature.ping.model.PingUiState
import javax.inject.Inject

@HiltViewModel
class PingViewModel @Inject constructor(
    private val pinger: Pinger,
) : ViewModel() {

    private val _state = MutableStateFlow(PingUiState())
    val state: StateFlow<PingUiState> = _state.asStateFlow()

    private var pingJob: Job? = null

    fun onHostChange(host: String) {
        _state.update { it.copy(host = host) }
    }

    fun startPing(host: String, count: Int) {
        pingJob?.cancel()
        _state.update {
            it.copy(
                isPinging = true,
                results = emptyList(),
                summary = null,
                error = null,
            )
        }

        pingJob = viewModelScope.launch {
            pinger.ping(host, count)
                .catch { e ->
                    _state.update {
                        it.copy(
                            isPinging = false,
                            error = e.message ?: "Ping failed",
                        )
                    }
                }
                .onCompletion {
                    _state.update { current ->
                        current.copy(
                            isPinging = false,
                            summary = computeSummary(current),
                        )
                    }
                }
                .collect { result ->
                    _state.update { current ->
                        current.copy(results = current.results + result)
                    }
                }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        _state.update { current ->
            current.copy(
                isPinging = false,
                summary = computeSummary(current),
            )
        }
    }

    private fun computeSummary(current: PingUiState): PingSummary? {
        val results = current.results
        if (results.isEmpty()) return null

        val transmitted = results.size
        val received = results.count { !it.isTimeout }
        val latencies = results.mapNotNull { it.latencyMs }
        val lossPercent = if (transmitted > 0) {
            ((transmitted - received).toFloat() / transmitted) * 100f
        } else {
            0f
        }

        return PingSummary(
            transmitted = transmitted,
            received = received,
            lossPercent = lossPercent,
            minMs = latencies.minOrNull() ?: 0f,
            avgMs = if (latencies.isNotEmpty()) latencies.average().toFloat() else 0f,
            maxMs = latencies.maxOrNull() ?: 0f,
        )
    }
}
