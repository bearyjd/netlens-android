package com.ventoux.netlens.feature.wifi

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
import com.ventoux.netlens.feature.wifi.engine.ChannelCalculator
import com.ventoux.netlens.feature.wifi.engine.WifiScanner
import com.ventoux.netlens.feature.wifi.model.WifiBand
import com.ventoux.netlens.feature.wifi.model.WifiNetwork
import com.ventoux.netlens.feature.wifi.model.WifiUiState
import javax.inject.Inject

@HiltViewModel
class WifiViewModel @Inject constructor(
    private val wifiScanner: WifiScanner,
) : ViewModel() {

    private val _state = MutableStateFlow(WifiUiState())
    val state: StateFlow<WifiUiState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (_state.value.isScanning) return
        if (!_state.value.permissionGranted) return

        _state.update {
            it.copy(
                isScanning = true,
                error = null,
            )
        }

        viewModelScope.launch {
            wifiScanner.observeConnected()
                .catch { /* ignore connected info errors */ }
                .collect { info ->
                    _state.update { it.copy(connectedInfo = info) }
                }
        }

        scanJob = viewModelScope.launch {
            wifiScanner.scan()
                .catch { e ->
                    _state.update {
                        it.copy(
                            isScanning = false,
                            error = e.message ?: "Scan failed",
                        )
                    }
                }
                .collect { networks ->
                    _state.update {
                        it.copy(
                            networks = networks.sortedByDescending { n -> n.level },
                            isScanning = false,
                            lastScanTimestamp = System.currentTimeMillis(),
                        )
                    }
                }
        }
    }

    fun onBandSelected(band: WifiBand) {
        _state.update { it.copy(selectedBand = band) }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted) }
        if (granted) {
            startScan()
        }
    }

    fun filteredNetworks(): List<WifiNetwork> {
        val state = _state.value
        return when (state.selectedBand) {
            WifiBand.ALL -> state.networks
            else -> state.networks.filter {
                ChannelCalculator.bandForFrequency(it.frequency) == state.selectedBand
            }
        }
    }

    fun buildExportText(): String {
        val current = _state.value
        val sb = StringBuilder()
        sb.appendLine("WiFi Analyzer Results:")

        current.connectedInfo?.let { info ->
            sb.appendLine("Connected: ${info.ssid} (${info.rssi}dBm, ${info.linkSpeedMbps}Mbps)")
        }

        sb.appendLine("Networks found: ${current.networks.size}")

        val grouped = current.networks.groupBy { ChannelCalculator.bandForFrequency(it.frequency) }

        grouped[WifiBand.TWO_GHZ]?.let { nets ->
            sb.appendLine("--- 2.4 GHz ---")
            nets.sortedByDescending { it.level }.forEach { n ->
                sb.appendLine("${n.ssid.ifEmpty { "(hidden)" }}  Ch ${n.channelNumber}  ${n.level}dBm  ${n.security}")
            }
        }

        grouped[WifiBand.FIVE_GHZ]?.let { nets ->
            sb.appendLine("--- 5 GHz ---")
            nets.sortedByDescending { it.level }.forEach { n ->
                sb.appendLine("${n.ssid.ifEmpty { "(hidden)" }}  Ch ${n.channelNumber}  ${n.level}dBm  ${n.security}")
            }
        }

        grouped[WifiBand.SIX_GHZ]?.let { nets ->
            sb.appendLine("--- 6 GHz ---")
            nets.sortedByDescending { it.level }.forEach { n ->
                sb.appendLine("${n.ssid.ifEmpty { "(hidden)" }}  Ch ${n.channelNumber}  ${n.level}dBm  ${n.security}")
            }
        }

        return sb.toString().trimEnd()
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
