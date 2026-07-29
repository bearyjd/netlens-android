package com.ventouxlabs.netlens.feature.portscan

import androidx.lifecycle.SavedStateHandle
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
import com.ventouxlabs.netlens.core.ui.UiText
import com.ventouxlabs.netlens.feature.portscan.engine.PortScanner
import com.ventouxlabs.netlens.feature.portscan.model.PortScanUiState
import javax.inject.Inject

@HiltViewModel
class PortScanViewModel @Inject constructor(
    private val portScanner: PortScanner,
    private val portScanHistoryDao: PortScanHistoryDao,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(PortScanUiState(host = savedState[KEY_HOST] ?: ""))
    val state: StateFlow<PortScanUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun onHostChanged(host: String) {
        // Mirrored into SavedStateHandle, not just the StateFlow: a ViewModel does not survive
        // process death, and the field used to be a rememberSaveable that did. Without this,
        // typing a host, getting reclaimed in the background and returning loses it.
        savedState[KEY_HOST] = host
        _state.update { it.copy(host = host) }
    }

    /**
     * Seeds the host from another tool's "scan this host" action.
     *
     * Applied at most once, and the "once" is itself saved state. The screen's `LaunchedEffect`
     * re-runs whenever the composition is recreated (a rotation, a fold) while the ViewModel
     * outlives it, so an unguarded write would throw away whatever had been typed. A plain field
     * would not be enough: after process death the nav back stack restores the original
     * `?query=` argument while the ViewModel is rebuilt fresh, so an in-memory flag would let the
     * stale argument overwrite the user's edit rather than merely lose it.
     */
    fun prefillHost(host: String) {
        if (savedState[KEY_PREFILLED] ?: false) return
        savedState[KEY_PREFILLED] = true
        onHostChanged(host)
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
        // The screen scans the trimmed host, so that is what must be restored, not the raw text.
        savedState[KEY_HOST] = host
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
                        error = UiText.of(e.message, R.string.portscan_error_scan_failed),
                    )
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = false) }
    }

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PREFILLED = "prefilled"
    }
}
