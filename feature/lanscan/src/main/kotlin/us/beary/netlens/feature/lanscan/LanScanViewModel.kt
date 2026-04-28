package us.beary.netlens.feature.lanscan

import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import us.beary.netlens.core.network.NetworkInterfaceProvider
import us.beary.netlens.feature.lanscan.engine.SubnetScanner
import us.beary.netlens.feature.lanscan.model.LanScanUiState
import javax.inject.Inject

@HiltViewModel
class LanScanViewModel @Inject constructor(
    private val subnetScanner: SubnetScanner,
    private val networkInterfaceProvider: NetworkInterfaceProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanScanUiState())
    val uiState: StateFlow<LanScanUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (_uiState.value.isScanning) return

        val iface = networkInterfaceProvider.getActiveNetworkInterface()
        if (iface == null) {
            _uiState.update { it.copy(error = "No active network connection") }
            return
        }

        val subnet = iface.ip
        val prefixLength = iface.prefixLength
        val subnetInfo = "$subnet/$prefixLength"

        _uiState.update {
            it.copy(
                devices = emptyList(),
                isScanning = true,
                subnetInfo = subnetInfo,
                progress = 0f,
                error = null,
            )
        }

        scanJob = viewModelScope.launch {
            var deviceCount = 0
            subnetScanner.scan(subnet, prefixLength)
                .catch { e ->
                    _uiState.update { it.copy(error = e.message ?: "Scan failed") }
                }
                .onCompletion {
                    _uiState.update { it.copy(isScanning = false, progress = 1f) }
                }
                .collect { device ->
                    deviceCount++
                    _uiState.update { state ->
                        state.copy(
                            devices = state.devices + device,
                            progress = (deviceCount.toFloat() / MAX_EXPECTED_DEVICES)
                                .coerceAtMost(0.95f),
                        )
                    }
                }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    companion object {
        private const val MAX_EXPECTED_DEVICES = 254f
    }
}
