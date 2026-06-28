# Plan: LAN Scan Host Detail — Port Scan, Fingerprinting & MAC Addresses

## Summary
When a user taps a discovered host in LAN Scan results, open a bottom sheet showing host details and an inline port scan. Enhance device fingerprinting using open port signatures. Explain and surface the MAC address platform limitation.

## User Story
As a network administrator using NetLens,
I want to tap a discovered LAN host and immediately see its open ports and a richer device profile,
So that I can quickly assess what services each device runs without manually copying the IP into Port Scanner.

## Problem → Solution
**Current:** DeviceCard is display-only. Clicking does nothing. Fingerprinting relies solely on hostname keywords (low hit rate). MAC addresses are permanently hidden with no explanation.
**Desired:** Tapping a host opens a bottom sheet with port scan + enriched fingerprint. MAC row explains *why* it's unavailable (Android 10+ restriction).

## Metadata
- **Complexity**: Medium
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 12-15

---

## UX Design

### Before
```
┌──────────────────────────────────┐
│ ✓  192.168.1.5                   │
│    myprinter.local               │
│    MAC: — (Not available)        │
│    [Printer] [PING+mDNS] [📋]   │
│    [http] [ipp]                  │
└──────────────────────────────────┘
  (no click action)
```

### After
```
┌──────────────────────────────────┐
│ ✓  192.168.1.5            >     │  ← card is clickable, chevron hint
│    myprinter.local               │
│    MAC: unavailable (Android 10+ │
│    blocks ARP table access)      │
│    [Printer] [PING+mDNS] [📋]   │
│    [http] [ipp]                  │
└──────────────────────────────────┘
  (tap opens bottom sheet ↓)

┌──────────────────────────────────┐
│ ─── 192.168.1.5 ───             │
│ myprinter.local                  │
│ Latency: 12ms | PING+mDNS       │
│ Type: Printer | OS: —            │
│                                  │
│ ── Port Scan ──                  │
│ [Common ▪] [All] [Custom]       │
│ [▶ Scan Ports]                   │
│ ━━━━━━━━━━░░░░░░░ 45%           │
│  80  HTTP       ✓ open   4ms    │
│ 443  HTTPS      ✓ open   5ms    │
│ 631  IPP        ✓ open   3ms    │
│  22  SSH        ✗ closed        │
│                                  │
│ ── Fingerprint ──                │
│ Profile: Network Printer         │
│ Evidence: port 631 (IPP),        │
│   mDNS _ipp._tcp, hostname      │
│   contains "printer"             │
└──────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| DeviceCard tap | Nothing | Opens ModalBottomSheet | Card gets onClick + chevron |
| Port scan of LAN host | Manual: copy IP → navigate to Port Scan → paste | Inline in bottom sheet | Reuses PortScanner engine |
| Device fingerprint | Hostname keywords only | Hostname + open ports + mDNS services | Post-port-scan enrichment |
| MAC address text | "MAC: — (Not available)" | "MAC: unavailable (Android restricts ARP access)" | Explains why, no false hope |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `feature/lanscan/.../LanScanScreen.kt` | 274-403 | DeviceCard composable to modify |
| P0 | `feature/lanscan/.../LanScanViewModel.kt` | all | State management pattern to follow |
| P0 | `feature/lanscan/.../model/LanDevice.kt` | all | Device model — no MAC field |
| P0 | `feature/lanscan/.../model/LanScanUiState.kt` | all | UI state to extend |
| P0 | `feature/portscan/.../engine/PortScannerImpl.kt` | all | Port scanning engine to reuse |
| P0 | `feature/portscan/.../model/WellKnownPorts.kt` | all | Port → service name mapping |
| P1 | `feature/lanscan/.../engine/DeviceFingerprinter.kt` | all | Current fingerprinting to enhance |
| P1 | `feature/lanscan/.../di/LanScanModule.kt` | all | DI bindings to update |
| P2 | `feature/portscan/.../PortScanScreen.kt` | 115-225 | Reference for port scan UI patterns |
| P2 | `feature/lanscan/src/main/res/values/strings.xml` | all | String resources to extend |

---

## Patterns to Mirror

### STATE_UPDATE
// SOURCE: LanScanViewModel.kt:115-143
```kotlin
_uiState.update {
    it.copy(
        devices = updatedDevices,
        deviceCount = updatedDevices.size,
        progress = (deviceCount.toFloat() / expectedHosts).coerceAtMost(0.95f),
    )
}
```

### CARD_COMPOSABLE
// SOURCE: LanScanScreen.kt:279-283
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
)
```

### DI_BINDING
// SOURCE: LanScanModule.kt
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class LanScanModule {
    @Binds @Singleton
    abstract fun bindSubnetScanner(impl: SubnetScannerImpl): SubnetScanner
}
```

### FLOW_SCAN_PATTERN
// SOURCE: PortScannerImpl.kt:19-40
```kotlin
fun scan(host: String, ports: List<Int>, timeoutMs: Int): Flow<PortResult> = flow {
    // validate, batch, scan in parallel, emit results
}.flowOn(Dispatchers.IO)
```

### TEST_STRUCTURE
// SOURCE: feature/lanscan/src/test/.../DeviceFingerprinterTest.kt
```kotlin
class DeviceFingerprinterTest {
    private val fingerprinter = DeviceFingerprinter()

    @Test
    fun `device with printer hostname is classified as Printer`() {
        val device = LanDevice(ip = "192.168.1.10", hostname = "hp-printer.local")
        val result = fingerprinter.fingerprint(device)
        assertEquals("Printer", result.deviceType)
    }
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `feature/lanscan/.../model/LanScanUiState.kt` | UPDATE | Add `selectedDevice: LanDevice?` for bottom sheet |
| `feature/lanscan/.../model/HostDetailState.kt` | CREATE | State for port scan results + enriched fingerprint in bottom sheet |
| `feature/lanscan/.../LanScanViewModel.kt` | UPDATE | Add host selection, port scan, and enriched fingerprint logic |
| `feature/lanscan/.../LanScanScreen.kt` | UPDATE | Make DeviceCard clickable, add ModalBottomSheet with port scan UI |
| `feature/lanscan/.../engine/DeviceFingerprinter.kt` | UPDATE | Add port-based fingerprinting method |
| `feature/lanscan/.../di/LanScanModule.kt` | UPDATE | Add PortScanner binding |
| `feature/lanscan/build.gradle.kts` | UPDATE | Add dependency on `:feature:portscan` for WellKnownPorts and PortScanner |
| `feature/lanscan/src/main/res/values/strings.xml` | UPDATE | Add new string resources |
| `feature/lanscan/src/test/.../DeviceFingerprinterTest.kt` | UPDATE | Add port-based fingerprint tests |
| `feature/lanscan/src/test/.../LanScanViewModelTest.kt` | CREATE | Test host selection and port scan flow |

## NOT Building

- Standalone navigation from LAN Scan → Port Scan screen (inline bottom sheet instead)
- MAC address retrieval (impossible on Android 10+ without root)
- OS fingerprinting via TCP/IP stack analysis (too low-level for Android)
- Full port range scan from bottom sheet (keep it to common ports by default)
- Banner grabbing or service version detection

---

## Step-by-Step Tasks

### Task 1: Create HostDetailState model
- **ACTION**: Create `feature/lanscan/src/main/kotlin/com/ventouxlabs/netlens/feature/lanscan/model/HostDetailState.kt`
- **IMPLEMENT**:
  ```kotlin
  data class HostDetailState(
      val device: LanDevice,
      val portResults: List<PortResult> = emptyList(),
      val isScanning: Boolean = false,
      val progress: Float = 0f,
      val openCount: Int = 0,
      val enrichedType: String? = null,
      val enrichedOs: String? = null,
      val fingerprintEvidence: List<String> = emptyList(),
      val error: String? = null,
  )
  ```
- **MIRROR**: LanScanUiState pattern — flat data class, no sealed hierarchy
- **IMPORTS**: `com.ventouxlabs.netlens.feature.portscan.model.PortResult`
- **VALIDATE**: Compiles with no errors

### Task 2: Add selectedDevice to LanScanUiState
- **ACTION**: Update `LanScanUiState` to track which device is selected for the bottom sheet
- **IMPLEMENT**: Add `selectedDevice: LanDevice? = null` field
- **MIRROR**: STATE_UPDATE pattern — copy-on-write
- **VALIDATE**: Existing tests still pass

### Task 3: Enhance DeviceFingerprinter with port-based logic
- **ACTION**: Update `DeviceFingerprinter.kt` — add `fingerprintWithPorts()` method
- **IMPLEMENT**:
  ```kotlin
  fun fingerprintWithPorts(
      device: LanDevice,
      openPorts: List<Int>,
  ): Triple<String?, String?, List<String>> {
      val evidence = mutableListOf<String>()
      var type = device.deviceType
      var os = device.osGuess

      // Port-based type detection
      if (openPorts.containsAll(listOf(80, 443, 8080))) {
          if (type == null) type = "Web Server"
          evidence.add("ports 80/443/8080 (web)")
      }
      if (631 in openPorts || 9100 in openPorts) {
          type = "Printer"
          evidence.add("port ${if (631 in openPorts) "631 (IPP)" else "9100 (raw print)"}")
      }
      if (548 in openPorts || 5353 in openPorts) {
          if (os == null) os = "macOS"
          evidence.add("port 548 (AFP) or 5353 (mDNS)")
      }
      if (3389 in openPorts) {
          if (os == null) os = "Windows"
          evidence.add("port 3389 (RDP)")
      }
      if (22 in openPorts && 53 in openPorts) {
          if (type == null) type = "Router"
          evidence.add("ports 22+53 (SSH+DNS)")
      }

      // mDNS service evidence
      device.services.forEach { svc ->
          val clean = svc.trim('.').removePrefix("_").removeSuffix("._tcp").removeSuffix("._udp")
          evidence.add("mDNS: $clean")
      }

      // Hostname evidence
      device.hostname?.let {
          evidence.add("hostname: $it")
      }

      return Triple(type ?: device.deviceType, os ?: device.osGuess, evidence)
  }
  ```
- **MIRROR**: Existing `guessDeviceType()` / `guessOs()` pattern in same file
- **GOTCHA**: Don't overwrite more specific guesses from hostname with less specific port guesses — port logic should only fill nulls
- **VALIDATE**: Unit tests for port combinations

### Task 4: Add PortScanner dependency to LAN scan module
- **ACTION**: Update `feature/lanscan/build.gradle.kts` to depend on `:feature:portscan`
- **IMPLEMENT**: Add `implementation(project(":feature:portscan"))` to dependencies
- **GOTCHA**: This creates a feature→feature dependency. Acceptable here because we only need `PortScanner` interface, `PortResult`, and `WellKnownPorts` — no circular dependency risk since portscan doesn't depend on lanscan
- **VALIDATE**: `./gradlew :feature:lanscan:assembleDebug` succeeds

### Task 5: Update LanScanModule DI
- **ACTION**: PortScanner is already bound in PortScanModule as a singleton. No additional binding needed in LanScanModule since Hilt resolves cross-module singletons.
- **VALIDATE**: PortScanner can be injected into LanScanViewModel

### Task 6: Update LanScanViewModel with host detail logic
- **ACTION**: Add device selection and inline port scan to LanScanViewModel
- **IMPLEMENT**:
  ```kotlin
  // New state
  private val _hostDetail = MutableStateFlow<HostDetailState?>(null)
  val hostDetail: StateFlow<HostDetailState?> = _hostDetail.asStateFlow()
  private var hostScanJob: Job? = null

  // New injected dependency
  @Inject constructor(
      ...,
      private val portScanner: PortScanner,  // add this
  )

  fun selectDevice(device: LanDevice) {
      _hostDetail.value = HostDetailState(device = device)
      _uiState.update { it.copy(selectedDevice = device) }
  }

  fun dismissDetail() {
      hostScanJob?.cancel()
      _hostDetail.value = null
      _uiState.update { it.copy(selectedDevice = null) }
  }

  fun scanHostPorts(ports: List<Int> = WellKnownPorts.COMMON_PORTS.keys.sorted()) {
      val detail = _hostDetail.value ?: return
      hostScanJob?.cancel()
      hostScanJob = viewModelScope.launch {
          _hostDetail.update { it?.copy(isScanning = true, progress = 0f, portResults = emptyList(), error = null) }
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
              // Enrich fingerprint after scan
              val openPorts = _hostDetail.value?.portResults?.filter { it.isOpen }?.map { it.port }.orEmpty()
              val (type, os, evidence) = fingerprinter.fingerprintWithPorts(detail.device, openPorts)
              _hostDetail.update {
                  it?.copy(isScanning = false, progress = 1f, enrichedType = type, enrichedOs = os, fingerprintEvidence = evidence)
              }
          } catch (e: CancellationException) { throw e }
          catch (e: Exception) {
              _hostDetail.update { it?.copy(isScanning = false, error = e.message) }
          }
      }
  }

  fun cancelHostScan() {
      hostScanJob?.cancel()
      _hostDetail.update { it?.copy(isScanning = false) }
  }
  ```
- **MIRROR**: STATE_UPDATE pattern, scan/cancel pattern from existing `startScan()`/`cancelScan()`
- **IMPORTS**: `com.ventouxlabs.netlens.feature.portscan.engine.PortScanner`, `com.ventouxlabs.netlens.feature.portscan.model.PortResult`, `com.ventouxlabs.netlens.feature.portscan.model.WellKnownPorts`
- **GOTCHA**: Must rethrow CancellationException. Use `_hostDetail.update {}` (nullable-safe) not `.value =`
- **VALIDATE**: Build compiles, unit tests for select/dismiss/scan flow

### Task 7: Update LanScanScreen — make DeviceCard clickable
- **ACTION**: Add `onClick` parameter to DeviceCard, add chevron icon
- **IMPLEMENT**:
  - Change `DeviceCard(device: LanDevice)` signature to `DeviceCard(device: LanDevice, onClick: () -> Unit)`
  - Wrap Card content with `modifier = Modifier.fillMaxWidth().clickable { onClick() }`
  - Add chevron icon at end of Row: `Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, ...)`
  - Update LazyColumn item: `DeviceCard(device = device, onClick = { viewModel.selectDevice(device) })`
- **MIRROR**: CARD_COMPOSABLE pattern
- **VALIDATE**: Card responds to tap

### Task 8: Update MAC address display text
- **ACTION**: Update string resource and display
- **IMPLEMENT**:
  - In `strings.xml`: Change `lanscan_mac_unavailable` to `"MAC: unavailable (Android restricts ARP access)"`
  - Keep the same styling (bodySmall, 0.6f alpha)
- **VALIDATE**: Text renders correctly, explains the restriction

### Task 9: Add ModalBottomSheet to LanScanScreen
- **ACTION**: Add bottom sheet composable showing host detail with port scan
- **IMPLEMENT**: New `HostDetailSheet` composable in LanScanScreen.kt
  - Uses `ModalBottomSheet` (M3)
  - Header: IP, hostname, latency, discovery method
  - Fingerprint section: type, OS, evidence list
  - Port scan section: preset chips (Common/All), scan button, progress bar, results list
  - Results: sorted open-first, show port number + service name + status + latency
  - Show in LanScanScreen when `uiState.selectedDevice != null`
- **MIRROR**: Port scan UI from PortScanScreen.kt:115-225 for result display pattern
- **IMPORTS**: `androidx.compose.material3.ModalBottomSheet`, `androidx.compose.material3.rememberModalBottomSheetState`
- **GOTCHA**: ModalBottomSheet requires `ExperimentalMaterial3Api` opt-in. Use `sheetState.hide()` before setting `selectedDevice = null` to avoid animation jank
- **VALIDATE**: Bottom sheet opens on card tap, port scan runs and shows results

### Task 10: Add string resources
- **ACTION**: Add new strings to `feature/lanscan/src/main/res/values/strings.xml`
- **IMPLEMENT**:
  ```xml
  <string name="lanscan_host_detail_title">Host Details</string>
  <string name="lanscan_latency_ms">Latency: %1$d ms</string>
  <string name="lanscan_port_scan_section">Port Scan</string>
  <string name="lanscan_scan_ports">Scan Ports</string>
  <string name="lanscan_cancel_port_scan">Cancel</string>
  <string name="lanscan_fingerprint_section">Fingerprint</string>
  <string name="lanscan_profile_label">Profile: %1$s</string>
  <string name="lanscan_os_label">OS: %1$s</string>
  <string name="lanscan_evidence_label">Evidence</string>
  <string name="lanscan_open_count">%1$d open</string>
  <string name="lanscan_scanned_count">%1$d scanned</string>
  <string name="lanscan_chip_common">Common</string>
  <string name="lanscan_chip_all">All (1-1024)</string>
  ```
- **VALIDATE**: No missing resource errors

### Task 11: Add DeviceFingerprinter tests for port-based logic
- **ACTION**: Update `DeviceFingerprinterTest.kt` with new test cases
- **IMPLEMENT**:
  ```kotlin
  @Test
  fun `device with printer ports is classified as Printer`() {
      val device = LanDevice(ip = "192.168.1.10")
      val (type, _, evidence) = fingerprinter.fingerprintWithPorts(device, listOf(631, 80))
      assertEquals("Printer", type)
      assertTrue(evidence.any { "631" in it })
  }

  @Test
  fun `device with RDP port gets Windows OS guess`() {
      val device = LanDevice(ip = "192.168.1.20")
      val (_, os, _) = fingerprinter.fingerprintWithPorts(device, listOf(3389, 445))
      assertEquals("Windows", os)
  }

  @Test
  fun `hostname guess not overwritten by port guess`() {
      val device = LanDevice(ip = "192.168.1.5", deviceType = "Smart TV")
      val (type, _, _) = fingerprinter.fingerprintWithPorts(device, listOf(80, 443))
      assertEquals("Smart TV", type) // hostname-based guess preserved
  }

  @Test
  fun `mDNS services appear in evidence`() {
      val device = LanDevice(ip = "192.168.1.8", services = listOf("_http._tcp."))
      val (_, _, evidence) = fingerprinter.fingerprintWithPorts(device, emptyList())
      assertTrue(evidence.any { "mDNS: http" in it })
  }
  ```
- **MIRROR**: TEST_STRUCTURE pattern from existing DeviceFingerprinterTest
- **VALIDATE**: `./gradlew :feature:lanscan:testDebugUnitTest` passes

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| Port fingerprint: printer ports | openPorts=[631] | type="Printer" | No |
| Port fingerprint: RDP | openPorts=[3389] | os="Windows" | No |
| Port fingerprint: no ports | openPorts=[] | type/os unchanged | Yes |
| Port fingerprint: hostname takes priority | device with type="Smart TV", openPorts=[80,443] | type="Smart TV" | Yes |
| selectDevice sets hostDetail | tap device | hostDetail.device == tapped device | No |
| dismissDetail clears state | dismiss | hostDetail == null, selectedDevice == null | No |
| scanHostPorts collects results | scan 3 ports | portResults.size == 3, progress == 1f | No |
| scanHostPorts error handling | port scanner throws | error message set, isScanning=false | Yes |
| cancelHostScan stops scan | cancel mid-scan | isScanning=false | No |

### Edge Cases Checklist
- [ ] Device unreachable during port scan (all ports closed)
- [ ] Scan cancelled mid-flight
- [ ] Bottom sheet dismissed during active scan (should cancel)
- [ ] Device with no hostname, no services (minimal card)
- [ ] Very long hostname or many services (layout overflow)

---

## Validation Commands

### Static Analysis
```bash
./gradlew :feature:lanscan:assembleDebug
```
EXPECT: Zero compilation errors

### Unit Tests
```bash
./gradlew :feature:lanscan:testDebugUnitTest
```
EXPECT: All tests pass including new fingerprint tests

### Full Test Suite
```bash
./gradlew testDebugUnitTest
```
EXPECT: No regressions in any module

### Browser Validation
```bash
./gradlew installDebug
```
EXPECT: Tap LAN scan host → bottom sheet opens → port scan runs → fingerprint enriches

### Manual Validation
- [ ] Run LAN scan, wait for results
- [ ] Tap a host card → bottom sheet opens with host info
- [ ] Tap "Scan Ports" → progress bar advances, results appear
- [ ] Open ports show green checkmark, closed show gray
- [ ] After scan completes, fingerprint section shows enriched profile
- [ ] Dismiss bottom sheet (swipe down or tap scrim)
- [ ] Verify MAC text says "Android restricts ARP access"
- [ ] Cancel port scan mid-flight — stops cleanly
- [ ] Tap different host — new bottom sheet with fresh state

---

## Acceptance Criteria
- [ ] Tapping a LAN scan result opens a bottom sheet
- [ ] Bottom sheet shows host info (IP, hostname, latency, discovery method)
- [ ] Port scan runs inline in the bottom sheet using common ports
- [ ] Port results display open/closed status with service names
- [ ] Fingerprint enriches after port scan with evidence list
- [ ] MAC address text explains the Android restriction
- [ ] All existing tests pass
- [ ] New tests for port-based fingerprinting pass

## Completion Checklist
- [ ] Code follows discovered patterns (StateFlow.update, Card composable, DI bindings)
- [ ] Error handling matches codebase style (rethrow CancellationException)
- [ ] Tests follow test patterns (JUnit 5, fakes, backtick names)
- [ ] No hardcoded values (string resources used)
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| feature→feature dependency (lanscan→portscan) | Low | Medium | Only imports interfaces + models, no circular dep |
| ModalBottomSheet API stability | Low | Low | M3 ModalBottomSheet is stable since compose-material3 1.2 |
| Port scan slow on many hosts sequentially | Medium | Low | Only scans one host at a time via bottom sheet |

## Notes
**Why MAC addresses can't be retrieved:** Android 10+ (API 29, which is this app's minSdk) enforces SELinux policies that block unprivileged apps from reading `/proc/net/arp`. This was deliberately removed in commit 572e9ba after confirming it silently returns empty data on all modern devices. There is no workaround without root access. The best UX is to explain the restriction to the user rather than showing empty data.
