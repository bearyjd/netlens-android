package com.ventoux.netlens.feature.mdns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ventoux.netlens.core.data.dao.MdnsHistoryDao
import com.ventoux.netlens.core.data.model.MdnsHistoryEntry
import com.ventoux.netlens.feature.mdns.engine.MdnsScanner
import com.ventoux.netlens.feature.mdns.model.MdnsUiState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class MdnsViewModel @Inject constructor(
    private val mdnsScanner: MdnsScanner,
    private val mdnsHistoryDao: MdnsHistoryDao,
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
            COMMON_SERVICE_TYPES.forEach { serviceType ->
                launch {
                    mdnsScanner.discoverServices(serviceType)
                        .catch { e ->
                            _uiState.update { state ->
                                state.copy(error = e.message ?: "Discovery failed")
                            }
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
    }

    fun buildExportText(): String {
        val current = _uiState.value
        val sb = StringBuilder()
        sb.appendLine("mDNS Browser:")
        sb.appendLine("Services found: ${current.services.size}")
        current.services.forEach { s ->
            sb.appendLine("${s.serviceName} (${s.serviceType})")
            s.host?.let { sb.appendLine("  Host: $it:${s.port}") }
            if (s.attributes.isNotEmpty()) {
                s.attributes.forEach { (k, v) -> sb.appendLine("  $k=$v") }
            }
        }
        return sb.toString().trimEnd()
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
        viewModelScope.launch { saveToHistory() }
    }

    private suspend fun saveToHistory() {
        val services = _uiState.value.services
        if (services.isEmpty()) return
        val servicesJson = Json.encodeToString(
            services.map { svc ->
                mapOf(
                    "name" to svc.serviceName,
                    "type" to svc.serviceType,
                    "host" to (svc.host ?: ""),
                    "port" to svc.port.toString(),
                )
            },
        )
        mdnsHistoryDao.insert(
            MdnsHistoryEntry(
                serviceCount = services.size,
                servicesJson = servicesJson,
            ),
        )
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
