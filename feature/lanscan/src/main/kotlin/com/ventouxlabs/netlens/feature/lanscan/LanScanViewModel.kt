package com.ventouxlabs.netlens.feature.lanscan

import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.LanScanHistoryDao
import com.ventouxlabs.netlens.core.data.dao.LanScanInventoryDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceSearch
import com.ventouxlabs.netlens.core.data.model.LanScanHistoryEntry
import com.ventouxlabs.netlens.core.data.model.LanScanInventoryEntry
import com.ventouxlabs.netlens.core.network.DisplayText
import com.ventouxlabs.netlens.core.network.NetworkInterfaceProvider
import com.ventouxlabs.netlens.core.network.calculateNetworkAddress
import com.ventouxlabs.netlens.core.scan.engine.ArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.DeviceDiscoveryPipeline
import com.ventouxlabs.netlens.core.scan.engine.DeviceFingerprinter
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryEvent
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryMode
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryRequest
import com.ventouxlabs.netlens.core.scan.engine.DiscoveryStage
import com.ventouxlabs.netlens.core.scan.engine.PING_SWEEP_FAILED_MESSAGE
import com.ventouxlabs.netlens.core.scan.engine.LanNetworkBinder
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository
import com.ventouxlabs.netlens.feature.lanscan.model.EmptyScanReason
import com.ventouxlabs.netlens.feature.lanscan.model.DeviceSortField
import com.ventouxlabs.netlens.core.scan.network.NetworkContext
import com.ventouxlabs.netlens.core.scan.network.NetworkIdentity
import com.ventouxlabs.netlens.core.scan.network.NetworkSnapshot
import com.ventouxlabs.netlens.feature.lanscan.model.HostDetailState
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.feature.lanscan.model.LanScanHistoryUiModel
import com.ventouxlabs.netlens.feature.lanscan.model.LanScanTab
import com.ventouxlabs.netlens.feature.lanscan.model.LanScanUiState
import com.ventouxlabs.netlens.feature.lanscan.model.ScanRangeMode
import com.ventouxlabs.netlens.feature.lanscan.engine.ScanLocationProvider
import com.ventouxlabs.netlens.feature.lanscan.model.LocationStatus
import com.ventouxlabs.netlens.feature.lanscan.model.ScanCoordinates
import com.ventouxlabs.netlens.feature.lanscan.model.SuggestedNetwork
import com.ventouxlabs.netlens.feature.lanscan.model.toSnapshotDevice
import com.ventouxlabs.netlens.feature.lanscan.model.HostPortResult
import com.ventouxlabs.netlens.feature.lanscan.model.HostScanExport
import com.ventouxlabs.netlens.core.scan.engine.PortScanner
import com.ventouxlabs.netlens.core.scan.model.PortRiskClassifier
import com.ventouxlabs.netlens.core.scan.model.WellKnownPorts
import javax.inject.Inject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class SortOrder { IP, LATENCY }

@HiltViewModel
class LanScanViewModel @Inject constructor(
    private val discoveryPipeline: DeviceDiscoveryPipeline,
    private val arpTableReader: ArpTableReader,
    private val fingerprinter: DeviceFingerprinter,
    private val portScanner: PortScanner,
    private val lanNetworkBinder: LanNetworkBinder,
    private val networkInterfaceProvider: NetworkInterfaceProvider,
    private val lanScanHistoryDao: LanScanHistoryDao,
    private val knownDeviceDao: KnownDeviceDao,
    private val deviceInventoryRepository: DeviceInventoryRepository,
    private val scanLocationProvider: ScanLocationProvider,
    private val networkIdentity: NetworkIdentity,
    private val lanScanInventoryDao: LanScanInventoryDao? = null,
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
                    devicesJson = entry.devicesJson,
                    latitude = entry.latitude,
                    longitude = entry.longitude,
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
        lanScanInventoryDao?.let { inventoryDao ->
            viewModelScope.launch {
                inventoryDao.getAll().collect { inventories ->
                    _uiState.update { it.copy(savedInventories = inventories) }
                }
            }
        }
        viewModelScope.launch {
            combine(
                knownDeviceDao.getAllDevices(),
                _inventorySearchQuery,
                _inventorySortField,
                _inventorySortAscending,
            ) { allDevices, query, sortField, ascending ->
                // Shared with the Devices screen so one search box doesn't quietly cover
                // fewer fields than the other — this one now also reaches tags, notes,
                // location and custom names.
                val filtered = allDevices.filter { KnownDeviceSearch.matches(it, query) }
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

    fun startScanWithCidr(cidr: String, location: ScanCoordinates? = null) {
        if (parseCidr(cidr) == null) return
        _uiState.update {
            it.copy(
                rangeMode = ScanRangeMode.CUSTOM,
                customRange = cidr,
                selectedTab = LanScanTab.SCAN,
            )
        }
        startScan(location)
    }

    /**
     * Fills the range field from an externally supplied CIDR (the exported deep link) without
     * scanning: a sweep of an arbitrary range must be started by the user's own tap.
     */
    fun prefillCidr(cidr: String) {
        if (parseCidr(cidr) == null) return
        _uiState.update {
            it.copy(rangeMode = ScanRangeMode.CUSTOM, customRange = cidr.trim(), rangeError = null, selectedTab = LanScanTab.SCAN)
        }
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

    fun onManualLatitudeChanged(latitude: String) {
        _uiState.update { it.copy(manualLatitude = latitude) }
    }

    fun onManualLongitudeChanged(longitude: String) {
        _uiState.update { it.copy(manualLongitude = longitude) }
    }

    /**
     * Capture the device's position for tagging the next scan. Never fails: a missing fix is
     * [LocationStatus.UNAVAILABLE], which offers manual entry rather than surfacing an error.
     */
    fun captureLocation() {
        if (_uiState.value.locationStatus == LocationStatus.CAPTURING) return
        viewModelScope.launch {
            _uiState.update { it.copy(locationStatus = LocationStatus.CAPTURING) }
            // runCatching even though ScanLocationProvider's contract says it never throws: this
            // is optional metadata on a scan, and a future implementation breaking that contract
            // should degrade to "no location", not take down the ViewModel's scope.
            val fix = runCatching { scanLocationProvider.current() }.getOrNull()
            _uiState.update {
                if (fix == null) {
                    it.copy(locationStatus = LocationStatus.UNAVAILABLE, capturedLocation = null)
                } else {
                    it.copy(
                        locationStatus = LocationStatus.FOUND,
                        capturedLocation = fix,
                        showManualLocationEntry = false,
                    )
                }
            }
        }
    }

    fun clearCapturedLocation() {
        _uiState.update {
            it.copy(
                locationStatus = LocationStatus.IDLE,
                capturedLocation = null,
                showManualLocationEntry = false,
            )
        }
    }

    fun showManualLocationEntry() {
        _uiState.update { it.copy(showManualLocationEntry = true) }
    }

    /**
     * Coordinates to tag a scan with: a captured fix wins over anything typed. This lived in
     * `LanScanScreen` as `context.lastKnownScanCoordinates() ?: uiState.manualScanCoordinates()`;
     * keeping the precedence here means it is testable and the screen does not re-derive it.
     *
     * Manual values are range-checked, so a half-typed or nonsense entry contributes nothing
     * rather than tagging a scan with a bad position.
     */
    internal fun resolveScanCoordinates(): ScanCoordinates? {
        val state = _uiState.value
        state.capturedLocation?.let { return it }
        val latitude = state.manualLatitude.toDoubleOrNull() ?: return null
        val longitude = state.manualLongitude.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return ScanCoordinates(latitude, longitude)
    }

    fun startScan(location: ScanCoordinates? = null) {
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

        // Order is load-bearing: this drop, then the identity snapshot inside the LAN binding (which
        // re-reads ARP, so the cache holds the PRE-sweep table), then the sweep, then enrichment from
        // that cached table. A post-sweep ARP read would change what the identity resolver sees on
        // the first scan; that is a separate, deliberate follow-up, not part of this refactor.
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
                emptyScanReason = null,
            )
        }

        val scanStartedAt = System.currentTimeMillis()
        val customRange = _uiState.value.rangeMode == ScanRangeMode.CUSTOM
        // A custom range is attributed only when it is a non-VPN interface network (the chip list may
        // be stale) AND, checked against the live snapshot inside the binding, the LAN we are bound to.
        val scanCidr = if (customRange) normalizedCidr("$subnet/$prefixLength") else null
        val ownInterfaceNetwork = scanCidr == null ||
            _uiState.value.suggestedNetworks.any { !it.isVpn && normalizedCidr(it.cidr) == scanCidr }
        scanJob = viewModelScope.launch {
            // Three scanners discover hosts concurrently. Rather than copy + re-sort the whole
            // device list and emit a new _uiState on every single discovery (an O(n^2 log n)
            // storm over a /24 sweep), merges accumulate into this map and a periodic emitter
            // flushes one sorted snapshot at a bounded cadence, plus a final flush at completion.
            // Mirrors the batched port-scan emissions in scanHostPorts().
            val discovered = LinkedHashMap<String, LanDevice>()
            val discoveredLock = Mutex()

            suspend fun flushDiscovered() {
                val sorted = discoveredLock.withLock {
                    discovered.values.sortedWith(_sortOrder.value.comparator())
                }
                _uiState.update { state ->
                    state.copy(
                        devices = sorted,
                        deviceCount = sorted.size,
                        progress = (sorted.size.toFloat() / expectedHosts).coerceAtMost(0.95f),
                    )
                }
            }

            // Every engine below reaches the LAN through an unbound socket, which Android binds to
            // the process default — the VPN, whenever one is up. Pin the process to the Wi-Fi or
            // Ethernet network for the discovery phase so the probes actually leave over the LAN.
            // Without this a scan under any VPN, including on-device ad-blockers, silently returns
            // nothing (issue #152). Scope stays as tight as the network work: the DB writes below
            // stay outside it.
            var network = NetworkContext.NONE
            val boundToLanNetwork = lanNetworkBinder.withLanNetwork { bound ->
                // Read the network identity inside the binding too: outside it the default network is
                // the VPN, whose gateway would name the tunnel rather than the LAN the devices are on.
                val identityAtStart = if (ownInterfaceNetwork) {
                    networkIdentity.snapshot().takeIf { scanCidr == null || normalizedCidr(it.subnet) == scanCidr }
                } else {
                    null
                }
                coroutineScope {
                    val emitterJob = launch {
                        while (isActive) {
                            delay(DISCOVERY_EMIT_INTERVAL_MS)
                            flushDiscovered()
                        }
                    }

                    // Until the pipeline reaches enrichment, updates accumulate in `discovered` for the
                    // periodic flush; from then on each one is applied to the published list in place.
                    var enriching = false
                    discoveryPipeline.run(DiscoveryRequest("$subnet/$prefixLength", DiscoveryMode.Standard))
                        .collect { event ->
                            when (event) {
                                is DiscoveryEvent.DeviceUpdated -> {
                                    val updated = event.device
                                    if (!enriching) {
                                        discoveredLock.withLock { discovered[updated.ip] = updated }
                                    } else {
                                        // Replaces the whole device by IP: assumes nothing else edits `devices` during a scan.
                                        _uiState.update { state ->
                                            state.copy(
                                                devices = state.devices.map { if (it.ip == updated.ip) updated else it },
                                            )
                                        }
                                    }
                                }
                                is DiscoveryEvent.Progress -> if (event.stage == DiscoveryStage.Arp) {
                                    emitterJob.cancelAndJoin()
                                    flushDiscovered()
                                    enriching = true
                                }
                                is DiscoveryEvent.StageFailed -> _uiState.update {
                                    it.copy(error = event.message ?: PING_SWEEP_FAILED_MESSAGE)
                                }
                                is DiscoveryEvent.Finished -> Unit
                            }
                        }
                    emitterJob.cancelAndJoin()
                    if (!enriching) flushDiscovered()
                }
                network = scanNetwork(identityAtStart, _uiState.value.devices)
                bound
            }

            // An empty result only means "nothing is out there" if the probes had a way out. When
            // the binder found no LAN network to bind to, they did not, and reporting that as an
            // empty network is the ambiguity users hit in #152. Finding anything at all proves a
            // route existed, so the notice is never shown alongside results.
            _uiState.update { state ->
                state.copy(
                    isScanning = false,
                    progress = 1f,
                    emptyScanReason = if (!boundToLanNetwork && state.devices.isEmpty()) {
                        EmptyScanReason.NETWORK_UNREACHABLE
                    } else {
                        null
                    },
                )
            }
            saveToHistory(scanStartedAt, location)
            deviceInventoryRepository.persistScan(_uiState.value.devices, networkId = null, network = network)
        }
    }

    // [atStart] is null for a custom range that is not the LAN the process is bound to: only an
    // automatic scan, or a custom range equal to that live network (a tapped suggestion chip, or a
    // typed range that happens to be the same cidr, by design), is attributed to it. A VPN chip, another
    // interface's chip, a stale chip or any other range stays unattributed, and a scan during which
    // the phone moved networks is attributed to none.
    private suspend fun scanNetwork(atStart: NetworkSnapshot?, devices: List<LanDevice>): NetworkContext {
        if (atStart == null) return NetworkContext.NONE
        val atEnd = networkIdentity.snapshot()
        if (!atStart.sameNetworkAs(atEnd)) return NetworkContext.NONE
        return NetworkContext(atEnd.keys(devices), networkIdentity.currentNetworkLabel())
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

    private suspend fun saveToHistory(timestamp: Long, location: ScanCoordinates?) {
        val state = _uiState.value
        if (state.devices.isEmpty()) return
        lanScanHistoryDao.insert(
            LanScanHistoryEntry(
                timestamp = timestamp,
                ssid = null,
                subnet = state.subnetInfo,
                deviceCount = state.devices.size,
                devicesJson = Json.encodeToString(state.devices.map { it.toSnapshotDevice() }),
                latitude = location?.latitude,
                longitude = location?.longitude,
            ),
        )
    }

    fun saveEventToInventory(event: LanScanHistoryUiModel) {
        val inventoryDao = lanScanInventoryDao ?: return
        viewModelScope.launch {
            inventoryDao.insert(
                LanScanInventoryEntry(
                    name = "LAN scan ${event.subnet ?: "inventory"}",
                    sourceEventId = event.id,
                    capturedAt = event.timestamp,
                    subnet = event.subnet,
                    deviceCount = event.deviceCount,
                    devicesJson = event.devicesJson,
                    latitude = event.latitude,
                    longitude = event.longitude,
                ),
            )
        }
    }

    fun deleteSavedInventory(id: Long) {
        val inventoryDao = lanScanInventoryDao ?: return
        viewModelScope.launch { inventoryDao.deleteById(id) }
    }

    fun buildEventExportText(event: LanScanHistoryUiModel): String = buildSnapshotExportText(
        title = "LAN Scan Event",
        timestamp = event.timestamp,
        subnet = event.subnet,
        deviceCount = event.deviceCount,
        devicesJson = event.devicesJson,
        latitude = event.latitude,
        longitude = event.longitude,
    )

    fun buildInventoryExportText(inventory: LanScanInventoryEntry): String = buildSnapshotExportText(
        title = inventory.name,
        timestamp = inventory.capturedAt,
        subnet = inventory.subnet,
        deviceCount = inventory.deviceCount,
        devicesJson = inventory.devicesJson,
        latitude = inventory.latitude,
        longitude = inventory.longitude,
    )

    private fun buildSnapshotExportText(
        title: String,
        timestamp: Long,
        subnet: String?,
        deviceCount: Int,
        devicesJson: String,
        latitude: Double?,
        longitude: Double?,
    ): String = buildString {
        appendLine(title)
        appendLine("Captured: ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))}")
        appendLine("Subnet: ${subnet ?: "Unknown"}")
        appendLine("Devices: $deviceCount")
        if (latitude != null && longitude != null) appendLine("Location: $latitude, $longitude")
        val snapshots = runCatching { Json.decodeFromString<List<com.ventouxlabs.netlens.feature.lanscan.model.ScanSnapshotDevice>>(devicesJson) }.getOrNull()
        val legacyIps = if (snapshots == null) runCatching { Json.decodeFromString<List<String>>(devicesJson) }.getOrDefault(emptyList()) else emptyList()
        snapshots.orEmpty().forEach { device ->
            // Flattened: hostname/vendor are device-supplied, and a control character in one
            // splits this row in two. See DisplayText. Since 2026-08-17 the scan engines flatten
            // at ingestion too, but these calls are NOT redundant: rows persisted before that
            // change (scan history, known_devices until the next rescan overwrites them) still
            // hold raw values, and this export is the one sink where a newline forges rather
            // than wraps. Do not remove.
            val host = DisplayText.flatten(device.hostname)?.let { " ($it)" } ?: ""
            val mac = DisplayText.flatten(device.macAddress)?.let { "  MAC=$it" } ?: ""
            val vendor = DisplayText.flatten(device.vendor)?.let { "  Vendor=$it" } ?: ""
            val type = DisplayText.flatten(device.deviceType)?.let { "  Type=$it" } ?: ""
            val os = DisplayText.flatten(device.osGuess)?.let { "  OS=$it" } ?: ""
            val model = DisplayText.flatten(device.model)?.let { "  Model=$it" } ?: ""
            appendLine("${device.ip}$host$mac$vendor$type$os$model")
        }
        legacyIps.forEach(::appendLine)
    }.trimEnd()

    fun buildExportText(): String {
        val current = _uiState.value
        val sb = StringBuilder()
        sb.appendLine("LAN Scan results (${current.subnetInfo}):")
        sb.appendLine("Devices found: ${current.devices.size}")
        current.devices.forEach { d ->
            // Same reason as buildSnapshotExportText: these come from the device, not from us.
            val host = DisplayText.flatten(d.hostname)?.let { " ($it)" } ?: ""
            val mac = DisplayText.flatten(d.macAddress)?.let { "  MAC=$it" } ?: ""
            val vendor = DisplayText.flatten(d.vendor)?.let { "  Vendor=$it" } ?: ""
            val type = DisplayText.flatten(d.deviceType)?.let { "  Type=$it" } ?: ""
            val os = DisplayText.flatten(d.osGuess)?.let { "  OS=$it" } ?: ""
            val model = DisplayText.flatten(d.model)?.let { "  Model=$it" } ?: ""
            sb.appendLine("${d.ip}$host$mac$vendor$type$os$model  ${d.latencyMs}ms")
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
                val rawBatch = mutableListOf<com.ventouxlabs.netlens.core.scan.model.PortResult>()
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
                        enrichedTypeConfidence = fp.typeConfidence,
                        enrichedOsConfidence = fp.osConfidence,
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
        // Bounded cadence for flushing accumulated LAN-scan discoveries to the UI.
        private const val DISCOVERY_EMIT_INTERVAL_MS = 200L

        /** `a.b.c.d/p` with host bits cleared, or null when [cidr] is null or does not parse. */
        private fun normalizedCidr(cidr: String?): String? {
            val (ip, prefix) = parseCidr(cidr ?: return null) ?: return null
            return "${calculateNetworkAddress(ip, prefix)}/$prefix"
        }

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
        // Sorts on what the card actually shows, so a renamed device lands where the user
        // reads it rather than under the hostname the scanner happened to resolve.
        DeviceSortField.HOSTNAME -> compareBy(nullsLast()) {
            (it.customName ?: it.hostname)?.lowercase()
        }
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
