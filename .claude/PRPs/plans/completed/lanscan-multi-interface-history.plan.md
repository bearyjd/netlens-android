# Plan: LAN Scan Multi-Interface Suggestions + In-Feature History

## Summary

Replace the single-interface Auto-detect in LAN Scan with a multi-interface suggestion list that shows CIDRs for all routable network interfaces (LAN, VPN, tethering). Add a History tab within the LAN Scan screen that displays past scans and lets users tap to re-run any previous scan with its original CIDR.

## User Story

As a network diagnostics user,
I want to see all my routable interfaces as scan suggestions and browse my scan history,
so that I can quickly scan any network I'm connected to and re-run past scans without re-entering CIDRs.

## Problem -> Solution

**Current**: Auto-detect picks `activeNetwork` which defaults to VPN when active. User must manually switch to Custom mode and type the LAN CIDR. Scan history is saved to the DB but never displayed.

**Desired**: All routable IPv4 interfaces appear as tappable suggestion chips at the top. Tapping one pre-fills the CIDR and starts scanning. A "History" tab shows past scans with subnet, device count, and timestamp. Tapping a history entry re-runs that scan.

## Metadata

- **Complexity**: Medium
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 8-10

---

## UX Design

### Before

```
┌─────────────────────────────────────┐
│ ← LAN Scan                    [⇅]  │
├─────────────────────────────────────┤
│ [Auto-detect✓] [Custom range]       │
│                                     │
│ (Custom CIDR field when selected)   │
│                                     │
│ Subnet: 10.0.0.100/24              │  ← always picks activeNetwork (VPN)
│ Found 3 devices...                  │
│ ┌─────────────────────────────────┐ │
│ │ 10.0.0.1  router        PING   │ │
│ │ 10.0.0.5  phone         mDNS   │ │
│ └─────────────────────────────────┘ │
│                              [🔍]   │
└─────────────────────────────────────┘
```

### After

```
┌─────────────────────────────────────┐
│ ← LAN Scan                    [⇅]  │
├─────────────────────────────────────┤
│ [Scan] [History]                    │  ← two tabs
├─────────────────────────────────────┤
│ Suggested networks:                 │
│ [🌐 192.168.1.0/24] [🔒 10.8.0.0/24] [Custom...] │
│                                     │  ← chips for each routable interface
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░ 65%     │
│ Subnet: 192.168.1.0/24             │
│ Found 12 devices...                 │
│ ┌─────────────────────────────────┐ │
│ │ 192.168.1.1  router     PING   │ │
│ │ 192.168.1.5  phone      mDNS   │ │
│ └─────────────────────────────────┘ │
│                              [🔍]   │
└─────────────────────────────────────┘

── History tab ──
┌─────────────────────────────────────┐
│ ← LAN Scan                    [⇅]  │
├─────────────────────────────────────┤
│ [Scan] [History✓]                   │
├─────────────────────────────────────┤
│ ┌─────────────────────────────────┐ │
│ │ 192.168.1.0/24                  │ │
│ │ 12 devices · 2 hours ago    [→] │ │
│ ├─────────────────────────────────┤ │
│ │ 10.8.0.0/24                     │ │
│ │ 3 devices · yesterday       [→] │ │
│ ├─────────────────────────────────┤ │
│ │ 192.168.50.0/24                 │ │
│ │ 8 devices · 3 days ago      [→] │ │
│ └─────────────────────────────────┘ │
│                                     │
│           [Clear History]           │
└─────────────────────────────────────┘
```

### Interaction Changes

| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Start scan | Tap FAB after selecting Auto/Custom | Tap a suggested CIDR chip (instant scan) or FAB | Faster one-tap flow |
| VPN active | Auto picks VPN subnet only | Both VPN and LAN CIDRs shown as chips | Core fix |
| Custom CIDR | Switch to Custom tab, type CIDR | "Custom..." chip opens text field | Still available |
| History | Not visible | "History" tab shows past scans | New feature |
| Re-run scan | Not possible | Tap history entry, scans that CIDR | New feature |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `feature/lanscan/src/main/kotlin/.../LanScanViewModel.kt` | 87-107 | Current Auto-detect logic using activeNetwork |
| P0 | `feature/lanscan/src/main/kotlin/.../LanScanScreen.kt` | 104-305 | Current UI layout to modify |
| P0 | `feature/lanscan/src/main/kotlin/.../model/LanScanUiState.kt` | all | State to extend |
| P1 | `core/data/src/main/kotlin/.../dao/LanScanHistoryDao.kt` | all | Existing DAO methods |
| P1 | `core/data/src/main/kotlin/.../model/LanScanHistoryEntry.kt` | all | Existing history entity |
| P2 | `core/network/src/main/kotlin/.../ConnectivityManagerNetworkMonitor.kt` | all | Network capability detection pattern |
| P2 | `feature/lanscan/src/main/kotlin/.../engine/SubnetScannerImpl.kt` | 63-82 | IP range calculation |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| ConnectivityManager.getAllNetworks() | Android API docs | Returns all active Networks, not just the default. Available since API 21 (our minSdk is 29). |
| LinkProperties per Network | Android API docs | `getLinkProperties(network)` returns per-interface addresses and routes. Filter by `hasTransport(TRANSPORT_WIFI\|TRANSPORT_ETHERNET\|TRANSPORT_VPN)` |
| NetworkCapabilities.NET_CAPABILITY_NOT_VPN | Android API docs | Present = not VPN. Absent = VPN. Use to label suggested CIDRs. |

---

## Patterns to Mirror

### STATE_MANAGEMENT
// SOURCE: feature/lanscan/src/main/kotlin/.../LanScanViewModel.kt:46-47
```kotlin
private val _uiState = MutableStateFlow(LanScanUiState())
val uiState: StateFlow<LanScanUiState> = _uiState.asStateFlow()
```

### COPY_ON_WRITE
// SOURCE: feature/lanscan/src/main/kotlin/.../LanScanViewModel.kt:66
```kotlin
_uiState.update { it.copy(rangeMode = mode, rangeError = null, error = null) }
```

### HISTORY_DAO
// SOURCE: core/data/src/main/kotlin/.../dao/LanScanHistoryDao.kt:11-12
```kotlin
@Query("SELECT * FROM history_lanscan ORDER BY timestamp DESC LIMIT :limit")
fun getRecent(limit: Int = 50): Flow<List<LanScanHistoryEntry>>
```

### FILTER_CHIP_ROW
// SOURCE: feature/lanscan/src/main/kotlin/.../LanScanScreen.kt:211-223
```kotlin
FilterChip(
    selected = uiState.rangeMode == ScanRangeMode.AUTO,
    onClick = { onRangeModeChanged(ScanRangeMode.AUTO) },
    label = { Text(stringResource(R.string.lanscan_mode_auto)) },
    enabled = !uiState.isScanning,
)
```

### LINK_ADDRESS_IPV4
// SOURCE: feature/lanscan/src/main/kotlin/.../LanScanViewModel.kt:280-282
```kotlin
private fun LinkAddress.isIpv4(): Boolean {
    return address.address.size == 4
}
```

### SAVE_HISTORY
// SOURCE: feature/lanscan/src/main/kotlin/.../LanScanViewModel.kt:175-186
```kotlin
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
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `feature/lanscan/.../model/LanScanUiState.kt` | UPDATE | Add `suggestedNetworks`, `selectedTab`, `historyEntries` fields |
| `feature/lanscan/.../model/SuggestedNetwork.kt` | CREATE | Data class for interface CIDR + label + isVpn |
| `feature/lanscan/.../LanScanViewModel.kt` | UPDATE | Add interface enumeration, history loading, re-run, replace Auto logic |
| `feature/lanscan/.../LanScanScreen.kt` | UPDATE | Add tab row, suggested network chips, history tab content |
| `feature/lanscan/src/main/res/values/strings.xml` | UPDATE | Add new string resources for tabs, history, labels |
| `feature/lanscan/src/test/.../LanScanViewModelTest.kt` | CREATE | Tests for CIDR extraction, history re-run, interface enumeration |

## NOT Building

- Exporting/sharing scan history
- History search or filtering beyond the list
- Deleting individual history entries (just "Clear All")
- Background scan scheduling
- Network change listener that auto-refreshes suggestions (user taps refresh or re-enters screen)

---

## Step-by-Step Tasks

### Task 1: Create SuggestedNetwork model

- **ACTION**: Create a data class to represent a routable interface suggestion
- **IMPLEMENT**:
  ```kotlin
  data class SuggestedNetwork(
      val cidr: String,          // "192.168.1.0/24"
      val ip: String,            // "192.168.1.100"
      val prefixLength: Int,     // 24
      val label: String,         // "Wi-Fi" / "VPN" / "Ethernet"
      val isVpn: Boolean,
  )
  ```
- **MIRROR**: LanDevice data class pattern (simple data class in model package)
- **IMPORTS**: None (plain Kotlin)
- **GOTCHA**: Keep in `feature/lanscan/model/` not `core/data/model/` — this is UI-layer
- **VALIDATE**: Compiles

### Task 2: Extend LanScanUiState

- **ACTION**: Add fields for suggestions, tab selection, and history
- **IMPLEMENT**:
  ```kotlin
  data class LanScanUiState(
      // existing fields unchanged
      val suggestedNetworks: List<SuggestedNetwork> = emptyList(),
      val selectedTab: LanScanTab = LanScanTab.SCAN,
      val historyEntries: List<LanScanHistoryEntry> = emptyList(),
  )

  enum class LanScanTab { SCAN, HISTORY }
  ```
- **MIRROR**: COPY_ON_WRITE pattern
- **IMPORTS**: `LanScanHistoryEntry` from core:data, `SuggestedNetwork` from local model
- **GOTCHA**: `LanScanTab` enum can go in the same file or in `ScanRangeMode.kt`
- **VALIDATE**: Existing code compiles (new fields have defaults)

### Task 3: Add interface enumeration to ViewModel

- **ACTION**: Replace single `activeNetwork` detection with enumeration of all routable IPv4 interfaces
- **IMPLEMENT**:
  ```kotlin
  fun refreshSuggestedNetworks() {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
      val suggestions = cm.allNetworks.mapNotNull { network ->
          val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
          val lp = cm.getLinkProperties(network) ?: return@mapNotNull null
          val linkAddr = lp.linkAddresses.firstOrNull { it.isIpv4() } ?: return@mapNotNull null
          val ip = linkAddr.address.hostAddress ?: return@mapNotNull null
          val prefix = linkAddr.prefixLength
          val isVpn = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
          val label = when {
              isVpn -> "VPN"
              caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
              caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
              else -> "Network"
          }
          val networkAddr = calculateNetworkAddress(ip, prefix)
          SuggestedNetwork(
              cidr = "$networkAddr/$prefix",
              ip = ip,
              prefixLength = prefix,
              label = label,
              isVpn = isVpn,
          )
      }.sortedBy { it.isVpn } // LAN interfaces first, VPN last
      _uiState.update { it.copy(suggestedNetworks = suggestions) }
  }
  ```
  Call from `init {}` block and when scan tab is selected.
- **MIRROR**: LINK_ADDRESS_IPV4 pattern, STATE_MANAGEMENT pattern
- **IMPORTS**: `NetworkCapabilities`, `ConnectivityManager`
- **GOTCHA**: `calculateNetworkAddress` needs to derive the network address from host IP + prefix (e.g., 192.168.1.100/24 -> 192.168.1.0). Extract `SubnetScannerImpl.calculateIpRange` logic or add a small utility. Also need `@Suppress("MissingPermission")` since `allNetworks` requires `ACCESS_NETWORK_STATE` (already declared in manifest).
- **VALIDATE**: With WiFi + VPN active, both appear as suggestions. WiFi-only shows just one.

### Task 4: Add startScanWithCidr method

- **ACTION**: Add a method that accepts a `SuggestedNetwork` and starts scanning it directly
- **IMPLEMENT**:
  ```kotlin
  fun startScanWithCidr(cidr: String) {
      val parsed = parseCidr(cidr) ?: return
      _uiState.update {
          it.copy(
              rangeMode = ScanRangeMode.CUSTOM,
              customRange = cidr,
          )
      }
      startScan()
  }
  ```
  Reuse existing `startScan()` with Custom mode pre-filled.
- **MIRROR**: COPY_ON_WRITE pattern
- **IMPORTS**: None new
- **GOTCHA**: Don't duplicate scan logic. Pre-set state to CUSTOM + CIDR, then call `startScan()` which already handles CUSTOM mode.
- **VALIDATE**: Calling `startScanWithCidr("192.168.1.0/24")` starts a scan on that subnet.

### Task 5: Load and expose history

- **ACTION**: Observe `lanScanHistoryDao.getRecent()` and surface in UI state
- **IMPLEMENT**:
  ```kotlin
  init {
      refreshSuggestedNetworks()
      viewModelScope.launch {
          lanScanHistoryDao.getRecent().collect { entries ->
              _uiState.update { it.copy(historyEntries = entries) }
          }
      }
  }
  ```
- **MIRROR**: HISTORY_DAO pattern (Flow collection)
- **IMPORTS**: Already has `lanScanHistoryDao` injected
- **GOTCHA**: `getRecent()` returns `Flow` — use `.collect` in a `viewModelScope.launch`. History auto-updates after each scan since `saveToHistory()` inserts and the Flow re-emits.
- **VALIDATE**: After a scan completes, History tab shows the new entry.

### Task 6: Add tab selection and clear history

- **ACTION**: Add tab switching and history clearing to ViewModel
- **IMPLEMENT**:
  ```kotlin
  fun selectTab(tab: LanScanTab) {
      _uiState.update { it.copy(selectedTab = tab) }
      if (tab == LanScanTab.SCAN) refreshSuggestedNetworks()
  }

  fun clearHistory() {
      viewModelScope.launch { lanScanHistoryDao.deleteAll() }
  }
  ```
- **MIRROR**: `onRangeModeChanged` pattern (simple state update)
- **IMPORTS**: None new
- **GOTCHA**: Refresh suggestions when returning to Scan tab so interface list is current
- **VALIDATE**: Tab switches between Scan and History views

### Task 7: Update LanScanScreen with tabs and suggestions

- **ACTION**: Replace Auto/Custom chip row with a TabRow (Scan / History) and a suggested-networks chip row
- **IMPLEMENT**:
  - Add `TabRow` with `Tab` items for SCAN and HISTORY
  - In SCAN tab: show suggested network chips (each as `SuggestionChip` or `AssistChip` with label like "Wi-Fi 192.168.1.0/24"), then a "Custom..." chip that toggles the text field
  - In HISTORY tab: show `LazyColumn` of history entries, each as a `Card` with subnet, device count, relative timestamp, and tap-to-rescan
  - Add a "Clear History" `TextButton` at the bottom of the history list
- **MIRROR**: FILTER_CHIP_ROW pattern for chips, DeviceCard pattern for history cards
- **IMPORTS**: `TabRow`, `Tab`, `RelativeDateTimeFormatter` (or manual "X ago" logic)
- **GOTCHA**: Keep FAB visible on Scan tab, hide on History tab. Disable chips during active scan. Format timestamps as relative ("2h ago", "yesterday").
- **VALIDATE**: Visual: both tabs render, chips show all interfaces, history entries are tappable

### Task 8: Add string resources

- **ACTION**: Add new strings for tabs, suggestions, and history UI
- **IMPLEMENT**:
  ```xml
  <string name="lanscan_tab_scan">Scan</string>
  <string name="lanscan_tab_history">History</string>
  <string name="lanscan_suggested_networks">Suggested networks</string>
  <string name="lanscan_custom_chip">Custom…</string>
  <string name="lanscan_history_empty">No scan history yet</string>
  <string name="lanscan_history_devices">%1$d devices</string>
  <string name="lanscan_history_clear">Clear History</string>
  <string name="lanscan_history_clear_confirm">Clear all scan history?</string>
  <string name="lanscan_time_just_now">Just now</string>
  <string name="lanscan_time_minutes_ago">%1$d min ago</string>
  <string name="lanscan_time_hours_ago">%1$dh ago</string>
  <string name="lanscan_time_days_ago">%1$dd ago</string>
  ```
- **MIRROR**: Existing string resource naming pattern (`lanscan_` prefix)
- **IMPORTS**: N/A
- **GOTCHA**: Use plurals for device count if needed, but existing pattern uses simple format strings
- **VALIDATE**: All `stringResource()` calls resolve

### Task 9: Add relative time formatting utility

- **ACTION**: Create a small composable or function to format timestamps as relative time
- **IMPLEMENT**:
  ```kotlin
  @Composable
  fun relativeTimeText(timestampMs: Long): String {
      val now = System.currentTimeMillis()
      val diff = now - timestampMs
      return when {
          diff < 60_000 -> stringResource(R.string.lanscan_time_just_now)
          diff < 3_600_000 -> stringResource(R.string.lanscan_time_minutes_ago, (diff / 60_000).toInt())
          diff < 86_400_000 -> stringResource(R.string.lanscan_time_hours_ago, (diff / 3_600_000).toInt())
          else -> stringResource(R.string.lanscan_time_days_ago, (diff / 86_400_000).toInt())
      }
  }
  ```
  Place inside `LanScanScreen.kt` as a private composable.
- **MIRROR**: `gradeDescription()` pattern from PostureScreen (composable returning String)
- **IMPORTS**: None new
- **GOTCHA**: `System.currentTimeMillis()` in a composable won't recompose on clock tick, which is fine — it updates on navigation/recomposition
- **VALIDATE**: Shows "Just now" for recent, "2h ago" for older

### Task 10: Write tests

- **ACTION**: Test interface enumeration logic and history re-run flow
- **IMPLEMENT**:
  - `parseCidr` existing tests (already in companion, add for new edge cases)
  - `calculateNetworkAddress` unit test (192.168.1.100/24 -> 192.168.1.0)
  - History loading: fake DAO returns entries, verify `historyEntries` populated
  - `startScanWithCidr`: verify state updates to CUSTOM mode with correct CIDR
- **MIRROR**: PostureViewModelTest pattern (fakes, Turbine, StandardTestDispatcher)
- **IMPORTS**: JUnit 5, Turbine, kotlinx-coroutines-test
- **GOTCHA**: Can't test `ConnectivityManager.allNetworks` in unit tests — extract interface enumeration into a testable provider or test at integration level. Focus unit tests on pure logic (CIDR parsing, network address calculation, state transitions).
- **VALIDATE**: `./gradlew :feature:lanscan:testDebugUnitTest` passes

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| calculateNetworkAddress 192.168.1.100/24 | "192.168.1.100", 24 | "192.168.1.0" | No |
| calculateNetworkAddress 10.0.0.5/8 | "10.0.0.5", 8 | "10.0.0.0" | Yes — large subnet |
| calculateNetworkAddress 172.16.5.130/28 | "172.16.5.130", 28 | "172.16.5.128" | Yes — non-octet boundary |
| startScanWithCidr sets state | "192.168.1.0/24" | rangeMode=CUSTOM, customRange set | No |
| startScanWithCidr invalid CIDR | "not-a-cidr" | No state change, no scan | Yes |
| history entries loaded on init | 3 entries in DAO | historyEntries.size == 3 | No |
| empty history | 0 entries in DAO | historyEntries.isEmpty() | Yes |
| suggestedNetworks sorted LAN first | VPN + WiFi | WiFi first, VPN second | No |

### Edge Cases Checklist

- [ ] No network interfaces active (airplane mode) — empty suggestions, show error
- [ ] VPN-only (no WiFi/Ethernet) — VPN shown as only suggestion
- [ ] Multiple WiFi interfaces (unlikely but possible with tethering) — both shown
- [ ] IPv6-only interface — filtered out (IPv4 only)
- [ ] History with 50+ entries — limited by DAO LIMIT 50
- [ ] Re-running a scan for a subnet that's no longer reachable — handled by existing error flow

---

## Validation Commands

### Static Analysis
```bash
./gradlew :feature:lanscan:compileDebugKotlin
```
EXPECT: Zero errors

### Unit Tests
```bash
./gradlew :feature:lanscan:testDebugUnitTest
```
EXPECT: All tests pass

### Full Build
```bash
./gradlew assembleDebug
```
EXPECT: BUILD SUCCESSFUL

### Manual Validation

- [ ] Open LAN Scan — see Scan and History tabs
- [ ] With WiFi on: see WiFi CIDR chip
- [ ] With WiFi + VPN: see both chips, WiFi first
- [ ] Tap a suggestion chip — scan starts immediately for that CIDR
- [ ] Tap "Custom..." chip — shows text field
- [ ] Complete a scan — switch to History tab, see the entry
- [ ] Tap a history entry — switches to Scan tab and starts scanning that CIDR
- [ ] Tap "Clear History" — entries removed
- [ ] No network — empty suggestions, appropriate error

---

## Acceptance Criteria

- [ ] All routable IPv4 interfaces shown as suggestion chips
- [ ] LAN interfaces sorted before VPN interfaces
- [ ] Tapping a suggestion starts scan immediately
- [ ] Custom CIDR entry still available
- [ ] History tab shows past scans with subnet, device count, relative time
- [ ] Tapping history entry re-runs scan with that CIDR
- [ ] Clear History button works
- [ ] All tests pass
- [ ] Build succeeds

## Completion Checklist

- [ ] Code follows discovered patterns (copy-on-write, Flow collection, DAO)
- [ ] Error handling matches codebase style
- [ ] Tests follow Turbine + fakes pattern
- [ ] All strings extracted to resources
- [ ] No hardcoded values
- [ ] No unnecessary scope additions

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `allNetworks` returns unexpected interfaces (cellular, loopback) | Medium | Low | Filter by TRANSPORT_WIFI, TRANSPORT_ETHERNET, TRANSPORT_VPN only |
| SecurityException on allNetworks without permission | Low | Medium | ACCESS_NETWORK_STATE already in manifest; wrap in try-catch |
| Network address calculation off-by-one at prefix boundaries | Low | High | Unit test /28, /24, /16, /8 boundaries explicitly |

## Notes

- The `ScanRangeMode.AUTO` enum value can be kept but its behavior changes: instead of auto-picking one network, it means "use suggestion chips." Or remove AUTO entirely and always show suggestions. Simpler to keep AUTO as the default and show suggestions when in AUTO mode.
- The `ssid` field in `LanScanHistoryEntry` is currently always null. Could populate it from `WifiManager` but that requires location permission. Leave null for now — the subnet string is sufficient context.
- `calculateNetworkAddress` utility: extract from `SubnetScannerImpl` or write standalone. The scanner already does this math internally (lines 71-73 of SubnetScannerImpl) but it's private. A small standalone function is cleaner than exposing scanner internals.
