package com.ventoux.netlens.feature.traceroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ventoux.netlens.core.data.dao.TracerouteHistoryDao
import com.ventoux.netlens.core.data.model.TracerouteHistoryEntry
import com.ventoux.netlens.feature.traceroute.engine.AnomalyDetector
import com.ventoux.netlens.feature.traceroute.engine.HopGeolocator
import com.ventoux.netlens.feature.traceroute.engine.Tracer
import com.ventoux.netlens.feature.traceroute.model.TracerouteUiState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class TracerouteViewModel @Inject constructor(
    private val tracer: Tracer,
    private val tracerouteHistoryDao: TracerouteHistoryDao,
    private val hopGeolocator: HopGeolocator,
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
                isGeoLoading = false,
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
                .onCompletion { cause ->
                    _state.update { it.copy(isTracing = false) }
                    if (cause == null) {
                        enrichWithGeolocation()
                        saveToHistory()
                    }
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

    private suspend fun enrichWithGeolocation() {
        val hops = _state.value.hops
        if (hops.isEmpty()) return

        _state.update { it.copy(isGeoLoading = true) }

        try {
            val ips = hops.map { it.ip }
            val locations = hopGeolocator.lookupAll(ips)

            val enrichedHops = hops.map { hop ->
                val loc = hop.ip?.let { locations[it] }
                hop.copy(location = loc)
            }

            val anomalies = AnomalyDetector.detect(enrichedHops)
            val finalHops = enrichedHops.mapIndexed { index, hop ->
                hop.copy(anomalies = anomalies[index])
            }

            _state.update { it.copy(hops = finalHops, isGeoLoading = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(isGeoLoading = false) }
        }
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
                val loc = hop.location?.let { l ->
                    val parts = listOfNotNull(
                        l.city.ifEmpty { null },
                        l.country.ifEmpty { null },
                    )
                    if (parts.isNotEmpty()) " [${parts.joinToString(", ")}]" else ""
                } ?: ""
                val anomaly = if (hop.anomalies.isNotEmpty()) {
                    " ⚠ " + hop.anomalies.joinToString(", ") { it.name }
                } else ""
                sb.appendLine("${hop.hopNumber}  ${hop.ip ?: "*"}  $rtt$loc$anomaly")
            }
        }
        return sb.toString().trimEnd()
    }
}
