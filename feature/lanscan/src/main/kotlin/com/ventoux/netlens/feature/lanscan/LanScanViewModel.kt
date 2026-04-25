package com.ventoux.netlens.feature.lanscan

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ventoux.netlens.core.data.dao.LanScanHistoryDao
import com.ventoux.netlens.core.data.model.LanScanHistoryEntry
import com.ventoux.netlens.feature.lanscan.engine.DeviceFingerprinter
import com.ventoux.netlens.feature.lanscan.engine.LanMdnsScanner
import com.ventoux.netlens.feature.lanscan.engine.SubnetScanner
import com.ventoux.netlens.feature.lanscan.model.DiscoveryMethod
import com.ventoux.netlens.feature.lanscan.model.LanDevice
import com.ventoux.netlens.feature.lanscan.model.LanScanUiState
import com.ventoux.netlens.feature.lanscan.model.ScanRangeMode
import javax.inject.Inject

enum class SortOrder { IP, LATENCY }

@HiltViewModel
class LanScanViewModel @Inject constructor(
    private val subnetScanner: SubnetScanner,
    private val mdnsScanner: LanMdnsScanner,
    private val fingerprinter: DeviceFingerprinter,
    @ApplicationContext private val context: Context,
    private val lanScanHistoryDao: LanScanHistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanScanUiState())
    val uiState: StateFlow<LanScanUiState> = _uiState.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.IP)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private var scanJob: Job? = null

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        _uiState.update { state ->
            state.copy(devices = state.devices.sortedWith(order.comparator()))
        }
    }

    fun onRangeModeChanged(mode: ScanRangeMode) {
        _uiState.update { it.copy(rangeMode = mode, rangeError = null, error = null) }
    }

    fun onCustomRangeChanged(range: String) {
        _uiState.update { it.copy(customRange = range, rangeError = null) }
    }

    fun startScan() {
        if (scanJob?.isActive == true) return

        val (subnet, prefixLength, subnetInfo) = when (_uiState.value.rangeMode) {
            ScanRangeMode.CUSTOM -> {
                val parsed = parseCidr(_uiState.value.customRange)
                if (parsed == null) {
                    _uiState.update {
                        it.copy(rangeError = context.getString(R.string.lanscan_error_invalid_cidr))
                    }
                    return
                }
                Triple(parsed.first, parsed.second, "${parsed.first}/${parsed.second}")
            }
            ScanRangeMode.AUTO -> {
                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val linkProperties = connectivityManager.getLinkProperties(
                    connectivityManager.activeNetwork,
                )
                if (linkProperties == null) {
                    _uiState.update { it.copy(error = "No active network connection") }
                    return
                }
                val linkAddress = linkProperties.linkAddresses.firstOrNull { it.isIpv4() }
                if (linkAddress == null) {
                    _uiState.update { it.copy(error = "No IPv4 address found") }
                    return
                }
                val ip = linkAddress.address.hostAddress ?: run {
                    _uiState.update { it.copy(error = "Could not determine IP address") }
                    return
                }
                Triple(ip, linkAddress.prefixLength, "$ip/${linkAddress.prefixLength}")
            }
        }
        val expectedHosts = ((1 shl (32 - prefixLength)) - 2).coerceIn(1, 1024).toFloat()

        _uiState.update {
            it.copy(
                devices = emptyList(),
                isScanning = true,
                subnetInfo = subnetInfo,
                progress = 0f,
                error = null,
                rangeError = null,
                deviceCount = 0,
            )
        }

        scanJob = viewModelScope.launch {
            fun mergeDevice(device: LanDevice) {
                val fingerprinted = fingerprinter.fingerprint(device)
                _uiState.update { state ->
                    val existing = state.devices.find { it.ip == fingerprinted.ip }
                    val merged = if (existing != null) {
                        existing.copy(
                            hostname = existing.hostname ?: fingerprinted.hostname,
                            discoveryMethod = DiscoveryMethod.BOTH,
                            services = (existing.services + fingerprinted.services).distinct(),
                            deviceType = existing.deviceType ?: fingerprinted.deviceType,
                            osGuess = existing.osGuess ?: fingerprinted.osGuess,
                            latencyMs = maxOf(existing.latencyMs, fingerprinted.latencyMs),
                        )
                    } else {
                        fingerprinted
                    }
                    val updatedDevices = if (existing != null) {
                        state.devices.map { if (it.ip == merged.ip) merged else it }
                    } else {
                        state.devices + merged
                    }.sortedWith(_sortOrder.value.comparator())
                    val newCount = updatedDevices.size
                    state.copy(
                        devices = updatedDevices,
                        deviceCount = newCount,
                        progress = (newCount.toFloat() / expectedHosts).coerceAtMost(0.95f),
                    )
                }
            }

            val pingJob = launch {
                subnetScanner.scan(subnet, prefixLength)
                    .catch { e ->
                        _uiState.update { it.copy(error = e.message ?: "Ping sweep failed") }
                    }
                    .collect { device -> mergeDevice(device) }
            }

            val mdnsJob = launch {
                mdnsScanner.discover()
                    .catch { /* mDNS failure is non-fatal */ }
                    .collect { device -> mergeDevice(device) }
            }

            pingJob.join()
            mdnsJob.join()
            _uiState.update { it.copy(isScanning = false, progress = 1f) }
            saveToHistory()
        }
    }

    private suspend fun saveToHistory() {
        val state = _uiState.value
        if (state.devices.isEmpty()) return
        lanScanHistoryDao.insert(
            LanScanHistoryEntry(
                ssid = null,
                subnet = state.subnetInfo,
                deviceCount = state.devices.size,
                devicesJson = Json.encodeToString(state.devices.map { it.ip }),
            ),
        )
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    companion object {
        internal fun parseCidr(cidr: String): Pair<String, Int>? {
            val trimmed = cidr.trim()
            val parts = trimmed.split("/")
            if (parts.size != 2) return null
            val ip = parts[0]
            val prefix = parts[1].toIntOrNull() ?: return null
            if (prefix < 16 || prefix > 30) return null
            val octets = ip.split(".")
            if (octets.size != 4) return null
            if (octets.any { o -> o.toIntOrNull()?.let { it in 0..255 } != true }) return null
            return ip to prefix
        }
    }

}

private fun SortOrder.comparator(): Comparator<LanDevice> = when (this) {
    SortOrder.IP -> compareBy { ipToLong(it.ip) }
    SortOrder.LATENCY -> compareBy { it.latencyMs }
}

private fun ipToLong(ip: String): Long {
    return ip.split(".").fold(0L) { acc, part -> acc * 256 + (part.toLongOrNull() ?: 0L) }
}

private fun LinkAddress.isIpv4(): Boolean {
    return address.address.size == 4
}
