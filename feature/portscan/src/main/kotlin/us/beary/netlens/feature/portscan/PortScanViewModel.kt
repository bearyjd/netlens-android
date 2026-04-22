package us.beary.netlens.feature.portscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import us.beary.netlens.feature.portscan.engine.PortScanner
import us.beary.netlens.feature.portscan.model.PortScanUiState
import javax.inject.Inject

@HiltViewModel
class PortScanViewModel @Inject constructor(
    private val portScanner: PortScanner,
) : ViewModel() {

    private val _state = MutableStateFlow(PortScanUiState())
    val state: StateFlow<PortScanUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

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
