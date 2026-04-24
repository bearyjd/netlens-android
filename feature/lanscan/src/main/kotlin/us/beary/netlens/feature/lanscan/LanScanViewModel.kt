package us.beary.netlens.feature.lanscan

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
import us.beary.netlens.feature.lanscan.engine.DeviceFingerprinter
import us.beary.netlens.feature.lanscan.engine.LanMdnsScanner
import us.beary.netlens.feature.lanscan.engine.SubnetScanner
import us.beary.netlens.feature.lanscan.model.DiscoveryMethod
import us.beary.netlens.feature.lanscan.model.LanDevice
import us.beary.netlens.feature.lanscan.model.LanScanUiState
import javax.inject.Inject

enum class SortOrder { IP, LATENCY }

@HiltViewModel
class LanScanViewModel @Inject constructor(
    private val subnetScanner: SubnetScanner,
    private val mdnsScanner: LanMdnsScanner,
    private val fingerprinter: DeviceFingerprinter,
    @ApplicationContext private val context: Context,
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

    fun startScan() {
        if (scanJob?.isActive == true) return

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val linkProperties = connectivityManager.getLinkProperties(
            connectivityManager.activeNetwork,
        )

        if (linkProperties == null) {
            _uiState.update { it.copy(error = "No active network connection") }
            return
        }

        val linkAddress = linkProperties.linkAddresses
            .firstOrNull { it.isIpv4() }

        if (linkAddress == null) {
            _uiState.update { it.copy(error = "No IPv4 address found") }
            return
        }

        val subnet = linkAddress.address.hostAddress ?: run {
            _uiState.update { it.copy(error = "Could not determine IP address") }
            return
        }
        val prefixLength = linkAddress.prefixLength
        val subnetInfo = "$subnet/$prefixLength"
        val expectedHosts = ((1 shl (32 - prefixLength)) - 2).coerceIn(1, 1024).toFloat()

        _uiState.update {
            it.copy(
                devices = emptyList(),
                isScanning = true,
                subnetInfo = subnetInfo,
                progress = 0f,
                error = null,
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
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
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
