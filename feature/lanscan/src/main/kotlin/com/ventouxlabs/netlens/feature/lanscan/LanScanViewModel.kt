package com.ventouxlabs.netlens.feature.lanscan

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.LanScanHistoryDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.LanScanHistoryEntry
import com.ventouxlabs.netlens.core.network.NetworkInterfaceProvider
import com.ventouxlabs.netlens.core.network.calculateNetworkAddress
import com.ventouxlabs.netlens.feature.lanscan.engine.ArpTableReader
import com.ventouxlabs.netlens.feature.lanscan.engine.DeviceFingerprinter
import com.ventouxlabs.netlens.feature.lanscan.engine.LanMdnsScanner
import com.ventouxlabs.netlens.feature.lanscan.engine.NetBiosProber
import com.ventouxlabs.netlens.feature.lanscan.engine.SsdpScanner
import com.ventouxlabs.netlens.feature.lanscan.engine.SubnetScanner
import com.ventouxlabs.netlens.feature.lanscan.model.DeviceSortField
import com.ventouxlabs.netlens.feature.lanscan.model.DiscoveryMethod
import com.ventouxlabs.netlens.feature.lanscan.model.HostDetailState
import com.ventouxlabs.netlens.feature.lanscan.model.LanDevice
import com.ventouxlabs.netlens.feature.lanscan.model.LanScanHistoryUiModel
import com.ventouxlabs.netlens.feature.lanscan.model.LanScanTab
import com.ventouxlabs.netlens.feature.lanscan.model.LanScanUiState
import com.ventouxlabs.netlens.feature.lanscan.model.ScanRangeMode
import com.ventouxlabs.netlens.feature.lanscan.model.SuggestedNetwork
import com.ventouxlabs.netlens.feature.lanscan.model.HostPortResult
import com.ventouxlabs.netlens.feature.lanscan.model.HostScanExport
import com.ventouxlabs.netlens.feature.portscan.engine.PortScanner
import com.ventouxlabs.netlens.feature.portscan.model.PortRiskClassifier
import com.ventouxlabs.netlens.feature.portscan.model.WellKnownPorts
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
    private val knownDeviceDao: KnownDeviceDao,
    private val newDeviceNotifier: NewDeviceNotifier,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanScanUiState())
    val uiState: StateFlow<LanScanUiState> = _uiState.asStateFlow()

    private val _hostDetail = MutableStateFlow<HostDetailState?>(null)
    val hostDetail: StateFlow<HostDetailState?> = _hostDetail.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.IP)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _inventorySearchQuery = MutableStateFlow("")
    private val _inventorySortField = MutableStateFlow(DeviceSortField.LAST_SEEN)
    private val _inventorySortAscending = MutableStateFlow(false)

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
        viewModelScope.launch {
            combine(
                knownDeviceDao.getAllDevices(),
                _inventorySearchQuery,
                _inventorySortField,
                _inventorySortAscending,
            ) { allDevices, query, sortField, ascending ->
                val filtered = if (query.isBlank()) {
                    allDevices
                } else {
                    allDevices.filter { device ->
                        device.hostname?.contains(query, ignoreCase = true) == true ||
                            device.ip.contains(query, ignoreCase = true) ||
                            device.vendor?.contains(query, ignoreCase = true) == true ||
                            device.macAddress?.contains(query, ignoreCase = true) == true
                    }
                }
                val sorted = sortDevices(filtered, sortField, ascending)
                Triple(sorted, query, sortField to ascending)
            }.collect { (devices, query, sortPair) ->
                _uiState.update {
                    it.copy(
                        knownDevices = devices,
                        inventorySearchQuery = query,
                        inventorySortField = sortPair.first,
                        inventorySortAscending = sortPair.second,
                    )
                }
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
            persistScanResults(_uiState.value.devices)
        }
    }

    private suspend fun persistScanResults(devices: List<LanDevice>) {
        val now = System.currentTimeMillis()
        for (device in devices) {
            val mac = device.macAddress
            // Devices with no resolvable MAC (mDNS/SSDP-only, or absent from
            // /proc/net/arp) still get an inventory row, keyed by IP instead —
            // otherwise they'd never accumulate no matter how many times they're
            // seen. If a MAC later resolves for that IP, upgrade the row instead
            // of creating a duplicate.
            val existing = mac?.let { knownDeviceDao.getByMac(it) }
                ?: knownDeviceDao.getByIpWithoutMac(device.ip)
            if (existing != null) {
                if (mac != null && existing.macAddress == null) {
                    knownDeviceDao.setMacAddress(existing.id, mac)
                }
                knownDeviceDao.updateLastSeen(
                    id = existing.id,
                    hostname = device.hostname ?: existing.hostname,
                    ip = device.ip,
                    vendor = device.vendor ?: existing.vendor,
                    lastSeen = now,
                    deviceType = device.deviceType ?: existing.deviceType,
                    osGuess = device.osGuess ?: existing.osGuess,
                )
            } else {
                val entity = KnownDeviceEntity(
                    macAddress = mac,
                    hostname = device.hostname,
                    ip = device.ip,
                    vendor = device.vendor,
                    firstSeen = now,
                    lastSeen = now,
                    isKnown = false,
                    deviceType = device.deviceType,
                    osGuess = device.osGuess,
                )
                val insertResult = knownDeviceDao.insertIfNew(entity)
                if (insertResult != -1L) {
                    newDeviceNotifier.notify(entity.copy(id = insertResult))
                }
            }
        }
    }

    fun toggleKnown(id: Long) {
        viewModelScope.launch {
            val device = _uiState.value.knownDevices.find { it.id == id } ?: return@launch
            knownDeviceDao.setKnown(id, !device.isKnown)
        }
    }

    fun deleteDevice(id: Long) {
        viewModelScope.launch {
            knownDeviceDao.delete(id)
        }
    }

    fun clearInventory() {
        viewModelScope.launch {
            knownDeviceDao.deleteAll()
        }
    }

    fun setInventorySearchQuery(query: String) {
        _inventorySearchQuery.value = query
    }

    fun setInventorySortField(field: DeviceSortField) {
        _inventorySortField.value = field
    }

    fun toggleInventorySortOrder() {
        _inventorySortAscending.value = !_inventorySortAscending.value
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
        scanHostPorts(WellKnownPorts.TOP_1000_PORTS)
    }

    fun dismissDetail() {
        hostScanJob?.cancel()
        hostScanJob = null
        _hostDetail.value = null
        _uiState.update { it.copy(selectedDevice = null) }
    }

    fun scanHostPorts(ports: List<Int> = WellKnownPorts.TOP_1000_PORTS) {
        val detail = _hostDetail.value ?: return
        hostScanJob?.cancel()
        hostScanJob = viewModelScope.launch {
            _hostDetail.update {
                it?.copy(
                    isScanning = true,
                    progress = 0f,
                    portResults = emptyList(),
                    enrichedResults = emptyList(),
                    error = null,
                )
            }
            try {
                var scanned = 0
                val total = ports.size
                val rawBatch = mutableListOf<com.ventouxlabs.netlens.feature.portscan.model.PortResult>()
                val enrichedBatch = mutableListOf<HostPortResult>()
                portScanner.scan(detail.device.ip, ports).collect { result ->
                    scanned++
                    rawBatch.add(result)
                    enrichedBatch.add(
                        HostPortResult(
                            port = result.port,
                            serviceName = result.serviceName,
                            isOpen = result.isOpen,
                            latencyMs = result.latencyMs,
                            riskLevel = PortRiskClassifier.classifyRisk(result.port, result.isOpen),
                            description = WellKnownPorts.getDescription(result.port),
                        ),
                    )
                    if (rawBatch.size >= 50 || scanned == total) {
                        val raw = rawBatch.toList()
                        val enriched = enrichedBatch.toList()
                        rawBatch.clear()
                        enrichedBatch.clear()
                        _hostDetail.update { state ->
                            val updatedRaw = state?.portResults.orEmpty() + raw
                            val updatedEnriched = state?.enrichedResults.orEmpty() + enriched
                            state?.copy(
                                portResults = updatedRaw,
                                enrichedResults = updatedEnriched,
                                progress = scanned.toFloat() / total,
                                openCount = updatedRaw.count { it.isOpen },
                            )
                        }
                    }
                }
                val currentDetail = _hostDetail.value ?: return@launch
                val openPorts = currentDetail.portResults
                    .filter { it.isOpen }
                    .map { it.port }
                val fp = fingerprinter.fingerprintWithPorts(currentDetail.device, openPorts)
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

    fun buildHostScanJson(): String {
        val detail = _hostDetail.value ?: return ""
        val export = HostScanExport(
            host = detail.device.ip,
            hostname = detail.device.hostname,
            macAddress = detail.device.macAddress,
            vendor = detail.device.vendor,
            scanTimestamp = System.currentTimeMillis(),
            totalPortsScanned = detail.portResults.size,
            openPorts = detail.openCount,
            results = detail.enrichedResults.filter { it.isOpen },
        )
        return Json.encodeToString(export)
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

private fun sortDevices(
    devices: List<KnownDeviceEntity>,
    sortField: DeviceSortField,
    ascending: Boolean,
): List<KnownDeviceEntity> {
    val comparator: Comparator<KnownDeviceEntity> = when (sortField) {
        DeviceSortField.HOSTNAME -> compareBy(nullsLast()) { it.hostname?.lowercase() }
        DeviceSortField.IP -> compareBy {
            it.ip.split(".").fold(0L) { acc, part -> acc * 256 + (part.toLongOrNull() ?: 0L) }
        }
        DeviceSortField.VENDOR -> compareBy(nullsLast()) { it.vendor?.lowercase() }
        DeviceSortField.FIRST_SEEN -> compareBy { it.firstSeen }
        DeviceSortField.LAST_SEEN -> compareBy { it.lastSeen }
        DeviceSortField.MAC -> compareBy(nullsLast()) { it.macAddress }
    }
    return if (ascending) devices.sortedWith(comparator) else devices.sortedWith(comparator.reversed())
}
