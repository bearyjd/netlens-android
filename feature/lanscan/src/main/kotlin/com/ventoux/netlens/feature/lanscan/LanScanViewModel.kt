package com.ventoux.netlens.feature.lanscan

import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ventoux.netlens.core.data.dao.LanScanHistoryDao
import com.ventoux.netlens.core.data.model.LanScanHistoryEntry
import com.ventoux.netlens.core.network.NetworkInterfaceProvider
import com.ventoux.netlens.core.network.calculateNetworkAddress
import com.ventoux.netlens.feature.lanscan.engine.ArpTableReader
import com.ventoux.netlens.feature.lanscan.engine.DeviceFingerprinter
import com.ventoux.netlens.feature.lanscan.engine.LanMdnsScanner
import com.ventoux.netlens.feature.lanscan.engine.NetBiosProber
import com.ventoux.netlens.feature.lanscan.engine.SsdpScanner
import com.ventoux.netlens.feature.lanscan.engine.SubnetScanner
import com.ventoux.netlens.feature.lanscan.model.DiscoveryMethod
import com.ventoux.netlens.feature.lanscan.model.HostDetailState
import com.ventoux.netlens.feature.lanscan.model.LanDevice
import com.ventoux.netlens.feature.lanscan.model.LanScanHistoryUiModel
import com.ventoux.netlens.feature.lanscan.model.LanScanTab
import com.ventoux.netlens.feature.lanscan.model.LanScanUiState
import com.ventoux.netlens.feature.lanscan.model.ScanRangeMode
import com.ventoux.netlens.feature.lanscan.model.SuggestedNetwork
import com.ventoux.netlens.feature.portscan.engine.PortScanner
import com.ventoux.netlens.feature.portscan.model.WellKnownPorts
import javax.inject.Inject

enum class SortOrder { IP, LATENCY }

@HiltViewModel
class LanScanViewModel @Inject constructor(
    private val subnetScanner: SubnetScanner,
    private val mdnsScanner: LanMdnsScanner,
    private val fingerprinter: DeviceFingerprinter,
    private val portScanner: PortScanner,
    private val ssdpScanner: SsdpScanner,
    private val netBiosProber: NetBiosProber,
    private val arpTableReader: ArpTableReader,
    private val networkInterfaceProvider: NetworkInterfaceProvider,
    private val lanScanHistoryDao: LanScanHistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanScanUiState())
    val uiState: StateFlow<LanScanUiState> = _uiState.asStateFlow()

    private val _hostDetail = MutableStateFlow<HostDetailState?>(null)
    val hostDetail: StateFlow<HostDetailState?> = _hostDetail.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.IP)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private var scanJob: Job? = null
    private var hostScanJob: Job? = null

    private val historyEntries = lanScanHistoryDao.getRecent()
        .map { entries ->
            entries.map { entry ->
                LanScanHistoryUiModel(
                    id = entry.id,
                    timestamp = entry.timestamp,
                    subnet = entry.subnet,
                    deviceCount = entry.deviceCount,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshSuggestedNetworks()
        viewModelScope.launch {
            historyEntries.collect { entries ->
                _uiState.update { it.copy(historyEntries = entries) }
            }
        }
    }

    fun refreshSuggestedNetworks() {
        val suggestions = networkInterfaceProvider.getNetworkInterfaces().mapNotNull { iface ->
            val networkAddr = calculateNetworkAddress(iface.ip, iface.prefixLength)
            val cidr = "$networkAddr/${iface.prefixLength}"
            if (parseCidr(cidr) == null) return@mapNotNull null
            SuggestedNetwork(
                cidr = cidr,
                ip = iface.ip,
                prefixLength = iface.prefixLength,
                label = iface.label,
                isVpn = iface.isVpn,
            )
        }.sortedBy { it.isVpn }
        _uiState.update { it.copy(suggestedNetworks = suggestions) }
    }

    fun startScanWithCidr(cidr: String) {
        if (parseCidr(cidr) == null) return
        _uiState.update {
            it.copy(
                rangeMode = ScanRangeMode.CUSTOM,
                customRange = cidr,
                selectedTab = LanScanTab.SCAN,
            )
        }
        startScan()
    }

    fun selectTab(tab: LanScanTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == LanScanTab.SCAN) refreshSuggestedNetworks()
    }

    fun clearHistory() {
        viewModelScope.launch { lanScanHistoryDao.deleteAll() }
    }

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
                    _uiState.update { it.copy(rangeError = "Invalid CIDR notation") }
                    return
                }
                Triple(parsed.first, parsed.second, "${parsed.first}/${parsed.second}")
            }
            ScanRangeMode.AUTO -> {
                val iface = networkInterfaceProvider.getActiveNetworkInterface()
                if (iface == null) {
                    _uiState.update { it.copy(error = "No active network connection") }
                    return
                }
                Triple(iface.ip, iface.prefixLength, "${iface.ip}/${iface.prefixLength}")
            }
        }
        val expectedHosts = ((1 shl (32 - prefixLength)) - 2).coerceIn(1, 1024).toFloat()

        arpTableReader.invalidateCache()
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
            suspend fun mergeDevice(device: LanDevice) {
                val fingerprinted = fingerprinter.fingerprint(device)
                _uiState.update { state ->
                    val existing = state.devices.find { it.ip == fingerprinted.ip }
                    val merged = if (existing != null) {
                        existing.copy(
                            hostname = existing.hostname ?: fingerprinted.hostname,
                            discoveryMethod = DiscoveryMethod.MULTIPLE,
                            services = (existing.services + fingerprinted.services).distinct(),
                            deviceType = existing.deviceType ?: fingerprinted.deviceType,
                            osGuess = existing.osGuess ?: fingerprinted.osGuess,
                            latencyMs = maxOf(existing.latencyMs, fingerprinted.latencyMs),
                            macAddress = existing.macAddress ?: fingerprinted.macAddress,
                            vendor = existing.vendor ?: fingerprinted.vendor,
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
                    .catch { e -> Log.d("LanScan", "mDNS discovery failed: ${e.message}") }
                    .collect { device -> mergeDevice(device) }
            }

            val ssdpJob = launch {
                ssdpScanner.discover()
                    .catch { e -> Log.d("LanScan", "SSDP discovery failed: ${e.message}") }
                    .collect { ssdpDevice ->
                        val (type, os) = fingerprinter.classifyFromSsdp(ssdpDevice)
                        val device = LanDevice(
                            ip = ssdpDevice.ip,
                            hostname = ssdpDevice.friendlyName,
                            isReachable = true,
                            discoveryMethod = DiscoveryMethod.SSDP,
                            deviceType = type,
                            osGuess = os,
                            vendor = ssdpDevice.manufacturer,
                        )
                        mergeDevice(device)
                    }
            }

            pingJob.join()
            mdnsJob.join()
            ssdpJob.join()

            enrichWithArpAndNetBios()

            _uiState.update { it.copy(isScanning = false, progress = 1f) }
            saveToHistory()
        }
    }

    private suspend fun enrichWithArpAndNetBios() {
        val arpTable = arpTableReader.getAll()

        for (device in _uiState.value.devices) {
            val mac = device.macAddress ?: arpTable[device.ip]
            if (mac != null && device.macAddress == null) {
                val enriched = fingerprinter.fingerprint(device.copy(macAddress = mac))
                _uiState.update { state ->
                    state.copy(
                        devices = state.devices.map {
                            if (it.ip == enriched.ip) it.copy(
                                macAddress = enriched.macAddress,
                                vendor = it.vendor ?: enriched.vendor,
                            ) else it
                        },
                    )
                }
            }
        }

        kotlinx.coroutines.coroutineScope {
            _uiState.value.devices.filter { it.hostname == null && it.osGuess == null }.forEach { device ->
                launch {
                    val nbInfo = netBiosProber.probe(device.ip) ?: return@launch
                    val os = fingerprinter.classifyFromNetBios(nbInfo)
                    _uiState.update { state ->
                        state.copy(
                            devices = state.devices.map {
                                if (it.ip == device.ip) it.copy(
                                    hostname = it.hostname ?: nbInfo.name,
                                    osGuess = it.osGuess ?: os,
                                ) else it
                            },
                        )
                    }
                }
            }
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

    fun buildExportText(): String {
        val current = _uiState.value
        val sb = StringBuilder()
        sb.appendLine("LAN Scan results (${current.subnetInfo}):")
        sb.appendLine("Devices found: ${current.devices.size}")
        current.devices.forEach { d ->
            val host = d.hostname?.let { " ($it)" } ?: ""
            val mac = d.macAddress?.let { "  MAC=$it" } ?: ""
            val vendor = d.vendor?.let { "  Vendor=$it" } ?: ""
            sb.appendLine("${d.ip}$host$mac$vendor  ${d.latencyMs}ms")
        }
        return sb.toString().trimEnd()
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    fun selectDevice(device: LanDevice) {
        _hostDetail.value = HostDetailState(device = device)
        _uiState.update { it.copy(selectedDevice = device) }
    }

    fun dismissDetail() {
        hostScanJob?.cancel()
        hostScanJob = null
        _hostDetail.value = null
        _uiState.update { it.copy(selectedDevice = null) }
    }

    fun scanHostPorts(ports: List<Int> = WellKnownPorts.COMMON_PORTS.keys.sorted()) {
        val detail = _hostDetail.value ?: return
        hostScanJob?.cancel()
        hostScanJob = viewModelScope.launch {
            _hostDetail.update {
                it?.copy(isScanning = true, progress = 0f, portResults = emptyList(), error = null)
            }
            try {
                var scanned = 0
                val total = ports.size
                portScanner.scan(detail.device.ip, ports).collect { result ->
                    scanned++
                    _hostDetail.update { state ->
                        val updated = state?.portResults.orEmpty() + result
                        state?.copy(
                            portResults = updated,
                            progress = scanned.toFloat() / total,
                            openCount = updated.count { it.isOpen },
                        )
                    }
                }
                val openPorts = _hostDetail.value?.portResults
                    ?.filter { it.isOpen }
                    ?.map { it.port }
                    .orEmpty()
                val fp = fingerprinter.fingerprintWithPorts(detail.device, openPorts)
                _hostDetail.update {
                    it?.copy(
                        isScanning = false,
                        progress = 1f,
                        enrichedType = fp.deviceType,
                        enrichedOs = fp.osGuess,
                        fingerprintEvidence = fp.evidence,
                    )
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _hostDetail.update { it?.copy(isScanning = false, error = e.message ?: "Port scan failed") }
            }
        }
    }

    fun cancelHostScan() {
        hostScanJob?.cancel()
        hostScanJob = null
        _hostDetail.update { it?.copy(isScanning = false) }
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

