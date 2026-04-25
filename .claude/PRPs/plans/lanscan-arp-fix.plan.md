# Plan: LAN Scan — Replace /proc/net/arp with Ping Sweep + mDNS

## Summary
Replace the broken `/proc/net/arp` ARP table reader with a dual-strategy LAN discovery approach: parallel ICMP ping sweep (with semaphore-bounded concurrency) and NsdManager mDNS service discovery running concurrently. Results are merged by IP. MAC address and vendor fields become dormant (always null on Android 10+ without root).

## User Story
As a NetLens user on Android 10+,
I want LAN scan to discover devices on my network without requiring root,
So that I can see all reachable hosts, their hostnames, and advertised services.

## Problem → Solution
`/proc/net/arp` is blocked on Android 10+ (`EACCES`), breaking device discovery entirely → Replace with two complementary non-root approaches (ICMP ping + mDNS) that together cover devices regardless of whether they respond to ping or advertise services.

## Metadata
- **Complexity**: Medium
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: ~8 modified, ~2 created, ~1 deleted
- **Branch**: `fix/lanscan-arp`
- **PR Title**: `fix: replace /proc/net/arp with NsdManager + parallel ping sweep for LAN discovery`

---

## UX Design

### Before
```
┌─────────────────────────────────────┐
│ LAN Scan                        [↕] │
├─────────────────────────────────────┤
│ Subnet: 192.168.1.204/24  4 devices│
│ ─────────────────────────────────── │
│ ● 192.168.1.1                      │
│   AA:BB:CC:DD:EE:FF                │
│   gateway.local        [Router] [TP│
│                                     │
│ ● 192.168.1.50                     │
│   11:22:33:44:55:66                │
│   laptop.local      [Computer][Dell│
│                                     │
│ (devices show MAC + vendor chips)   │
│ (scan fails with EACCES on API 29+)│
└─────────────────────────────────────┘
```

### After
```
┌─────────────────────────────────────┐
│ LAN Scan                        [↕] │
├─────────────────────────────────────┤
│ Subnet: 192.168.1.204/24           │
│ Found 6 devices...          [PING] │
│ ─────────────────────────────────── │
│ ● 192.168.1.1                      │
│   gateway.local                    │
│   MAC: —  (Not available)          │
│            [Router] [PING+mDNS]    │
│   [http] [ssh]                     │
│                                     │
│ ● 192.168.1.50                     │
│   laptop.local                     │
│   MAC: —  (Not available)          │
│            [Computer] [PING]       │
│                                     │
│ ● 192.168.1.80                     │
│   printer.local                    │
│   MAC: —  (Not available)          │
│            [Printer] [mDNS]        │
│   [ipp] [http]                     │
└─────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| MAC address | Displayed as monospace text | Shows "—" with "(Not available)" caption | MAC always null on Android 10+ |
| Vendor chip | Shown from OUI lookup | Hidden (no MAC → no vendor) | Keep field dormant |
| Discovery method | Not shown | Tag chip: `PING` / `mDNS` / `PING+mDNS` | New visual indicator |
| Services | Not shown | Chips per mDNS service type: `http`, `ssh`, etc. | New row below device info |
| Progress text | "N devices" chip only | "Found N devices..." live counter | More informative during scan |
| Sort options | IP, Vendor, Latency | IP, Latency (Vendor sort hidden — always null) | Vendor sort is useless without MAC |
| Copy MAC button | Visible when MAC present | Hidden (MAC always null) | No data to copy |

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `feature/lanscan/src/main/kotlin/.../engine/SubnetScannerImpl.kt` | all | Code being replaced — reuse `calculateIpRange`, `resolveHostname`, `pingBatch` |
| P0 | `feature/mdns/src/main/kotlin/.../engine/MdnsScannerImpl.kt` | 28-80 | Reference pattern for NsdManager callbackFlow + serial resolve queue |
| P0 | `feature/lanscan/src/main/kotlin/.../model/LanDevice.kt` | all | Model being modified |
| P0 | `feature/lanscan/src/main/kotlin/.../LanScanScreen.kt` | 218-334 | DeviceCard being updated |
| P1 | `feature/lanscan/src/main/kotlin/.../LanScanViewModel.kt` | all | Scan orchestration |
| P1 | `feature/lanscan/src/main/kotlin/.../engine/ArpTableReader.kt` | all | File to delete |
| P1 | `feature/lanscan/src/main/kotlin/.../di/LanScanModule.kt` | all | DI bindings — may need new binding |
| P2 | `feature/lanscan/src/main/kotlin/.../engine/DeviceFingerprinter.kt` | all | Works with hostname — no changes needed |
| P2 | `feature/lanscan/src/main/kotlin/.../model/LanScanUiState.kt` | all | May need live count field |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| NsdManager | Android SDK docs | `discoverServices()` requires serial resolve on API < 34; use `Channel` queue |
| InetAddress.isReachable | Android SDK docs | Uses ICMP if root, falls back to TCP echo port 7; needs `INTERNET` permission |
| Semaphore | kotlinx.coroutines | `Semaphore(permits)` + `withPermit {}` for bounded concurrency |

---

## Patterns to Mirror

### CALLBACKFLOW_NSD
```kotlin
// SOURCE: feature/mdns/src/main/kotlin/.../engine/MdnsScannerImpl.kt:28-80
override fun discoverServices(serviceType: String): Flow<MdnsService> = callbackFlow {
    val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    val discoveryListener = object : NsdManager.DiscoveryListener { ... }
    launch {
        for (service in resolveQueue) {
            val resolved = resolveServiceSuspending(service)
            resolved?.let { trySend(it) }
        }
    }
    nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    awaitClose {
        resolveQueue.close()
        stopDiscoveryQuietly(discoveryListener)
    }
}
```

### RESOLVE_SUSPENDING
```kotlin
// SOURCE: feature/mdns/src/main/kotlin/.../engine/MdnsScannerImpl.kt:86-124
private suspend fun resolveServiceSuspending(serviceInfo: NsdServiceInfo): MdnsService? =
    suspendCancellableCoroutine { cont ->
        try {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    if (cont.isActive) cont.resume(/* mapped result */)
                }
            })
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (cont.isActive) cont.resume(null) }
    }
```

### PING_BATCH_COROUTINE
```kotlin
// SOURCE: feature/lanscan/src/main/kotlin/.../engine/SubnetScannerImpl.kt:64-74
private suspend fun pingBatch(ips: List<String>): Set<String> = coroutineScope {
    val results = ips.map { ip ->
        async {
            val reachable = runCatching {
                InetAddress.getByName(ip).isReachable(PING_TIMEOUT_MS)
            }.getOrDefault(false)
            if (reachable) ip else null
        }
    }
    results.mapNotNull { it.await() }.toSet()
}
```

### FLOW_ON_IO
```kotlin
// SOURCE: feature/lanscan/src/main/kotlin/.../engine/SubnetScannerImpl.kt:19-62
override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow {
    // ...
}.flowOn(Dispatchers.IO)
```

### HILT_BINDS
```kotlin
// SOURCE: feature/lanscan/src/main/kotlin/.../di/LanScanModule.kt:11-20
@Module
@InstallIn(SingletonComponent::class)
abstract class LanScanModule {
    @Binds @Singleton
    abstract fun bindSubnetScanner(impl: SubnetScannerImpl): SubnetScanner
}
```

### VIEWMODEL_STATE_UPDATE
```kotlin
// SOURCE: feature/lanscan/src/main/kotlin/.../LanScanViewModel.kt:86-107
scanJob = viewModelScope.launch {
    var deviceCount = 0
    subnetScanner.scan(subnet, prefixLength)
        .catch { e -> _uiState.update { it.copy(error = e.message ?: "Scan failed") } }
        .onCompletion { _uiState.update { it.copy(isScanning = false, progress = 1f) } }
        .collect { device ->
            deviceCount++
            _uiState.update { state ->
                val updatedDevices = (state.devices + device).sortedWith(_sortOrder.value.comparator())
                state.copy(devices = updatedDevices, progress = ...)
            }
        }
}
```

### TEST_STRUCTURE
```kotlin
// SOURCE: feature/ping/src/test/.../PingViewModelTest.kt (pattern)
class FakePinger : Pinger {
    var results: List<PingResult> = emptyList()
    var error: Throwable? = null
    override fun ping(host: String, count: Int): Flow<PingResult> = flow {
        error?.let { throw it }
        results.forEach { emit(it) }
    }
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `feature/lanscan/src/main/kotlin/.../model/LanDevice.kt` | UPDATE | Add `discoveryMethod`, `services` fields |
| `feature/lanscan/src/main/kotlin/.../model/DiscoveryMethod.kt` | CREATE | New enum: `PING`, `MDNS`, `BOTH` |
| `feature/lanscan/src/main/kotlin/.../engine/SubnetScannerImpl.kt` | UPDATE | Replace ARP reads with Semaphore-bounded ping + mDNS merge |
| `feature/lanscan/src/main/kotlin/.../engine/ArpTableReader.kt` | DELETE | `/proc/net/arp` is blocked on Android 10+ |
| `feature/lanscan/src/main/kotlin/.../LanScanScreen.kt` | UPDATE | Demote MAC, add discovery method tag, service chips |
| `feature/lanscan/src/main/kotlin/.../LanScanViewModel.kt` | UPDATE | Remove VENDOR sort option (no data), minor progress tweak |
| `feature/lanscan/src/main/kotlin/.../model/LanScanUiState.kt` | UPDATE | Add `deviceCount` for live progress text |
| `feature/lanscan/src/main/res/values/strings.xml` | UPDATE | Add new string resources for discovery method, MAC unavailable |
| `feature/lanscan/src/test/.../engine/SubnetScannerImplTest.kt` | CREATE | Unit tests for refactored scanner |
| `feature/lanscan/src/test/.../engine/FakeSubnetScanner.kt` | CREATE | Test fake for SubnetScanner |

## NOT Building

- MAC address recovery via alternative methods (requires root)
- OUI/vendor lookup revival (no MAC to look up)
- Background scanning or scheduling
- Scan history persistence (separate feature: scan-history)
- New permissions or manifest changes
- Changes to mDNS feature module (LAN scan gets its own NSD integration)
- Device detail screen or expanded device info view

---

## Step-by-Step Tasks

### Task 1: Create DiscoveryMethod enum
- **ACTION**: Create new file `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/model/DiscoveryMethod.kt`
- **IMPLEMENT**:
  ```kotlin
  package com.ventoux.netlens.feature.lanscan.model

  enum class DiscoveryMethod { PING, MDNS, BOTH }
  ```
- **MIRROR**: Follow existing enum style (see `SortOrder` in LanScanViewModel.kt:23)
- **IMPORTS**: None needed
- **GOTCHA**: None
- **VALIDATE**: File compiles, enum has 3 entries

### Task 2: Update LanDevice model
- **ACTION**: Modify `feature/lanscan/src/main/kotlin/.../model/LanDevice.kt` to add `discoveryMethod` and `services`
- **IMPLEMENT**:
  ```kotlin
  data class LanDevice(
      val ip: String,
      val mac: String? = null,
      val vendor: String? = null,
      val hostname: String? = null,
      val isReachable: Boolean = true,
      val latencyMs: Long = 0,
      val deviceType: String? = null,
      val osGuess: String? = null,
      val discoveryMethod: DiscoveryMethod = DiscoveryMethod.PING,
      val services: List<String> = emptyList(),
  )
  ```
- **MIRROR**: Existing data class style with defaults
- **IMPORTS**: `com.ventoux.netlens.feature.lanscan.model.DiscoveryMethod`
- **GOTCHA**: All existing construction sites pass no `discoveryMethod` — defaults to `PING` which is correct for existing ping-only code paths. No breakage.
- **VALIDATE**: Module compiles without errors

### Task 3: Rewrite SubnetScannerImpl — ping sweep with Semaphore
- **ACTION**: Rewrite `SubnetScannerImpl.scan()` to use `Semaphore(64)` for bounded parallel ping, remove all ARP table reads
- **IMPLEMENT**:
  - Remove `ArpTableReader.read()` calls (lines 26, 46)
  - Remove the "unreachable devices from ARP" loop (lines 44-60)
  - Replace `pingBatch()` chunked approach with full-range `Semaphore`-bounded parallel async:
    ```kotlin
    override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow {
        val ipAddresses = calculateIpRange(subnet, prefixLength)
        val totalIps = ipAddresses.size

        // Phase 1: Parallel ping sweep with bounded concurrency
        val reachableDevices = pingSweep(ipAddresses)

        // Phase 2: mDNS discovery (already completed concurrently via merge in ViewModel)
        // Emit ping results
        for (device in reachableDevices) {
            emit(device)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun pingSweep(ips: List<String>): List<LanDevice> = coroutineScope {
        val semaphore = Semaphore(64)
        ips.map { ip ->
            async {
                semaphore.withPermit {
                    val startTime = System.currentTimeMillis()
                    val reachable = withTimeoutOrNull(PING_TIMEOUT_MS.toLong()) {
                        runCatching { InetAddress.getByName(ip).isReachable(PING_TIMEOUT_MS) }
                            .getOrDefault(false)
                    } ?: false
                    if (reachable) {
                        val latency = System.currentTimeMillis() - startTime
                        val hostname = resolveHostname(ip)
                        val device = LanDevice(
                            ip = ip,
                            hostname = hostname,
                            isReachable = true,
                            latencyMs = latency,
                            discoveryMethod = DiscoveryMethod.PING,
                        )
                        fingerprinter.fingerprint(device)
                    } else null
                }
            }
        }.awaitAll().filterNotNull()
    }
    ```
  - Keep `resolveHostname()` and `calculateIpRange()` unchanged
  - Remove `OuiLookup` from constructor (no MAC → no OUI lookup)
- **MIRROR**: PING_BATCH_COROUTINE, FLOW_ON_IO
- **IMPORTS**: Add `kotlinx.coroutines.sync.Semaphore`, `kotlinx.coroutines.sync.withPermit`, `kotlinx.coroutines.withTimeoutOrNull`; remove `com.ventoux.netlens.core.oui.OuiLookup`
- **GOTCHA**: Don't emit from inside `coroutineScope` — collect results, return list, emit from outer `flow {}`. Using `Semaphore(64)` prevents ANR from 254 simultaneous coroutines. `withTimeoutOrNull` wraps `isReachable` for double safety.
- **VALIDATE**: Module compiles. No references to `ArpTableReader`. No import of `OuiLookup`.

### Task 4: Add mDNS discovery to SubnetScannerImpl
- **ACTION**: Add a new `interface LanMdnsScanner` and `LanMdnsScannerImpl` that wraps NsdManager for LAN-scan-specific mDNS discovery, producing `LanDevice` objects
- **IMPLEMENT**: Create `feature/lanscan/src/main/kotlin/.../engine/LanMdnsScanner.kt`:
  ```kotlin
  interface LanMdnsScanner {
      fun discover(timeoutMs: Long = 5000): Flow<LanDevice>
  }
  ```
  Modify `SubnetScannerImpl` to NOT do mDNS internally. Instead, the ViewModel will merge the two flows. This keeps the scanner interface clean and testable.

  Actually — better approach: keep `SubnetScanner` as ping-only, create `LanMdnsScanner` as a separate interface, and merge in the ViewModel. This follows the existing separation pattern and makes each scanner independently testable.

  Create `feature/lanscan/src/main/kotlin/.../engine/LanMdnsScannerImpl.kt`:
  ```kotlin
  @Singleton
  class LanMdnsScannerImpl @Inject constructor(
      @ApplicationContext private val context: Context,
  ) : LanMdnsScanner {
      private val nsdManager: NsdManager by lazy {
          context.getSystemService(Context.NSD_SERVICE) as NsdManager
      }
      private val serviceTypes = listOf(
          "_services._dns-sd._udp", "_http._tcp", "_https._tcp",
          "_ssh._tcp", "_smb._tcp", "_airplay._tcp", "_ipp._tcp",
      )
      override fun discover(timeoutMs: Long): Flow<LanDevice> = callbackFlow {
          val seen = ConcurrentHashMap<String, MutableList<String>>() // ip → services
          val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
          val listeners = mutableListOf<NsdManager.DiscoveryListener>()
          // Serial resolve coroutine
          launch {
              for (service in resolveQueue) {
                  val resolved = resolveServiceSuspending(service)
                  if (resolved != null) {
                      val ip = resolved.first
                      val serviceType = resolved.second
                      val services = seen.getOrPut(ip) { mutableListOf() }
                      val isNew = services.isEmpty()
                      services.add(serviceType)
                      val hostname = resolved.third
                      trySend(LanDevice(
                          ip = ip, hostname = hostname,
                          isReachable = true, discoveryMethod = DiscoveryMethod.MDNS,
                          services = services.toList(),
                      ))
                  }
              }
          }
          for (type in serviceTypes) {
              val listener = createDiscoveryListener(resolveQueue)
              listeners.add(listener)
              try {
                  nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
              } catch (_: Exception) { /* some types may fail */ }
          }
          // Auto-close after timeout
          launch {
              delay(timeoutMs)
              close()
          }
          awaitClose {
              resolveQueue.close()
              listeners.forEach { stopDiscoveryQuietly(it) }
          }
      }
  }
  ```
- **MIRROR**: CALLBACKFLOW_NSD, RESOLVE_SUSPENDING
- **IMPORTS**: NsdManager, callbackFlow, Channel, suspendCancellableCoroutine, CancellationException
- **GOTCHA**: Must handle `FAILURE_ALREADY_ACTIVE` — serial resolve via Channel. Multiple service type discoveries can run in parallel since each has its own listener. Timeout ensures scan completes within bounds.
- **VALIDATE**: Compiles. Produces `Flow<LanDevice>` with `DiscoveryMethod.MDNS`.

### Task 5: Update DI module for new bindings
- **ACTION**: Add `LanMdnsScanner` binding to `LanScanModule.kt`
- **IMPLEMENT**:
  ```kotlin
  @Binds @Singleton
  abstract fun bindLanMdnsScanner(impl: LanMdnsScannerImpl): LanMdnsScanner
  ```
- **MIRROR**: HILT_BINDS
- **IMPORTS**: `LanMdnsScanner`, `LanMdnsScannerImpl`
- **GOTCHA**: None
- **VALIDATE**: Hilt compiles, no unresolved bindings

### Task 6: Update LanScanViewModel to merge ping + mDNS
- **ACTION**: Inject `LanMdnsScanner`, launch both scans concurrently, merge results by IP
- **IMPLEMENT**:
  ```kotlin
  @HiltViewModel
  class LanScanViewModel @Inject constructor(
      private val subnetScanner: SubnetScanner,
      private val mdnsScanner: LanMdnsScanner,
      @ApplicationContext private val context: Context,
  ) : ViewModel() {
      // In startScan(), after launching subnetScanner:
      scanJob = viewModelScope.launch {
          var deviceCount = 0
          // Merge function: merges new device into existing list by IP
          fun mergeDevice(device: LanDevice) {
              _uiState.update { state ->
                  val existing = state.devices.find { it.ip == device.ip }
                  val merged = if (existing != null) {
                      existing.copy(
                          hostname = existing.hostname ?: device.hostname,
                          discoveryMethod = DiscoveryMethod.BOTH,
                          services = (existing.services + device.services).distinct(),
                          deviceType = existing.deviceType ?: device.deviceType,
                          osGuess = existing.osGuess ?: device.osGuess,
                          latencyMs = maxOf(existing.latencyMs, device.latencyMs),
                      )
                  } else {
                      deviceCount++
                      device
                  }
                  val updatedDevices = if (existing != null) {
                      state.devices.map { if (it.ip == merged.ip) merged else it }
                  } else {
                      state.devices + merged
                  }.sortedWith(_sortOrder.value.comparator())
                  state.copy(devices = updatedDevices, deviceCount = deviceCount)
              }
          }

          // Launch both concurrently
          val pingJob = launch {
              subnetScanner.scan(subnet, prefixLength)
                  .catch { e -> _uiState.update { it.copy(error = e.message ?: "Ping sweep failed") } }
                  .collect { mergeDevice(fingerprinter.fingerprint(it)) }
          }
          val mdnsJob = launch {
              mdnsScanner.discover()
                  .catch { /* mDNS failure is non-fatal */ }
                  .collect { mergeDevice(fingerprinter.fingerprint(it)) }
          }
          pingJob.join()
          mdnsJob.join()
          _uiState.update { it.copy(isScanning = false, progress = 1f) }
      }
  ```
  - Remove `SortOrder.VENDOR` — no vendor data without MAC
  - Remove `DeviceFingerprinter` from `SubnetScannerImpl` constructor, inject into ViewModel instead (fingerprinting now happens at merge point)
- **MIRROR**: VIEWMODEL_STATE_UPDATE
- **IMPORTS**: `LanMdnsScanner`, `DiscoveryMethod`
- **GOTCHA**: Merge must be thread-safe — `_uiState.update {}` is atomic via `MutableStateFlow`. `DeviceFingerprinter` should be called after merge so hostname from mDNS is available. Both jobs must join before marking scan complete.
- **VALIDATE**: ViewModel compiles. Devices appear from both sources during scan.

### Task 7: Update LanScanUiState
- **ACTION**: Add `deviceCount` field for live progress display
- **IMPLEMENT**:
  ```kotlin
  data class LanScanUiState(
      val devices: List<LanDevice> = emptyList(),
      val isScanning: Boolean = false,
      val subnetInfo: String = "",
      val progress: Float = 0f,
      val error: String? = null,
      val deviceCount: Int = 0,
  )
  ```
- **MIRROR**: Existing UiState pattern
- **IMPORTS**: None
- **GOTCHA**: None
- **VALIDATE**: Compiles

### Task 8: Update LanScanScreen — DeviceCard
- **ACTION**: Modify `DeviceCard` to: demote MAC field, show discovery method chip, show service chips, hide copy-MAC button
- **IMPLEMENT**:
  - Replace the `device.mac?.let` block (lines 253-259) with:
    ```kotlin
    Text(
        text = stringResource(R.string.lanscan_mac_unavailable),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    ```
  - Replace `device.vendor` chip (lines 293-305) with discovery method chip:
    ```kotlin
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = when (device.discoveryMethod) {
                    DiscoveryMethod.PING -> "PING"
                    DiscoveryMethod.MDNS -> "mDNS"
                    DiscoveryMethod.BOTH -> "PING+mDNS"
                },
                style = MaterialTheme.typography.labelSmall,
            )
        },
    )
    ```
  - Add services row after the Column (inside the Card):
    ```kotlin
    if (device.services.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            device.services.forEach { service ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(service.removePrefix("_").removeSuffix("._tcp"), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
    ```
  - Remove copy-MAC `IconButton` block (lines 320-331)
  - Update device count display (line 187): `"Found ${uiState.deviceCount} devices${if (uiState.isScanning) "..." else ""}"`
- **MIRROR**: Existing chip and text patterns in LanScanScreen
- **IMPORTS**: Add `FlowRow` from `androidx.compose.foundation.layout`, `DiscoveryMethod`
- **GOTCHA**: `FlowRow` is in `foundation.layout` (Compose 1.4+). Service type strings need cleanup — strip `_` prefix and `._tcp` suffix for display. The Card layout changes from single Row to Column(Row + FlowRow).
- **VALIDATE**: UI compiles. Visual check: MAC shows "—", discovery method chip visible, service chips render.

### Task 9: Update LanScanViewModel sort options
- **ACTION**: Remove `VENDOR` from `SortOrder` enum, update comparator
- **IMPLEMENT**:
  ```kotlin
  enum class SortOrder { IP, LATENCY }
  ```
  Remove `SortOrder.VENDOR -> ...` from `comparator()`. Remove vendor sort dropdown menu item from Screen.
- **MIRROR**: Existing enum + comparator pattern
- **IMPORTS**: None
- **GOTCHA**: Remove the `DropdownMenuItem` for vendor sort in LanScanScreen (lines 120-129). Also remove `lanscan_sort_by_vendor` string resource.
- **VALIDATE**: Compiles. Only IP and Latency sort options visible.

### Task 10: Update string resources
- **ACTION**: Add new strings, remove unused vendor sort string
- **IMPLEMENT**:
  ```xml
  <string name="lanscan_mac_unavailable">MAC: — (Not available on Android 10+)</string>
  <string name="lanscan_found_devices">Found %1$d devices</string>
  <string name="lanscan_found_devices_scanning">Found %1$d devices…</string>
  ```
  Remove: `<string name="lanscan_sort_by_vendor">Sort by Vendor</string>`
  Remove: `<string name="lanscan_cd_copy_mac">Copy MAC</string>`
- **MIRROR**: Existing `lanscan_` prefix convention
- **IMPORTS**: N/A
- **GOTCHA**: None
- **VALIDATE**: No unresolved string references

### Task 11: Delete ArpTableReader
- **ACTION**: Delete `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/engine/ArpTableReader.kt`
- **IMPLEMENT**: `git rm feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/engine/ArpTableReader.kt`
- **MIRROR**: N/A
- **IMPORTS**: N/A
- **GOTCHA**: Verify no remaining references via grep. The only caller was `SubnetScannerImpl` (already updated in Task 3).
- **VALIDATE**: `grep -r "ArpTableReader" feature/lanscan/` returns nothing

### Task 12: Move DeviceFingerprinter injection
- **ACTION**: Remove `DeviceFingerprinter` from `SubnetScannerImpl` constructor, ensure it's injected into `LanScanViewModel`
- **IMPLEMENT**: `SubnetScannerImpl` constructor becomes:
  ```kotlin
  class SubnetScannerImpl @Inject constructor() : SubnetScanner
  ```
  `LanScanViewModel` constructor becomes:
  ```kotlin
  class LanScanViewModel @Inject constructor(
      private val subnetScanner: SubnetScanner,
      private val mdnsScanner: LanMdnsScanner,
      private val fingerprinter: DeviceFingerprinter,
      @ApplicationContext private val context: Context,
  )
  ```
- **MIRROR**: HILT_BINDS — `DeviceFingerprinter` is a concrete class with `@Inject constructor()`, no binding needed
- **IMPORTS**: Add `DeviceFingerprinter` import to ViewModel
- **GOTCHA**: `DeviceFingerprinter` has `@Inject constructor()` already — Hilt auto-provides it
- **VALIDATE**: No compilation errors. Fingerprinting still applied to all devices.

### Task 13: Write unit tests for SubnetScannerImpl
- **ACTION**: Create `feature/lanscan/src/test/kotlin/com.ventoux.netlens/feature/lanscan/engine/SubnetScannerImplTest.kt`
- **IMPLEMENT**:
  ```kotlin
  class SubnetScannerImplTest {
      @Test
      fun `calculateIpRange returns correct range for /24 subnet`() {
          val range = SubnetScannerImpl.calculateIpRange("192.168.1.0", 24)
          assertEquals(254, range.size)
          assertEquals("192.168.1.1", range.first())
          assertEquals("192.168.1.254", range.last())
      }
      @Test
      fun `calculateIpRange returns empty for invalid subnet`() {
          val range = SubnetScannerImpl.calculateIpRange("invalid", 24)
          assertTrue(range.isEmpty())
      }
      @Test
      fun `calculateIpRange returns empty when host count exceeds MAX_HOSTS`() {
          val range = SubnetScannerImpl.calculateIpRange("10.0.0.0", 8)
          assertTrue(range.isEmpty())
      }
  }
  ```
- **MIRROR**: TEST_STRUCTURE — backtick names, JUnit 5
- **IMPORTS**: `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Assertions.*`
- **GOTCHA**: `calculateIpRange` is `internal` in companion object — accessible from test in same module
- **VALIDATE**: Tests pass

### Task 14: Write FakeSubnetScanner and FakeLanMdnsScanner
- **ACTION**: Create test fakes for both scanner interfaces
- **IMPLEMENT**:
  ```kotlin
  // FakeSubnetScanner.kt
  class FakeSubnetScanner : SubnetScanner {
      var devices: List<LanDevice> = emptyList()
      var error: Throwable? = null
      override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow {
          error?.let { throw it }
          devices.forEach { emit(it) }
      }
  }

  // FakeLanMdnsScanner.kt
  class FakeLanMdnsScanner : LanMdnsScanner {
      var devices: List<LanDevice> = emptyList()
      var error: Throwable? = null
      override fun discover(timeoutMs: Long): Flow<LanDevice> = flow {
          error?.let { throw it }
          devices.forEach { emit(it) }
      }
  }
  ```
- **MIRROR**: TEST_STRUCTURE (FakePinger pattern)
- **IMPORTS**: `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.flow`
- **GOTCHA**: None
- **VALIDATE**: Fakes compile

### Task 15: Write LanScanViewModel merge tests
- **ACTION**: Create/update `LanScanViewModelTest.kt` to test merge behavior
- **IMPLEMENT**: Test cases:
  - `ping only devices have PING discovery method`
  - `mdns only devices have MDNS discovery method`
  - `devices found by both methods are merged with BOTH discovery method`
  - `merged device combines services from both sources`
  - `merged device prefers first non-null hostname`
  - `error from ping updates error state`
  - `error from mdns is non-fatal`
- **MIRROR**: TEST_STRUCTURE — `UnconfinedTestDispatcher`, `Dispatchers.setMain`, Turbine
- **IMPORTS**: JUnit 5, Turbine, kotlinx-coroutines-test
- **GOTCHA**: ViewModel needs `ConnectivityManager` mock or refactoring to inject subnet info. For testing, consider extracting subnet detection into a separate injectable. Alternatively, test the merge logic directly.
- **VALIDATE**: All tests pass

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| calculateIpRange /24 | "192.168.1.0", 24 | 254 IPs from .1 to .254 | No |
| calculateIpRange invalid | "invalid", 24 | empty list | Yes |
| calculateIpRange /8 too large | "10.0.0.0", 8 | empty list (>MAX_HOSTS) | Yes |
| Merge ping-only device | device from ping | discoveryMethod = PING | No |
| Merge mdns-only device | device from mDNS | discoveryMethod = MDNS | No |
| Merge both sources same IP | ping + mDNS for same IP | discoveryMethod = BOTH, services merged | No |
| mDNS error non-fatal | mDNS throws, ping works | devices from ping shown, no error | Yes |
| Ping error shows error | ping throws | error message in UI state | No |
| Empty subnet | no devices reachable | empty list, scan completes | Yes |

### Edge Cases Checklist
- [ ] No network connection (linkProperties == null)
- [ ] No IPv4 address on interface
- [ ] All 254 IPs unreachable
- [ ] mDNS discovers device not in ping range
- [ ] Same device discovered by both ping and mDNS
- [ ] mDNS discovery fails entirely (non-fatal)
- [ ] Scan cancelled mid-sweep
- [ ] Device hostname is same as IP (resolveHostname returns null)
- [ ] Very large subnet (/16 — capped by MAX_HOSTS)

---

## Validation Commands

### Static Analysis
```bash
./gradlew :feature:lanscan:compileDebugKotlin
```
EXPECT: Zero compilation errors

### Unit Tests
```bash
./gradlew :feature:lanscan:testDebugUnitTest
```
EXPECT: All tests pass

### Full Test Suite
```bash
./gradlew testDebugUnitTest
```
EXPECT: No regressions in other modules

### Build
```bash
./gradlew assembleDebug
```
EXPECT: APK builds successfully

### Manual Validation
- [ ] Install debug APK on Android 10+ device connected to WiFi
- [ ] Open LAN Scan tool
- [ ] Tap FAB to start scan
- [ ] Verify progress indicator shows and updates
- [ ] Verify "Found N devices..." text updates live during scan
- [ ] Verify devices appear with IP and hostname (when available)
- [ ] Verify MAC shows "—" with "(Not available)" text
- [ ] Verify discovery method chip shows PING / mDNS / PING+mDNS
- [ ] Verify mDNS devices show service chips (http, ssh, etc.)
- [ ] Verify sort by IP works
- [ ] Verify sort by Latency works
- [ ] Verify cancel scan stops both ping and mDNS
- [ ] Verify scan completes within ~10 seconds on /24 subnet
- [ ] Verify no ANR during scan (semaphore bounds concurrency)
- [ ] Verify no crash on device with no WiFi connection

---

## Acceptance Criteria
- [ ] All tasks completed
- [ ] All validation commands pass
- [ ] Tests written and passing
- [ ] No type errors
- [ ] No references to `/proc/net/arp` or `ArpTableReader` remain
- [ ] Scan discovers devices via ping sweep on Android 10+
- [ ] Scan discovers mDNS-advertising devices
- [ ] Results merged by IP with correct `DiscoveryMethod`
- [ ] UI shows discovery method and services
- [ ] MAC field shows "—" gracefully
- [ ] Total scan time under 10 seconds on /24

## Completion Checklist
- [ ] Code follows discovered patterns (callbackFlow, Semaphore, Hilt @Binds)
- [ ] Error handling matches codebase style (`.catch {}`, `runCatching`)
- [ ] No hardcoded strings (all user-facing text in strings.xml)
- [ ] Tests follow test patterns (backtick names, fakes over mocks, JUnit 5)
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `InetAddress.isReachable()` uses TCP fallback instead of ICMP (no root) | Medium | Some devices not detected | mDNS covers ICMP-blocking devices |
| NsdManager discover fails on some Android versions/OEMs | Low | Reduced discovery | Ping sweep is primary; mDNS is additive |
| 64 concurrent ping coroutines still too aggressive on low-end devices | Low | ANR or network congestion | Lower semaphore to 32 if needed; timeout protects |
| Serial mDNS resolve queue slow for many services | Medium | Some devices not resolved before timeout | 5s timeout is generous; resolve is fast per-service |
| `FlowRow` not available in current Compose version | Low | Build failure | Fall back to `Row` with `horizontalScroll` |

## Notes
- `OuiLookup` dependency remains in `build.gradle.kts` but is unused. Leave it — the scan-history feature may re-enable vendor lookup for rooted devices in the future.
- `DeviceFingerprinter` continues to work because it matches on hostname patterns, not just vendor strings. Moving it to the ViewModel merge point ensures it has the best available data (hostname from either ping or mDNS).
- The existing `feature:mdns` module is NOT reused here. LAN scan gets its own NSD integration because: (1) the mdns module emits `MdnsService` not `LanDevice`, (2) LAN scan discovers multiple service types simultaneously, (3) decoupling prevents cross-feature dependencies.
