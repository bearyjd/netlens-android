# Plan: LAN Scan Custom IP Range

## Summary
Add the ability to manually specify a custom IP range (CIDR or start–end) for LAN scan, so users on VPNs or multi-homed networks can scan subnets other than the auto-detected active network. The auto-detect behavior remains the default; custom range is an opt-in override. This lays groundwork for similar overrides in other features (ping, port scan, traceroute) but scopes implementation to LAN scan only.

## User Story
As a network engineer on a VPN, I want to specify a custom IP range for LAN scan, so that I can scan my actual LAN subnet instead of the VPN tunnel interface.

## Problem → Solution
**Current**: LAN scan always uses the active network's IPv4 address/prefix from `ConnectivityManager`. On a VPN, the active network is the VPN tunnel, so the real LAN is unreachable.

**Desired**: User can toggle between auto-detect (default) and a manual CIDR input (e.g. `192.168.1.0/24`). When manual mode is active, the scan uses the user-provided range instead of querying `ConnectivityManager`.

## Metadata
- **Complexity**: Medium
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 8 (4 updated, 4 with minor touches)

---

## UX Design

### Before
```
┌──────────────────────────────────────────┐
│  [←]  LAN Scan                    [Sort] │
│                                          │
│  Subnet: 10.8.0.1/24  (VPN tunnel)      │
│  Found 2 devices...                      │
│                                          │
│  ┌─ 10.8.0.1 ─────────────────────────┐ │
│  │  gateway   PING   2ms              │ │
│  └────────────────────────────────────┘ │
│                                          │
│  User CANNOT scan 192.168.1.0/24 LAN    │
│                                    [FAB] │
└──────────────────────────────────────────┘
```

### After
```
┌──────────────────────────────────────────┐
│  [←]  LAN Scan                    [Sort] │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │ [Auto-detect ▾]  [Custom range] │    │
│  └──────────────────────────────────┘    │
│                                          │
│  ┌──────────────────────────────────┐    │
│  │ 192.168.1.0/24          [CIDR]  │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Subnet: 192.168.1.0/24                 │
│  Found 12 devices...                     │
│                                          │
│  ┌─ 192.168.1.1 ───────────────────────┐│
│  │  router   PING   1ms               ││
│  └─────────────────────────────────────┘│
│                                    [FAB] │
└──────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Scan trigger | FAB taps → auto-detect subnet → scan | FAB taps → check mode → auto-detect OR use custom range → scan | Default unchanged |
| Subnet display | Shows auto-detected CIDR | Shows whichever CIDR is active (auto or custom) | Label updates dynamically |
| Range input | None | OutlinedTextField for CIDR notation | Only visible when "Custom" selected |
| Mode toggle | None | Two FilterChips: Auto-detect / Custom | Persisted in UiState only (not across sessions) |
| Validation | None | Validates CIDR format before scan | Shows inline error on bad input |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `feature/lanscan/.../LanScanViewModel.kt` | 55-96 | `startScan()` — where auto-detect happens, must be modified |
| P0 | `feature/lanscan/.../LanScanScreen.kt` | 77-213 | `LanScanContent` — where UI input goes |
| P0 | `feature/lanscan/.../model/LanScanUiState.kt` | 1-10 | State data class to extend |
| P0 | `feature/lanscan/.../engine/SubnetScannerImpl.kt` | 63-82 | `calculateIpRange()` — accepts subnet+prefix, reusable as-is |
| P1 | `feature/lanscan/.../engine/SubnetScanner.kt` | 1-11 | Interface contract — no change needed |
| P1 | `feature/ping/.../PingScreen.kt` | 144-164 | Input field pattern to mirror |
| P2 | `feature/portscan/.../PortScanScreen.kt` | 113-159 | FilterChip preset pattern to mirror |
| P2 | `feature/lanscan/.../LanScanModule.kt` | 1-28 | DI module — no change needed |

## External Documentation

No external research needed — feature uses established internal patterns (OutlinedTextField, FilterChip, CIDR parsing via bitwise math already in SubnetScannerImpl).

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: LanScanViewModel.kt:29,40-41,48
enum class SortOrder { IP, LATENCY }

private val _uiState = MutableStateFlow(LanScanUiState())
val uiState: StateFlow<LanScanUiState> = _uiState.asStateFlow()

fun setSortOrder(order: SortOrder) { ... }
```
New enum: `ScanRangeMode { AUTO, CUSTOM }` — same file, same style.
New methods: `onRangeModeChanged(mode: ScanRangeMode)`, `onCustomRangeChanged(range: String)`.

### STATE_UPDATE
```kotlin
// SOURCE: LanScanViewModel.kt:50-52,86-95
_uiState.update { state ->
    state.copy(devices = state.devices.sortedWith(order.comparator()))
}
```
All state updates use `_uiState.update { it.copy(...) }` — immutable copy-on-write.

### ERROR_HANDLING
```kotlin
// SOURCE: LanScanViewModel.kt:65-68,73-76
if (linkProperties == null) {
    _uiState.update { it.copy(error = "No active network connection") }
    return
}
```
Errors are simple strings set on `UiState.error`. Validation errors follow same pattern.

### INPUT_FIELD
```kotlin
// SOURCE: PingScreen.kt:148-155
OutlinedTextField(
    value = state.host,
    onValueChange = onHostChange,
    label = { Text(stringResource(R.string.ping_label_host)) },
    placeholder = { Text(stringResource(R.string.ping_placeholder_host)) },
    singleLine = true,
    modifier = Modifier.weight(1f),
)
```

### FILTER_CHIP_SELECTOR
```kotlin
// SOURCE: PortScanScreen.kt:141-158
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    FilterChip(
        selected = selectedPreset == PRESET_COMMON,
        onClick = { selectedPreset = PRESET_COMMON },
        label = { Text(stringResource(R.string.portscan_chip_common)) },
    )
    FilterChip(
        selected = selectedPreset == PRESET_ALL,
        onClick = { selectedPreset = PRESET_ALL },
        label = { Text(stringResource(R.string.portscan_chip_all)) },
    )
}
```

### SCREEN_COMPOSABLE_WIRING
```kotlin
// SOURCE: LanScanScreen.kt:58-75
@Composable
fun LanScanScreen(
    onBack: () -> Unit = {},
    viewModel: LanScanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    LanScanContent(
        onBack = onBack,
        uiState = uiState,
        sortOrder = sortOrder,
        onStartScan = viewModel::startScan,
        onCancelScan = viewModel::cancelScan,
        onSortOrderChange = viewModel::setSortOrder,
    )
}
```
New callbacks: `onRangeModeChanged`, `onCustomRangeChanged` wired via `viewModel::` references.

### IP_RANGE_CALCULATION
```kotlin
// SOURCE: SubnetScannerImpl.kt:63-82
internal fun calculateIpRange(subnet: String, prefixLength: Int): List<String> {
    val parts = subnet.split(".")
    if (parts.size != 4) return emptyList()
    val ipInt = parts.fold(0L) { acc, part ->
        (acc shl 8) or (part.toIntOrNull()?.toLong() ?: return emptyList())
    }
    val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
    val network = ipInt and mask
    val broadcast = network or mask.inv() and 0xFFFFFFFFL
    val hostCount = (broadcast - network - 1).toInt()
    if (hostCount <= 0 || hostCount > MAX_HOSTS) return emptyList()
    return (1..hostCount).map { offset ->
        val addr = network + offset
        "${(addr shr 24) and 0xFF}.${(addr shr 16) and 0xFF}.${(addr shr 8) and 0xFF}.${addr and 0xFF}"
    }
}
```
This function already accepts arbitrary subnet/prefix — no changes needed in the engine.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `feature/lanscan/.../model/LanScanUiState.kt` | UPDATE | Add `rangeMode`, `customRange`, `rangeError` fields |
| `feature/lanscan/.../LanScanViewModel.kt` | UPDATE | Add `ScanRangeMode` enum, `onRangeModeChanged`, `onCustomRangeChanged`, CIDR validation, modify `startScan()` to branch on mode |
| `feature/lanscan/.../LanScanScreen.kt` | UPDATE | Add mode selector chips and CIDR input field above scan results |
| `feature/lanscan/src/main/res/values/strings.xml` | UPDATE | Add new string resources for mode labels, input hint, validation errors |
| `feature/lanscan/src/test/.../SubnetScannerImplTest.kt` | UPDATE (or CREATE if missing) | Add tests for CIDR parsing validation edge cases |
| `feature/lanscan/src/test/.../LanScanViewModelTest.kt` | UPDATE (or CREATE if missing) | Add tests for custom range mode behavior |

## NOT Building

- Persistent storage of custom range preference (DataStore) — keep it session-only for now
- Custom range on other features (ping, port scan, traceroute) — this plan is LAN scan only; other features can follow the same pattern later
- Start IP / End IP range format — CIDR only for v1 (simpler, standard)
- IPv6 custom range support — existing scanner is IPv4-only
- Network interface picker UI — more complex, separate feature

---

## Step-by-Step Tasks

### Task 1: Extend LanScanUiState with range mode fields
- **ACTION**: Add three fields to `LanScanUiState` data class
- **IMPLEMENT**:
  ```kotlin
  data class LanScanUiState(
      val devices: List<LanDevice> = emptyList(),
      val isScanning: Boolean = false,
      val subnetInfo: String = "",
      val progress: Float = 0f,
      val error: String? = null,
      val deviceCount: Int = 0,
      val rangeMode: ScanRangeMode = ScanRangeMode.AUTO,
      val customRange: String = "",
      val rangeError: String? = null,
  )
  ```
  Import `ScanRangeMode` from the ViewModel file (or define it in the model package).
- **MIRROR**: STATE_UPDATE pattern — all fields have sensible defaults
- **IMPORTS**: `com.ventoux.netlens.feature.lanscan.model.ScanRangeMode`
- **GOTCHA**: `rangeError` is separate from `error` — `error` is for scan-time failures, `rangeError` is for input validation
- **VALIDATE**: Build compiles; all existing callers of `LanScanUiState()` still work because all new fields have defaults

### Task 2: Add ScanRangeMode enum and ViewModel methods
- **ACTION**: Add `ScanRangeMode` enum in the model package. Add `onRangeModeChanged()`, `onCustomRangeChanged()`, and a private `parseCidr()` validation function to `LanScanViewModel`.
- **IMPLEMENT**:
  Create `feature/lanscan/.../model/ScanRangeMode.kt`:
  ```kotlin
  package com.ventoux.netlens.feature.lanscan.model
  
  enum class ScanRangeMode { AUTO, CUSTOM }
  ```
  
  In `LanScanViewModel`, add:
  ```kotlin
  fun onRangeModeChanged(mode: ScanRangeMode) {
      _uiState.update { it.copy(rangeMode = mode, rangeError = null) }
  }

  fun onCustomRangeChanged(range: String) {
      _uiState.update { it.copy(customRange = range, rangeError = null) }
  }
  ```
  
  Add private CIDR parser:
  ```kotlin
  private fun parseCidr(cidr: String): Pair<String, Int>? {
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
  ```
- **MIRROR**: NAMING_CONVENTION — enum in model package like `DiscoveryMethod`, camelCase methods
- **IMPORTS**: `com.ventoux.netlens.feature.lanscan.model.ScanRangeMode`
- **GOTCHA**: Prefix range 16–30 prevents scanning huge ranges (>1024 hosts already capped in `SubnetScannerImpl`). Prefix <16 = 65k+ hosts = unreasonable. Prefix >30 = 1 or 0 hosts = useless.
- **VALIDATE**: Build compiles; calling `onRangeModeChanged(CUSTOM)` then `onCustomRangeChanged("192.168.1.0/24")` updates state correctly

### Task 3: Modify startScan() to support custom range
- **ACTION**: Branch on `rangeMode` at the top of `startScan()`. When CUSTOM, validate and use the custom CIDR instead of querying ConnectivityManager.
- **IMPLEMENT**:
  Replace the top of `startScan()` (lines 55-84) with:
  ```kotlin
  fun startScan() {
      if (scanJob?.isActive == true) return

      val (subnet, prefixLength, subnetInfo) = when (_uiState.value.rangeMode) {
          ScanRangeMode.CUSTOM -> {
              val parsed = parseCidr(_uiState.value.customRange)
              if (parsed == null) {
                  _uiState.update { it.copy(rangeError = context.getString(R.string.lanscan_error_invalid_cidr)) }
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
      // ... rest of startScan() unchanged from line 86 onward
  ```
- **MIRROR**: ERROR_HANDLING — set error string on state and return early
- **IMPORTS**: `com.ventoux.netlens.feature.lanscan.model.ScanRangeMode` (already imported from Task 2)
- **GOTCHA**: The destructured Triple must be `val (subnet, prefixLength, subnetInfo)` — Kotlin destructuring works with Triple. Make sure `rangeError` is cleared on valid scan start in the state reset block (line 86-95 equivalent).
- **VALIDATE**: Build compiles. Manual test: set custom range "192.168.1.0/24" → scan uses that range. Set invalid "abc" → shows validation error. Auto mode → existing behavior unchanged.

### Task 4: Update LanScanScreen with mode selector and input field
- **ACTION**: Add a mode selector (two FilterChips) and a conditional CIDR input field to `LanScanContent`, above the existing subnet info row.
- **IMPLEMENT**:
  Add new parameters to `LanScanContent`:
  ```kotlin
  @Composable
  private fun LanScanContent(
      onBack: () -> Unit,
      uiState: LanScanUiState,
      sortOrder: SortOrder,
      onStartScan: () -> Unit,
      onCancelScan: () -> Unit,
      onSortOrderChange: (SortOrder) -> Unit,
      onRangeModeChanged: (ScanRangeMode) -> Unit,
      onCustomRangeChanged: (String) -> Unit,
  )
  ```
  
  Wire from `LanScanScreen`:
  ```kotlin
  onRangeModeChanged = viewModel::onRangeModeChanged,
  onCustomRangeChanged = viewModel::onCustomRangeChanged,
  ```
  
  Insert UI after `innerPadding` Column opening, before the AnimatedVisibility:
  ```kotlin
  // Range mode selector
  Row(
      modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
      FilterChip(
          selected = uiState.rangeMode == ScanRangeMode.AUTO,
          onClick = { onRangeModeChanged(ScanRangeMode.AUTO) },
          label = { Text(stringResource(R.string.lanscan_mode_auto)) },
          enabled = !uiState.isScanning,
      )
      FilterChip(
          selected = uiState.rangeMode == ScanRangeMode.CUSTOM,
          onClick = { onRangeModeChanged(ScanRangeMode.CUSTOM) },
          label = { Text(stringResource(R.string.lanscan_mode_custom)) },
          enabled = !uiState.isScanning,
      )
  }

  // Custom range input (visible only in CUSTOM mode)
  AnimatedVisibility(visible = uiState.rangeMode == ScanRangeMode.CUSTOM) {
      OutlinedTextField(
          value = uiState.customRange,
          onValueChange = onCustomRangeChanged,
          label = { Text(stringResource(R.string.lanscan_label_custom_range)) },
          placeholder = { Text(stringResource(R.string.lanscan_placeholder_cidr)) },
          isError = uiState.rangeError != null,
          supportingText = uiState.rangeError?.let { error -> { Text(error) } },
          singleLine = true,
          enabled = !uiState.isScanning,
          keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Uri,
              imeAction = ImeAction.Done,
          ),
          modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
      )
  }
  ```
- **MIRROR**: INPUT_FIELD (PingScreen pattern), FILTER_CHIP_SELECTOR (PortScanScreen pattern)
- **IMPORTS**: Add to LanScanScreen.kt:
  - `androidx.compose.material3.FilterChip`
  - `androidx.compose.foundation.text.KeyboardOptions`
  - `androidx.compose.foundation.text.KeyboardActions` (if adding imeAction handler)
  - `androidx.compose.ui.text.input.KeyboardType`
  - `androidx.compose.ui.text.input.ImeAction`
  - `com.ventoux.netlens.feature.lanscan.model.ScanRangeMode`
- **GOTCHA**: `FilterChip` needs the M3 import, not M2. The `enabled = !uiState.isScanning` prevents mode switching mid-scan. `AnimatedVisibility` is already imported in this file.
- **VALIDATE**: Build compiles. Visual check: chips appear, toggling Custom shows the text field, typing updates state, error text appears below field in red when `rangeError` is set.

### Task 5: Add string resources
- **ACTION**: Add new string resources to `feature/lanscan/src/main/res/values/strings.xml`
- **IMPLEMENT**:
  ```xml
  <string name="lanscan_mode_auto">Auto-detect</string>
  <string name="lanscan_mode_custom">Custom range</string>
  <string name="lanscan_label_custom_range">IP Range (CIDR)</string>
  <string name="lanscan_placeholder_cidr">192.168.1.0/24</string>
  <string name="lanscan_error_invalid_cidr">Invalid CIDR — use format like 192.168.1.0/24 (prefix /16–/30)</string>
  ```
- **MIRROR**: Existing strings use `lanscan_` prefix, descriptive suffixes
- **IMPORTS**: N/A
- **GOTCHA**: Keep the error message concise — it renders as `supportingText` under the field, limited width
- **VALIDATE**: No duplicate resource names, build compiles

### Task 6: Add unit tests for CIDR parsing
- **ACTION**: Add tests covering parseCidr validation and the custom range scan path
- **IMPLEMENT**:
  Test the `parseCidr` logic indirectly through `SubnetScannerImpl.calculateIpRange` (already `internal` visibility) and add ViewModel tests:

  `feature/lanscan/src/test/kotlin/com.ventoux.netlens/feature/lanscan/CidrValidationTest.kt`:
  ```kotlin
  class CidrValidationTest {
      @Test
      fun `valid /24 produces 254 hosts`() {
          val ips = SubnetScannerImpl.calculateIpRange("192.168.1.0", 24)
          assertEquals(254, ips.size)
          assertEquals("192.168.1.1", ips.first())
          assertEquals("192.168.1.254", ips.last())
      }

      @Test
      fun `valid /28 produces 14 hosts`() {
          val ips = SubnetScannerImpl.calculateIpRange("10.0.0.0", 28)
          assertEquals(14, ips.size)
      }

      @Test
      fun `non-dotted-quad returns empty`() {
          val ips = SubnetScannerImpl.calculateIpRange("not-an-ip", 24)
          assertTrue(ips.isEmpty())
      }

      @Test
      fun `prefix 32 returns empty (0 hosts)`() {
          val ips = SubnetScannerImpl.calculateIpRange("192.168.1.1", 32)
          assertTrue(ips.isEmpty())
      }

      @Test
      fun `prefix 8 returns empty (exceeds MAX_HOSTS)`() {
          val ips = SubnetScannerImpl.calculateIpRange("10.0.0.0", 8)
          assertTrue(ips.isEmpty())
      }
  }
  ```
- **MIRROR**: JUnit 5 backtick-quoted test names, no mocking framework
- **IMPORTS**: `org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Assertions.*`, `com.ventoux.netlens.feature.lanscan.engine.SubnetScannerImpl`
- **GOTCHA**: `calculateIpRange` is `internal` — test must be in the same module (which it is, under `src/test/`). The `MAX_HOSTS = 1024` cap means /8 returns empty.
- **VALIDATE**: `./gradlew :feature:lanscan:testDebugUnitTest` passes

### Task 7: Disable FAB when custom range is empty
- **ACTION**: Update the FAB enabled logic so that in CUSTOM mode, the scan button is only enabled when `customRange` is non-blank
- **IMPLEMENT**: In `LanScanContent`, update the FAB:
  ```kotlin
  val canStartScan = when {
      uiState.isScanning -> true  // shows cancel icon
      uiState.rangeMode == ScanRangeMode.CUSTOM -> uiState.customRange.isNotBlank()
      else -> true
  }

  FloatingActionButton(
      onClick = if (uiState.isScanning) onCancelScan else onStartScan,
      // No built-in "enabled" on FAB — use containerColor to indicate disabled
  )
  ```
  Actually, M3 `FloatingActionButton` doesn't have an `enabled` param. Instead, guard in the `onClick`:
  ```kotlin
  FloatingActionButton(
      onClick = {
          if (uiState.isScanning) onCancelScan()
          else if (uiState.rangeMode != ScanRangeMode.CUSTOM || uiState.customRange.isNotBlank()) onStartScan()
      },
  )
  ```
- **MIRROR**: Existing FAB pattern at LanScanScreen.kt:136-149
- **IMPORTS**: None additional
- **GOTCHA**: FAB doesn't have an `enabled` parameter. Use onClick guard. Could optionally dim the FAB via `containerColor` with alpha, but keep it simple for v1 — just make the tap a no-op.
- **VALIDATE**: Tapping FAB with empty custom range does nothing. Tapping with valid range starts scan.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `valid /24 produces 254 hosts` | "192.168.1.0", 24 | 254 IPs, first=.1, last=.254 | No |
| `valid /28 produces 14 hosts` | "10.0.0.0", 28 | 14 IPs | No |
| `non-dotted-quad returns empty` | "not-an-ip", 24 | empty list | Yes |
| `prefix 32 returns empty` | "192.168.1.1", 32 | empty list | Yes |
| `prefix 8 exceeds max hosts` | "10.0.0.0", 8 | empty list | Yes |
| `parseCidr rejects no slash` | "192.168.1.0" | null | Yes |
| `parseCidr rejects prefix >30` | "192.168.1.0/31" | null | Yes |
| `parseCidr rejects prefix <16` | "10.0.0.0/15" | null | Yes |
| `parseCidr accepts valid` | "192.168.1.0/24" | ("192.168.1.0", 24) | No |
| `parseCidr trims whitespace` | " 192.168.1.0/24 " | ("192.168.1.0", 24) | Yes |
| `parseCidr rejects octet >255` | "192.168.1.256/24" | null | Yes |

### Edge Cases Checklist
- [x] Empty input — FAB no-op, no crash
- [x] Maximum size input (e.g. /16 = 65534 hosts) — clamped to 1024 by `MAX_HOSTS`
- [x] Invalid CIDR formats — parseCidr returns null, rangeError shown
- [x] Switching modes mid-idle — state updates, no side effects
- [x] Switching back to AUTO clears rangeError
- [x] VPN active with custom range — scan uses custom range, ignores VPN tunnel
- [x] No network connection + CUSTOM mode — scan proceeds (doesn't need ConnectivityManager)

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
EXPECT: No regressions

### Build Verification
```bash
./gradlew assembleDebug
```
EXPECT: APK builds successfully

### Manual Validation
- [ ] Launch app → LAN Scan → see "Auto-detect" chip selected by default
- [ ] Tap "Custom range" chip → CIDR input field appears with animation
- [ ] Enter "192.168.1.0/24" → tap scan FAB → scan runs against that range
- [ ] Enter "garbage" → tap scan FAB → red error text appears below field
- [ ] Switch back to "Auto-detect" → error clears, input field hides
- [ ] Tap scan FAB in Auto mode → existing auto-detect behavior works
- [ ] On VPN: set custom range to real LAN CIDR → scan discovers LAN devices
- [ ] Chips disabled during active scan
- [ ] Input field disabled during active scan

---

## Acceptance Criteria
- [ ] All tasks completed
- [ ] All validation commands pass
- [ ] Tests written and passing
- [ ] No type errors
- [ ] No lint errors
- [ ] Auto-detect mode unchanged (zero regression)
- [ ] Custom mode validates CIDR and scans user-specified range
- [ ] Mode selector and input follow existing M3 patterns

## Completion Checklist
- [ ] Code follows discovered patterns (FilterChip, OutlinedTextField, StateFlow copy-on-write)
- [ ] Error handling matches codebase style (string on UiState, early return)
- [ ] Tests follow test patterns (JUnit 5, backtick names, no mocking)
- [ ] No hardcoded values (all user-facing strings in strings.xml)
- [ ] No unnecessary scope additions (no DataStore, no other features)
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Custom range on VPN may still route through VPN tunnel (scan traffic goes over VPN, not LAN) | Medium | High | This is an OS routing issue, not app-level. Document that split-tunneling or route config may be needed. Consider adding a note in the UI. |
| User enters huge range like /16 | Low | Low | Already mitigated by `MAX_HOSTS = 1024` in SubnetScannerImpl — range is silently capped. `parseCidr` also rejects prefix < 16. |
| FilterChip import collision (M2 vs M3) | Low | Low | Use `androidx.compose.material3.FilterChip` explicitly |

## Notes
- `parseCidr` is intentionally restrictive (prefix 16–30) to keep scans reasonable. The engine's `MAX_HOSTS = 1024` is the hard safety net.
- The `SubnetScanner.scan(subnet, prefixLength)` interface doesn't need changes — it already accepts arbitrary subnet/prefix pairs. The change is entirely in how the ViewModel obtains those values.
- Future expansion: other features (ping, port scan, traceroute) that take a host input could add an "or scan LAN range" toggle using the same `ScanRangeMode` enum and `parseCidr` validation. Those would be separate plans.
- No DataStore persistence for the custom range is intentional — it's a session-level override. If users want persistence, that's a separate feature request.
