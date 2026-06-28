package com.ventouxlabs.netlens.feature.portscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ventouxlabs.netlens.core.data.dao.PortScanHistoryDao
import com.ventouxlabs.netlens.core.data.model.PortScanHistoryEntry
import com.ventouxlabs.netlens.feature.portscan.engine.PortScanner
import com.ventouxlabs.netlens.feature.portscan.model.PortScanUiState
import javax.inject.Inject

@HiltViewModel
class PortScanViewModel @Inject constructor(
    private val portScanner: PortScanner,
    private val portScanHistoryDao: PortScanHistoryDao,
) : ViewModel() {

    private val _state = MutableStateFlow(PortScanUiState())
    val state: StateFlow<PortScanUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun onHostChanged(host: String) {
        _state.update { it.copy(host = host) }
    }

    fun buildExportText(): String {
        val current = _state.value
        val sb = StringBuilder()
        sb.appendLine("Port Scan results for ${current.host}:")
        val open = current.results.filter { it.isOpen }
        sb.appendLine("Open ports: ${open.size} / ${current.results.size} scanned")
        open.forEach { r ->
            sb.appendLine("  ${r.port}/${r.serviceName}  (${r.latencyMs}ms)")
        }
        return sb.toString().trimEnd()
    }

    fun scan(host: String, ports: List<Int>) {
        scanJob?.cancel()
        _state.update {
            PortScanUiState(
                host = host,
                isScanning = true,
                progress = 0f,
            )
        }

        val totalPorts = ports.size
        var scannedCount = 0
        val startTime = System.currentTimeMillis()

        scanJob = viewModelScope.launch {
            try {
                portScanner.scan(host, ports).collect { result ->
                    scannedCount++
                    _state.update { current ->
                        val updatedResults = current.results + result
                        current.copy(
                            results = updatedResults,
                            progress = scannedCount.toFloat() / totalPorts,
                            openCount = updatedResults.count { it.isOpen },
                        )
                    }
                }
                _state.update { it.copy(isScanning = false) }
                val currentState = _state.value
                if (currentState.results.isNotEmpty()) {
                    portScanHistoryDao.insert(
                        PortScanHistoryEntry(
                            host = host,
                            openPorts = Json.encodeToString(currentState.results.filter { it.isOpen }.map { it.port }),
                            totalScanned = currentState.results.size,
                            durationMs = System.currentTimeMillis() - startTime,
                        ),
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        isScanning = false,
                        error = e.message ?: "Scan failed",
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = false) }
    }
}
