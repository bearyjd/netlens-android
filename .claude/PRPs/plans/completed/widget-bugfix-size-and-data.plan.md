# Plan: Widget Bugfix — Size and Data Population

## Summary
Fix two widget bugs: (1) the dashboard widget cannot be resized to 4x1 because the provider XMLs lack `minResizeWidth`/`minResizeHeight` attributes, and (2) the widget perpetually shows "Not tested"/"Not scanned" because `WidgetRefreshWorker` only writes 4 of 20+ DataStore fields — security score, encryption, public IP, device count, and latency are never populated.

## User Story
As a NetLens user with a home screen widget,
I want my widget to show live security posture data and resize to a single 4x1 row,
So that I can see my network status at a glance without opening the app.

## Problem → Solution
**Bug 1**: Dashboard widget locked to 4x2 minimum — no `minResizeWidth`/`minResizeHeight` → Add resize attributes to all three provider XMLs so dashboard can shrink to 4x1.
**Bug 2**: Widget DataStore fields for score/IP/encryption/devices/latency are orphaned — never written → Expand `WidgetRefreshWorker` to gather and persist all widget data on each refresh.

## Metadata
- **Complexity**: Medium
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 8

---

## UX Design

### Before
```
┌─────────────────────────────────────┐
│ Dashboard widget (4x2 only)         │
│ Score: ?    Network: MyWiFi         │
│ IP: (empty) Speed: "Not tested"    │
│ Alerts: "✓ No threats detected"    │
│ Stats: "— ms · 0 devices"         │
│ Footer: "Not scanned"              │
└─────────────────────────────────────┘
Widget can't resize below 4x2.
All data except SSID/connectivity is blank.
```

### After
```
┌─────────────────────────────────────┐
│ Dashboard widget (resizable 4x1→4x2)│
│ Score: B    Network: MyWiFi WPA3 ✓ │
│ IP: 203.0.113.42 🇺🇸               │
│ Speed: "Not tested" (still needs   │
│   manual speed test in app)         │
│ Stats: "12 ms · 8 devices"        │
│ Footer: "Scanned 2m"              │
└─────────────────────────────────────┘
Widget resizable down to 4x1. Score, encryption,
IP, device count, and latency populate on refresh.
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Widget resize | Min 4x2 dashboard, 2x2 standard, 2x1 compact | Dashboard shrinks to 4x1, standard to 2x1, compact to 1x1 | Uses minResizeWidth/minResizeHeight |
| Widget refresh tap | Writes only connectivity/SSID | Gathers score, encryption, IP, devices, latency | Full data pipeline |
| Network change callback | Same partial refresh | Same but full data refresh | Already triggers enqueueWidgetRefresh |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 (critical) | `widget/src/main/kotlin/.../WidgetRefreshWorker.kt` | all | Worker to expand — currently only writes 4 fields |
| P0 (critical) | `widget/src/main/kotlin/.../WidgetStateDefinition.kt` | 15-60 | All DataStore keys and toWidgetState() mapping |
| P0 (critical) | `widget/src/main/res/xml/widget_dashboard_info.xml` | all | Missing resize attributes |
| P1 (important) | `widget/src/main/kotlin/.../WidgetState.kt` | all | Data class with hasScore/hasSpeed/etc. booleans |
| P1 (important) | `feature/posture/src/main/kotlin/.../PostureScoreEngine.kt` | 10-38 | Scoring logic to replicate in widget |
| P1 (important) | `feature/posture/src/main/kotlin/.../WifiSecurityProvider.kt` | 16-73 | Encryption detection to replicate in widget |
| P2 (reference) | `widget/src/main/kotlin/.../WidgetRefresh.kt` | all | enqueueWidgetRefresh, network callback |
| P2 (reference) | `widget/src/main/kotlin/.../model/WidgetIpResponse.kt` | all | Unused IP response model — use for IP fetch |
| P2 (reference) | `widget/src/main/kotlin/.../ui/DashboardWidgetContent.kt` | 154-185 | SpeedRow shows "Not tested" when !hasSpeed |
| P2 (reference) | `widget/build.gradle.kts` | all | Current deps (has Ktor, needs core:data) |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| AppWidgetProviderInfo sizing | Android Developers: App widget sizes | `minResizeWidth`/`minResizeHeight` (dp) control minimum resize; `targetCellWidth`/`targetCellHeight` (API 31+) set default cell count |
| Android 12 widget sizing | Developer docs: widget sizing on API 31+ | When both `targetCellWidth`/`minWidth` are present, `targetCell*` takes precedence on API 31+. `minResize*` allows shrinking below target. |
| dp-to-cell mapping | Android docs | 1 cell ≈ 57dp, formula: `(n * 73) - 16` for n cells. 4 cells = 276dp, 1 cell = 57dp. |
| WorkManager constraints | Android Developers | `setRequiredNetworkType(NetworkType.CONNECTED)` ensures worker only runs with network |

---

## Patterns to Mirror

### WIDGET_DATASTORE_WRITE
// SOURCE: widget/src/main/kotlin/.../WidgetRefreshWorker.kt:32-38
```kotlin
val dataStore = WidgetStateDefinition.getDataStore(appContext, "")
dataStore.edit { prefs ->
    prefs[WidgetStateDefinition.IS_CONNECTED] = isConnected
    ssid?.let { prefs[WidgetStateDefinition.SSID] = it }
    prefs[WidgetStateDefinition.LAST_SCAN_TIMESTAMP] = System.currentTimeMillis()
    prefs[WidgetStateDefinition.IS_SCAN_RUNNING] = false
}
```

### WIDGET_UPDATE_ALL
// SOURCE: widget/src/main/kotlin/.../WidgetRefreshWorker.kt:40-42
```kotlin
CompactWidget().updateAll(appContext)
StandardWidget().updateAll(appContext)
DashboardWidget().updateAll(appContext)
```

### ENCRYPTION_DETECTION_API31
// SOURCE: feature/posture/src/main/kotlin/.../WifiSecurityProvider.kt:23-53
```kotlin
val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
val network = cm.activeNetwork ?: return null
val caps = cm.getNetworkCapabilities(network) ?: return null
if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
@Suppress("DEPRECATION")
val info: WifiInfo = wm.connectionInfo ?: return null
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    when (info.currentSecurityType) {
        WifiInfo.SECURITY_TYPE_OPEN -> "Open"
        WifiInfo.SECURITY_TYPE_WEP -> "WEP"
        WifiInfo.SECURITY_TYPE_PSK -> "WPA2"
        WifiInfo.SECURITY_TYPE_SAE -> "WPA3"
        // ... etc
    }
}
```

### POSTURE_SCORE_COMPUTE
// SOURCE: feature/posture/src/main/kotlin/.../PostureScoreEngine.kt:10-38
```kotlin
fun compute(encryptionType: String?, isConnected: Boolean, deviceCount: Int?, isVpnActive: Boolean): PostureScore? {
    // ... builds factor list, computes weighted average, returns grade A-F
}
fun gradeFor(score: Int): String = when {
    score >= 90 -> "A"; score >= 75 -> "B"; score >= 60 -> "C"; score >= 40 -> "D"; else -> "F"
}
```

### KTOR_SERIALIZATION
// SOURCE: widget/src/main/kotlin/.../model/WidgetIpResponse.kt:1-11
```kotlin
@Serializable
data class WidgetIpResponse(
    val ip: String = "",
    @SerialName("country_code") val countryCode: String = "",
    val org: String = "",
)
```

### COROUTINE_WORKER_ERROR_HANDLING
// SOURCE: widget/src/main/kotlin/.../WidgetRefreshWorker.kt:44-49
```kotlin
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (_: Exception) {
    Result.retry()
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `widget/src/main/res/xml/widget_dashboard_info.xml` | UPDATE | Add minResizeWidth/minResizeHeight for 4x1 |
| `widget/src/main/res/xml/widget_standard_info.xml` | UPDATE | Add minResizeWidth/minResizeHeight for 2x1 |
| `widget/src/main/res/xml/widget_compact_info.xml` | UPDATE | Add minResizeWidth/minResizeHeight for 1x1 |
| `widget/src/main/kotlin/.../WidgetRefreshWorker.kt` | UPDATE | Expand doWork() to populate all DataStore fields |
| `widget/src/main/kotlin/.../WidgetRefresh.kt` | UPDATE | Add NetworkType.CONNECTED constraint to WorkRequest |
| `widget/build.gradle.kts` | UPDATE | Add core:data dependency for Room DB access |
| `widget/src/main/kotlin/.../model/WidgetIpResponse.kt` | UPDATE | Add country_name field for geo display |
| `widget/src/test/kotlin/.../WidgetRefreshWorkerTest.kt` | CREATE | Test the expanded data-gathering logic |

## NOT Building

- Speed test from widget (requires sustained bandwidth measurement — not suitable for background worker; "Not tested" is correct until user runs speed test in-app)
- Widget settings/configuration UI changes
- New widget sizes or layouts
- Moving PostureScoreEngine to a shared module (will inline scoring logic in worker for now)
- Hilt worker injection (manual context-based access keeps the change minimal)

---

## Step-by-Step Tasks

### Task 1: Fix dashboard widget resize to 4x1
- **ACTION**: Add `minResizeWidth` and `minResizeHeight` attributes to `widget_dashboard_info.xml`
- **IMPLEMENT**: Add `android:minResizeWidth="250dp"` (4 columns) and `android:minResizeHeight="40dp"` (1 row). Keep existing targetCellWidth=4, targetCellHeight=2 as defaults.
- **MIRROR**: Existing attribute patterns in widget_compact_info.xml
- **IMPORTS**: None (XML)
- **GOTCHA**: `minResizeWidth` must be ≤ `minWidth` for resize to work. Current minWidth is 250dp which equals 4 cells — set minResizeWidth to 250dp (keeps 4-column minimum). For height, 40dp ≈ 1 cell.
- **VALIDATE**: Build succeeds; on device, long-press dashboard widget and drag resize handle to shrink to 1 row

### Task 2: Fix standard widget resize to 2x1
- **ACTION**: Add `minResizeWidth` and `minResizeHeight` attributes to `widget_standard_info.xml`
- **IMPLEMENT**: Add `android:minResizeWidth="110dp"` and `android:minResizeHeight="40dp"`. Keeps 2-column minimum, allows shrinking to 1 row.
- **MIRROR**: Same pattern as Task 1
- **IMPORTS**: None
- **GOTCHA**: Standard widget UI may not look great at 2x1 — that's OK, the framework handles content clipping
- **VALIDATE**: Build succeeds

### Task 3: Fix compact widget resize to 1x1
- **ACTION**: Add `minResizeWidth` and `minResizeHeight` attributes to `widget_compact_info.xml`
- **IMPLEMENT**: Add `android:minResizeWidth="40dp"` and `android:minResizeHeight="40dp"`. Allows shrinking to 1x1 cell.
- **MIRROR**: Same pattern as Task 1
- **IMPORTS**: None
- **GOTCHA**: None
- **VALIDATE**: Build succeeds

### Task 4: Add core:data dependency to widget module
- **ACTION**: Add `implementation(project(":core:data"))` to widget/build.gradle.kts
- **IMPLEMENT**: Add after the existing `implementation(project(":core:network"))` line
- **MIRROR**: Same dependency declaration pattern as existing core:network
- **IMPORTS**: N/A
- **GOTCHA**: core:data brings Room + DAOs. No Hilt module conflict since widget worker accesses DB manually via Room.databaseBuilder.
- **VALIDATE**: `./gradlew :widget:assembleDebug` succeeds

### Task 5: Expand WidgetIpResponse for geo data
- **ACTION**: Add `country_name` field to WidgetIpResponse
- **IMPLEMENT**: Add `@SerialName("country") val countryName: String = ""` to the data class. The ip-api.com JSON response includes `country` for full country name and `countryCode` for 2-letter code.
- **MIRROR**: KTOR_SERIALIZATION pattern
- **IMPORTS**: None new
- **GOTCHA**: ip-api.com uses `countryCode` (camelCase in JSON) not `country_code`. Update SerialName for countryCode field.
- **VALIDATE**: Compiles

### Task 6: Expand WidgetRefreshWorker to populate all fields
- **ACTION**: Rewrite `doWork()` to gather encryption type, posture score, public IP/geo, device count, and latency
- **IMPLEMENT**:
  ```
  1. Get connectivity + SSID (existing code)
  2. Get encryption type:
     - If API 31+: WifiInfo.currentSecurityType via ConnectivityManager/WifiManager
     - Else: scanResults + parseCapabilities (same logic as WifiSecurityProvider)
  3. Compute posture score:
     - Inline gradeFor() function (pure: score >= 90 → A, etc.)
     - Inline simplified scoring: encryption type → score (WPA3=100, WPA2=70, etc.)
     - deviceCount from Room → device factor
     - VPN check via NetworkCapabilities.TRANSPORT_VPN
  4. Fetch public IP:
     - Ktor GET to http://ip-api.com/json/?fields=query,country,countryCode,org
     - Parse with WidgetIpResponse
     - Extract country flag emoji from countryCode
  5. Get device count:
     - Access NetLensDatabase via Room.databaseBuilder
     - Query LanScanHistoryDao.getRecent(1) for latest scan's deviceCount
  6. Measure latency:
     - InetAddress.getByName("8.8.8.8").isReachable(3000) with timing
     - Or simple socket connect to 8.8.8.8:53 with timing
  7. Write ALL fields to DataStore in a single edit {}
  8. Call updateAll() on all three widgets
  ```
- **MIRROR**: WIDGET_DATASTORE_WRITE, ENCRYPTION_DETECTION_API31, POSTURE_SCORE_COMPUTE, COROUTINE_WORKER_ERROR_HANDLING
- **IMPORTS**: `android.net.wifi.WifiInfo`, `android.net.wifi.WifiManager`, `android.os.Build`, `io.ktor.client.*`, `io.ktor.client.call.body`, `io.ktor.client.plugins.contentnegotiation.ContentNegotiation`, `io.ktor.serialization.kotlinx.json.json`, `kotlinx.serialization.json.Json`, `com.ventoux.netlens.widget.model.WidgetIpResponse`, `com.ventoux.netlens.widget.util.toFlagEmoji`, `java.net.InetAddress`, `java.net.Socket`, `androidx.room.Room`, `com.ventoux.netlens.core.data.NetLensDatabase`
- **GOTCHA**: 
  - Must rethrow CancellationException (already handled in existing pattern)
  - Ktor client must be closed after use (`client.use { }`)
  - ip-api.com has rate limit of 45 req/min for free tier — widget refresh every 30min is well within
  - ip-api.com requires HTTP not HTTPS on free tier
  - Room DB access in worker: use `Room.databaseBuilder(appContext, NetLensDatabase::class.java, "netlens.db").build()` — check the actual DB name in core:data module
  - Don't fail the entire worker if one data source fails — use individual try/catch blocks and write whatever data succeeded
  - The `parseCapabilities()` function from WifiSecurityProvider.kt is `internal` in feature:posture — inline it in the worker
- **VALIDATE**: `./gradlew :widget:testDebugUnitTest` passes; deploy to device and verify widget populates data

### Task 7: Add network constraint to WorkRequest
- **ACTION**: Update `enqueueWidgetRefresh()` in WidgetRefresh.kt to add a CONNECTED network constraint
- **IMPLEMENT**: 
  ```kotlin
  val workRequest = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
      .setConstraints(
          Constraints.Builder()
              .setRequiredNetworkType(NetworkType.CONNECTED)
              .build()
      )
      .build()
  ```
- **MIRROR**: Standard WorkManager constraint pattern
- **IMPORTS**: `androidx.work.Constraints`, `androidx.work.NetworkType`
- **GOTCHA**: Without network, the IP fetch and latency measurement will fail anyway. The constraint defers the work until network is available rather than running and failing.
- **VALIDATE**: Compiles; worker only runs when connected

### Task 8: Write unit tests for expanded worker logic
- **ACTION**: Create `WidgetRefreshWorkerTest.kt` to test the scoring and encryption type mapping logic
- **IMPLEMENT**: Test the inlined scoring logic:
  - `gradeFor(95)` → "A", `gradeFor(80)` → "B", etc.
  - Encryption scoring: WPA3 → 100, WPA2 → 70, WEP → 20, Open → 0
  - `toFlagEmoji()` for country code → emoji
- **MIRROR**: Existing test pattern in `widget/src/test/kotlin/.../WidgetThemeTest.kt`
- **IMPORTS**: JUnit 5 `@Test`, `assertEquals`
- **GOTCHA**: Keep worker logic testable by extracting pure functions (gradeFor, encryptionScore) as companion object or top-level functions
- **VALIDATE**: `./gradlew :widget:testDebugUnitTest` passes

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| gradeFor(95) | 95 | "A" | No |
| gradeFor(75) | 75 | "B" | Boundary |
| gradeFor(0) | 0 | "F" | Minimum |
| encryptionScore("WPA3") | "WPA3" | 100 | No |
| encryptionScore("WEP") | "WEP" | 20 | Critical security |
| encryptionScore(null) | null | 0 | No WiFi |
| encryptionScore("Open") | "Open" | 0 | No encryption |
| "US".toFlagEmoji() | "US" | "🇺🇸" | No |
| "".toFlagEmoji() | "" | "" | Empty |

### Edge Cases Checklist
- [x] No WiFi (mobile only) — encryption returns null, score uses fewer factors
- [x] No internet — WorkManager constraint defers work
- [x] ip-api.com unreachable — individual try/catch, IP fields stay empty
- [x] Room DB empty (no LAN scans) — deviceCount stays 0
- [x] Latency timeout — latencyMs stays -1L (shows "— ms")
- [x] CancellationException — rethrown, never caught

---

## Validation Commands

### Static Analysis
```bash
./gradlew :widget:assembleDebug
```
EXPECT: Zero errors

### Unit Tests
```bash
./gradlew :widget:testDebugUnitTest
```
EXPECT: All tests pass

### Full Test Suite
```bash
./gradlew testDebugUnitTest
```
EXPECT: No regressions

### Manual Validation
- [ ] Install debug APK on device
- [ ] Add dashboard widget to home screen
- [ ] Verify widget shows score grade (A/B/C/D/F), not "?"
- [ ] Verify widget shows encryption type (e.g. "WPA3 ✓")
- [ ] Verify widget shows public IP with country flag
- [ ] Verify widget shows latency (e.g. "12 ms")
- [ ] Verify widget shows device count from last LAN scan
- [ ] Verify "Not tested" still shows for speed (expected — no background speed test)
- [ ] Verify footer shows "Scanned Xm" not "Not scanned"
- [ ] Long-press dashboard widget, resize down to 4x1 row
- [ ] Long-press standard widget, resize down to 2x1 row
- [ ] Verify widget refreshes on network change (toggle WiFi)
- [ ] Tap "Not tested" row → opens app to speed test screen

---

## Acceptance Criteria
- [ ] Dashboard widget resizable to 4x1 on Android 12+
- [ ] Standard widget resizable to 2x1
- [ ] Compact widget resizable to 1x1
- [ ] Widget shows security score grade (A-F) after refresh
- [ ] Widget shows encryption type and secure/insecure indicator
- [ ] Widget shows public IP and country flag
- [ ] Widget shows device count from latest LAN scan
- [ ] Widget shows latency measurement
- [ ] Speed shows "Not tested" (expected — no background speed test)
- [ ] Footer shows relative time since last scan
- [ ] Worker runs only when network is available
- [ ] Individual data source failures don't crash the entire refresh
- [ ] All unit tests pass
- [ ] No build errors

## Completion Checklist
- [ ] Code follows discovered patterns (DataStore write, error handling)
- [ ] Error handling: individual try/catch per data source, CancellationException rethrown
- [ ] Tests follow JUnit 5 + existing widget test patterns
- [ ] No hardcoded values (API URLs as constants)
- [ ] No unnecessary scope additions (speed test stays out)
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ip-api.com rate limit hit | Low | Low | Widget refreshes every 30min; 45 req/min limit is far above usage |
| ip-api.com requires HTTP (not HTTPS) | Medium | Medium | Ensure network_security_config.xml allows cleartext to ip-api.com, or use alternative HTTPS API (ipify, ipinfo.io) |
| Room DB name mismatch | Low | High | Verify exact DB name from core:data module's `@Database` annotation before implementation |
| Widget resize causes layout overflow at 4x1 | Medium | Low | Dashboard content will clip — Glance handles this; user can resize back up |
| Background worker killed by Doze | Low | Medium | WorkManager handles Doze correctly; CONNECTED constraint ensures deferral |

## Notes
- Speed test is intentionally NOT included in background refresh — it requires sustained bandwidth measurement and would consume user data. The "Not tested" display is correct behavior until the user manually runs a speed test in-app. A future enhancement could write speed results to the widget DataStore from the speed test ViewModel after a manual test.
- The scoring logic is inlined from PostureScoreEngine rather than moved to a shared module. This is a pragmatic choice for a bug fix. The scheduled remote routine (May 10, 2026) to extract NetworkInterfaceProvider into core:network would be a good time to also extract PostureScoreEngine into a shared module.
- The existing `WidgetIpResponse` model was already defined but never used — clear evidence the IP fetch was planned but never implemented.
- `parseCapabilities()` in WifiSecurityProvider.kt is `internal` to feature:posture. We inline it in the widget worker. When PostureScoreEngine moves to core:network, this can be unified.
