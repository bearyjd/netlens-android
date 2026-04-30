package com.ventoux.netlens.feature.traceroute

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
import com.ventoux.netlens.core.data.dao.TracerouteHistoryDao
import com.ventoux.netlens.core.data.model.TracerouteHistoryEntry
import com.ventoux.netlens.feature.traceroute.engine.Tracer
import com.ventoux.netlens.feature.traceroute.model.TracerouteUiState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class TracerouteViewModel @Inject constructor(
    private val tracer: Tracer,
    private val tracerouteHistoryDao: TracerouteHistoryDao,
) : ViewModel() {

    private val _state = MutableStateFlow(TracerouteUiState())
    val state: StateFlow<TracerouteUiState> = _state.asStateFlow()

    private var traceJob: Job? = null

    fun onHostChange(host: String) {
        _state.update { it.copy(host = host) }
    }

    fun startTrace(host: String) {
        traceJob?.cancel()
        _state.update {
            it.copy(
                isTracing = true,
                hops = emptyList(),
                error = null,
            )
        }

        traceJob = viewModelScope.launch {
            tracer.trace(host)
                .catch { e ->
                    _state.update {
                        it.copy(
                            isTracing = false,
                            error = e.message ?: "Traceroute failed",
                        )
                    }
                }
                .onCompletion {
                    _state.update { it.copy(isTracing = false) }
                    saveToHistory()
                }
                .collect { hop ->
                    _state.update { current ->
                        current.copy(hops = current.hops + hop)
                    }
                }
        }
    }

    fun stopTrace() {
        traceJob?.cancel()
        _state.update { it.copy(isTracing = false) }
    }

    private suspend fun saveToHistory() {
        val state = _state.value
        if (state.hops.isEmpty()) return
        val hopsJson = Json.encodeToString(
            state.hops.map { hop ->
                mapOf(
                    "hop" to hop.hopNumber.toString(),
                    "ip" to (hop.ip ?: "*"),
                    "rtt" to hop.rttMs.firstOrNull()?.toString().orEmpty(),
                )
            },
        )
        tracerouteHistoryDao.insert(
            TracerouteHistoryEntry(
                host = state.host,
                hopCount = state.hops.size,
                hopsJson = hopsJson,
            ),
        )
    }

    fun buildExportText(): String {
        val current = _state.value
        val sb = StringBuilder()
        sb.appendLine("Traceroute to ${current.host}:")
        current.hops.forEach { hop ->
            if (hop.isTimeout) {
                sb.appendLine("${hop.hopNumber}  *")
            } else {
                val rtt = hop.rttMs.firstOrNull()?.let { "%.1f ms".format(it) } ?: ""
                sb.appendLine("${hop.hopNumber}  ${hop.ip ?: "*"}  $rtt")
            }
        }
        return sb.toString().trimEnd()
    }
}
