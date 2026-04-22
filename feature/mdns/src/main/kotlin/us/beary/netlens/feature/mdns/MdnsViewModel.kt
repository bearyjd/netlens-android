package us.beary.netlens.feature.mdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import us.beary.netlens.feature.mdns.engine.MdnsScanner
import us.beary.netlens.feature.mdns.model.MdnsUiState
import javax.inject.Inject

@HiltViewModel
class MdnsViewModel @Inject constructor(
    private val mdnsScanner: MdnsScanner,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MdnsUiState())
    val uiState: StateFlow<MdnsUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (_uiState.value.isScanning) return

        _uiState.update {
            it.copy(
                services = emptyList(),
                isScanning = true,
                error = null,
            )
        }

        scanJob = viewModelScope.launch {
            coroutineScope {
                COMMON_SERVICE_TYPES.forEach { serviceType ->
                    launch {
                        mdnsScanner.discoverServices(serviceType)
                            .catch { e ->
                                _uiState.update { state ->
                                    state.copy(error = e.message ?: "Discovery failed")
                                }
                            }
                            .onCompletion {
                                // Individual type scan completed
                            }
                            .collect { service ->
                                _uiState.update { state ->
                                    val isDuplicate = state.services.any { existing ->
                                        existing.serviceName == service.serviceName &&
                                            existing.serviceType == service.serviceType
                                    }
                                    if (isDuplicate) {
                                        state
                                    } else {
                                        state.copy(services = state.services + service)
                                    }
                                }
                            }
                    }
                }
            }
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    companion object {
        private val COMMON_SERVICE_TYPES = listOf(
            "_http._tcp.",
            "_https._tcp.",
            "_workstation._tcp.",
            "_ssh._tcp.",
            "_smb._tcp.",
            "_printer._tcp.",
            "_ipp._tcp.",
            "_airplay._tcp.",
        )
    }
}
