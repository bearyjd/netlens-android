# Plan: NetLens Feature Backlog & Widget Redesign

## Summary
Two-task plan: (1) Create a prioritized GitHub issue backlog of 15 features that transform NetLens from a diagnostics toolkit into a network security posture app, and (2) redesign the home screen widget in three sizes (2x1 compact, 2x2 standard, 4x2 full dashboard) centered around a security score hero element, replacing the current VPN/IP-centric Glance widget.

## User Story
As a non-technical home user, I want to glance at my home screen widget and instantly know whether my network is safe and what's happening, so that I can take action without opening the app.

As a power user, I want a prioritized roadmap of features that deepen NetLens's security posture and diagnostics capabilities, so the development team builds the right things in the right order.

## Problem -> Solution
**Current:** Widget shows VPN status and local IP — wrong for what NetLens actually does. No security score exists. No feature backlog tracking planned work. Deep links from widget are broken (manifest declares `netlens://feature` scheme but `MainActivity` never routes intents). Manifest references old package name `us.beary.netlens` for Wide/Banner receivers.

**Desired:** Widget communicates "Is my network safe?" at a glance via a security score badge. All taps deep-link to specific screens. 15 prioritized GitHub issues capture the full product vision. Data contracts allow widget to display real data as features ship.

## Metadata
- **Complexity**: XL
- **Source PRD**: N/A (inline spec)
- **PRD Phase**: N/A
- **Estimated Files**: 30-40 (widget rewrite) + GitHub issues (no files)

---

## Critical Codebase Findings

### Bug: Manifest references old package name
`app/src/main/AndroidManifest.xml:47,57` registers `us.beary.netlens.widget.WideWidgetReceiver` and `us.beary.netlens.widget.BannerWidgetReceiver` — but the package was renamed to `com.ventoux.netlens` in commit `972ad72`. These receivers will fail to resolve.

### Bug: Deep links not routed
`MainActivity.kt` has zero intent handling — no `onNewIntent`, no URI extraction. The manifest declares `<data android:scheme="netlens" android:host="feature" />` but tapping a widget deep link just opens the app to the home screen. `NetLensApp.kt` and `NetLensNavHost.kt` do not process incoming intents.

### Architecture: Glance vs RemoteViews
Current widget uses **Jetpack Glance 1.1.1** (Compose-for-widgets). The spec calls for "AppWidgetProvider + PendingIntent + RemoteViews". Glance compiles to RemoteViews under the hood, and `GlanceAppWidgetReceiver` extends `AppWidgetProvider`. Glance's `actionStartActivity()` creates PendingIntents. **Recommendation: keep Glance** — it's already integrated, abstracts RemoteViews complexity, handles dp padding natively, and satisfies all spec constraints. Rewriting to raw RemoteViews would be 3x the code for zero functional benefit.

### Data: Widget uses DataStore, not SharedPreferences
The project uses DataStore exclusively. Two DataStore instances in widget module:
1. `ip_widget_prefs` — widget state data (IP, SSID, etc.)
2. `widget_preferences` — widget appearance settings (colors, size, etc.)

The spec says "SharedPreferences" — we'll use DataStore (equivalent, already established pattern).

### Data: No security score, speed test, or Wi-Fi audit exists
These features are in the backlog but not yet built. Widget must define data contracts and show meaningful fallback states ("Not scanned", "Not tested", gray badge) until backing features ship.

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `widget/src/main/kotlin/.../NetLensWidget.kt` | all | Current widget to rewrite — Glance structure, state reading, composables |
| P0 | `widget/src/main/kotlin/.../IpWidgetState.kt` | all | Current widget state model — must be replaced with new `WidgetState` |
| P0 | `widget/src/main/kotlin/.../IpWidgetRefreshWorker.kt` | all | Worker that populates widget data — must be rewritten |
| P0 | `widget/src/main/kotlin/.../WidgetRefresh.kt` | all | Refresh triggers — must add broadcast-driven refresh |
| P0 | `widget/src/main/kotlin/.../util/Deeplink.kt` | all | Current deep link constants — must extend |
| P0 | `app/src/main/AndroidManifest.xml` | 18-66 | Intent filters and widget receivers — must fix and extend |
| P0 | `app/.../MainActivity.kt` | all | Must add intent routing for deep links |
| P0 | `app/.../navigation/ToolDestination.kt` | all | All route strings — deep link targets |
| P0 | `app/.../navigation/NetLensNavHost.kt` | all | Nav graph — must wire deep link routing |
| P1 | `widget/src/main/kotlin/.../model/WidgetPreferences.kt` | all | Preferences model — WidgetSize enum to update |
| P1 | `widget/src/main/kotlin/.../action/` | all | Action classes for taps — must add new actions |
| P1 | `widget/build.gradle.kts` | all | Widget module dependencies |
| P2 | `feature/ipinfo/.../IpInfoScreen.kt` | all | Reference for IP info data displayed |
| P2 | `feature/lanscan/.../LanScanViewModel.kt` | all | Reference for device count data |
| P2 | `feature/ping/.../PingViewModel.kt` | all | Reference for latency data |

---

## Patterns to Mirror

### WIDGET_STATE_DEFINITION
// SOURCE: widget/src/main/kotlin/.../IpWidgetStateDefinition.kt
```kotlin
object IpWidgetStateDefinition : GlanceStateDefinition<Preferences> {
    // DataStore-backed preferences for widget state
    // Keys defined as PreferencesKey<T> constants
    // getDataStore() provides the DataStore instance
}
```

### GLANCE_WIDGET
// SOURCE: widget/src/main/kotlin/.../NetLensWidget.kt:58-74
```kotlin
class NetLensWidget : GlanceAppWidget() {
    override val stateDefinition = IpWidgetStateDefinition
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetPrefs = WidgetPreferencesRepository.observe(context).first()
        provideContent {
            val prefs = currentState<Preferences>()
            val state = prefs.toIpWidgetState()
            WidgetRoot(state = state, widgetPrefs = widgetPrefs)
        }
    }
}
```

### WIDGET_REFRESH_WORKER
// SOURCE: widget/src/main/kotlin/.../IpWidgetRefreshWorker.kt
```kotlin
class IpWidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // 1. Read connectivity state
        // 2. Fetch data (network info, IP, etc.)
        // 3. Write to DataStore
        // 4. Call NetLensWidget().updateAll(applicationContext)
        return Result.success()
    }
}
```

### DEEP_LINK_ACTION
// SOURCE: widget/src/main/kotlin/.../util/Deeplink.kt:1-13
```kotlin
object Deeplink {
    const val SCHEME = "netlens"
    const val HOST = "feature"
    const val IPINFO = "$SCHEME://$HOST/ipinfo"
    const val LAN_SCAN = "$SCHEME://$HOST/lanscan"
}
```

### TOOL_DESTINATION_ENUM
// SOURCE: app/.../navigation/ToolDestination.kt:29-35
```kotlin
enum class ToolDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
    val description: String,
    val category: ToolCategory,
)
```

### FEATURE_MODULE_DI
// SOURCE: feature/whois/.../di/WhoisModule.kt
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class WhoisModule {
    @Binds @Singleton
    abstract fun bindWhoisClient(impl: WhoisClientImpl): WhoisClient
}
```

### VIEWMODEL_STATE
// SOURCE: feature/lanscan/.../LanScanViewModel.kt
```kotlin
private val _uiState = MutableStateFlow(LanScanUiState())
val uiState: StateFlow<LanScanUiState> = _uiState.asStateFlow()
// Updated via: _uiState.update { it.copy(field = newValue) }
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

# TASK 1: Feature Backlog (GitHub Issues)

## Overview
Create 15 GitHub issues with labels, effort estimates, acceptance criteria, and implementation notes. Issues reference existing codebase modules and follow the priority tiers from the spec.

## Issue Definitions

### Issue #1 — Network Security Posture Score
- **Title**: `feat: network security posture score (A-F grade)`
- **Labels**: `priority: P0`, `type: feature`, `size: XL`
- **Effort**: XL (new core module + scoring engine + home screen hero + widget integration)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Score (A-F, color-coded) computed from: open ports, DNS leak status, encryption type, public IP reputation, risky LAN services
  - Updates on every network change
  - Hero element on home screen, visible without scrolling
  - Each contributing factor tappable → deep-links to its detail screen
  - Score persisted to DataStore for widget consumption
- **Implementation notes**:
  - New module `core:posture` with `PostureScoreEngine` interface
  - Scoring rubric: each factor has a weight (0-100) and severity; aggregate produces letter grade
  - Depends on: #3 (DNS leak), #8 (Wi-Fi audit), #9 (IP reputation), #10 (port scanner), LAN scan (existing)
  - Can ship with partial scoring (just encryption type + known devices) and add factors as features land
  - Data contract for widget: `posture_score_grade: String`, `posture_score_color: Int`, `posture_issue_count: Int`, `posture_top_issue: String`
- **Blocking**: This is Netlens's core differentiator — no competitor has a unified score

### Issue #2 — Connected Devices Inventory
- **Title**: `feat: connected devices inventory with history and new-device alerts`
- **Labels**: `priority: P0`, `type: feature`, `size: L`
- **Effort**: L (extends existing LAN scan with persistence + notifications)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - LAN scan showing: hostname, MAC, vendor (OUI), IP, open ports, first seen, last seen
  - New/unknown device flagged with notification within 60s
  - Scan completes <10s for ≤32 devices
  - Device history persists locally across sessions (Room)
  - No account or cloud sync
- **Implementation notes**:
  - Extends existing `feature:lanscan` module — `LanDevice` already has ip, hostname, deviceType, services
  - Add Room entity `KnownDevice` with firstSeen/lastSeen timestamps to `core:data`
  - Add `DeviceHistoryDao` — insert on scan, flag new if not in history
  - Notification via `NotificationCompat` on new device detection
  - Data contract for widget: `device_count: Int`
- **Depends on**: `core:oui` (existing)

### Issue #2a — Host Port Scan Drill-down
- **Title**: `feat: connected devices → host detail screen with auto port scan`
- **Labels**: `priority: P0`, `type: feature`, `size: M`
- **Effort**: M (mostly done — bottom sheet exists from commit `734c1d8`, needs promotion to full screen)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Tap any host in Connected Devices → Host Detail screen
  - Auto-initiates port scan on screen open (top 1000 ports)
  - Results grouped by risk: Critical (21,23,3389,5900) / Open / Closed
  - Each port shows: number, service name, risk level, plain-language explanation
  - "Rescan" button, JSON export
  - Results stream in real-time; <20s for top 1000 ports
  - Back button preserves scroll position in inventory
- **Implementation notes**:
  - Existing `HostDetailState` and bottom sheet in `feature:lanscan` — evaluate promoting to full screen or keeping bottom sheet
  - Existing `PortScanner` and `WellKnownPorts` in `feature:portscan` — reuse directly
  - Add risk categorization to `WellKnownPorts`: `CRITICAL`, `WARNING`, `INFO`
  - Navigation: new route `lanscan/host/{ip}` in `ToolDestination` or argument-based composable
- **Depends on**: #2

### Issue #3 — DNS Leak Detection
- **Title**: `feat: DNS leak detection with posture score contribution`
- **Labels**: `priority: P0`, `type: feature`, `size: M`
- **Effort**: M (new engine + screen, existing DNS resolver can be extended)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - One-tap test; result in <5s
  - Clear pass/fail UI
  - Plain-language explanation of what a DNS leak means
  - Actionable fix guidance
  - Result contributes to posture score
- **Implementation notes**:
  - New module `feature:dnsleak` or add to existing `feature:dns`
  - Technique: resolve a unique subdomain (e.g., `<nonce>.dnsleaktest.netlens.app`) via system resolver, check which DNS server handled it
  - Simpler approach: resolve via system DNS and compare resolver IP against expected (configured DNS server)
  - Existing `DnsResolverImpl` in `feature:dns` uses dnsjava — can query system resolver directly
- **Depends on**: none (can use existing `feature:dns` engine)

### Issue #4 — IPinfo Integration
- **Title**: `feat: replace ip-api.com with IPinfo SDK for public IP intelligence`
- **Labels**: `priority: P1`, `type: feature`, `size: M`
- **Effort**: M (replace existing API, add SDK, extend screen with anonymity flags)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Integrate `io.ipinfo:ipinfo-api` Java SDK (free Lite tier)
  - On network change: cache public IP, country, flag emoji, ASN, anonymity flags
  - IP Info screen shows: flag, public IP, country, city, ISP, ASN, `is_anonymous`, `is_tor`, `is_vpn`, `is_proxy`, `is_hosting` with plain-language labels
  - Data refreshes on network change; cached value shown with timestamp if offline
  - "IP data by IPinfo" attribution in footer
  - User opt-in on first launch
- **Implementation notes**:
  - Replaces current `ip-api.com/json/` call in `IpInfoRepositoryImpl` and `IpWidgetRefreshWorker`
  - `getCountryFlag().emoji` renders natively in Android TextView — safe for RemoteViews/Glance Text
  - Add to `gradle/libs.versions.toml`: `ipinfo = "3.0.0"` (latest), `io.ipinfo:ipinfo-api`
  - Data contract for widget: `ip_public: String`, `ip_country_flag: String`, `ip_country_name: String`, `ip_isp: String`, `ip_asn: String`
  - Privacy: add opt-in dialog before first IPinfo call
- **Depends on**: none (replaces existing feature)

### Issue #5 — Self-hosted Speed Test
- **Title**: `feat: privacy-respecting speed test via Cloudflare`
- **Labels**: `priority: P1`, `type: feature`, `size: L`
- **Effort**: L (new feature module + engine + screen + history)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Download fixed 10MB file from `speed.cloudflare.com`, measure throughput
  - Thresholds: >25 Mbps = Fast (green), 5-25 = Medium (amber), <5 = Slow (red)
  - Display: label + numeric Mbps
  - Speed Test screen: current result, 10-test sparkline, timestamp, network type
  - Runs only on explicit user trigger — never automatic
  - Result cached in DataStore for widget
  - History stored locally
- **Implementation notes**:
  - New module `feature:speedtest`
  - Engine: Ktor CIO client downloads `https://speed.cloudflare.com/__down?bytes=10485760`, measures bytes/time
  - Careful: measure only transfer time, exclude connection setup
  - Room entity `SpeedTestHistoryEntry` in `core:data`
  - Data contract for widget: `speed_mbps: Float`, `speed_label: String`, `speed_timestamp: Long`
- **Depends on**: none

### Issue #6 — Cell Tower Detail View
- **Title**: `feat: cell tower info with signal metrics and neighbor cells`
- **Labels**: `priority: P1`, `type: feature`, `size: L`
- **Effort**: L (new feature module, `TelephonyManager` integration, requires careful permission handling)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Show: operator, band, network type (LTE/5G NR/NSA), RSRP, RSRQ, SNR, tower ID, TAC
  - Neighbor cells with signal metrics
  - Refreshes every 5s
  - Values color-coded against normal ranges
  - JSON export
  - Graceful degradation without fine location permission
- **Implementation notes**:
  - New module `feature:celltower`
  - Requires `ACCESS_FINE_LOCATION` + `READ_PHONE_STATE` permissions
  - `TelephonyManager.getAllCellInfo()` returns `CellInfoLte`, `CellInfoNr`, `CellInfoGsm`
  - 5G NR fields only available on API 29+ (matches minSdk 29)
- **Depends on**: none

### Issue #7 — Latency + Jitter Monitor
- **Title**: `feat: persistent latency monitor with rolling chart and alert threshold`
- **Labels**: `priority: P1`, `type: feature`, `size: M`
- **Effort**: M (extends existing ping feature with continuous monitoring card)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Configurable persistent ping to user-selected endpoint (default `1.1.1.1`)
  - Rolling 60s RTT chart with min/avg/max and jitter
  - Collapsible card on home screen
  - Pauses when app backgrounded
  - Configurable alert threshold (e.g., >100ms triggers notification)
  - Chart persists across app restores within session
- **Implementation notes**:
  - Extends existing `feature:ping` — `ContinuousPingService` already exists with foreground service
  - `PingSummary` already computes min/avg/max/jitterMs
  - Add `latency_current_ms: Long` to widget DataStore contract
  - Home screen card: new composable in `app` module, reads from ping ViewModel
- **Depends on**: none (extends existing feature)

### Issue #8 — Wi-Fi Security Audit
- **Title**: `feat: automatic Wi-Fi security audit on network join`
- **Labels**: `priority: P1`, `type: feature`, `size: L`
- **Effort**: L (new feature module + network callback + audit checks)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Runs automatically on every Wi-Fi join
  - Checks: encryption type (WEP/WPA2/WPA3), hidden SSID, captive portal detection, router admin panel exposed
  - Findings as dismissible cards with plain-language fix guidance
  - Each finding contributes to posture score
  - Re-runs on reconnect to same network
- **Implementation notes**:
  - New module `feature:wifiaudit`
  - Encryption type: `WifiInfo.getCurrentSecurityType()` (API 31+), fallback to `ScanResult.capabilities` parsing
  - Captive portal: `ConnectivityManager.getNetworkCapabilities()` check for `NET_CAPABILITY_CAPTIVE_PORTAL`
  - Router admin: TCP connect to gateway:80/443/8080 (reuse `PortScanner`)
  - Data contract for widget: `wifi_encryption: String`, `wifi_encryption_secure: Boolean`
- **Depends on**: none

### Issue #9 — Public IP Reputation Check
- **Title**: `feat: public IP reputation check via IPinfo + AbuseIPDB`
- **Labels**: `priority: P2`, `type: feature`, `size: M`
- **Effort**: M (extends #4 with AbuseIPDB integration)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - IPinfo anonymity flags as first layer
  - AbuseIPDB free API for abuse/spam scoring
  - Warning badge on flagged IPs with plain-language explanation
  - Contributes to posture score
  - User can manually refresh
- **Implementation notes**:
  - Extend `feature:ipinfo` — add AbuseIPDB check endpoint
  - AbuseIPDB free tier: 1000 checks/day
  - Requires API key — store in BuildConfig, user provides their own key in settings
- **Depends on**: #4

### Issue #10 — Open Port Scanner (standalone)
- **Title**: `feat: standalone open port scanner with risk explanations`
- **Labels**: `priority: P2`, `type: feature`, `size: S`
- **Effort**: S (existing `feature:portscan` — add risk categorization and plain-language explanations)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Scan localhost and LAN hosts
  - Flag risky ports: 21 (FTP), 23 (Telnet), 3389 (RDP), 5900 (VNC), 8080
  - Plain-language risk explanations
  - <15s for top 1000 ports on localhost
  - Results persist per-host
  - Contributes to posture score
- **Implementation notes**:
  - Enhance existing `WellKnownPorts` with risk level enum and explanation strings
  - Already have `PortScanner` and `PortScanScreen`
- **Depends on**: none

### Issue #11 — Network History + Change Log
- **Title**: `feat: comprehensive network change log with timeline view`
- **Labels**: `priority: P2`, `type: feature`, `size: M`
- **Effort**: M (extends existing `feature:netlog` + `feature:history`)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Log: network join/leave, IP change, new device, DNS resolver change, speed test, security event, posture score change
  - Filterable timeline (by type, date range)
  - JSON export
  - Local only — no cloud
- **Implementation notes**:
  - Existing `NetworkEvent` entity and `NetworkEventDao` in `core:data`
  - Existing `feature:netlog` already shows network events
  - Extend `NetworkEvent.eventType` to cover new event types
  - Existing `feature:history` aggregates all history — extend with new types
- **Depends on**: none

### Issue #12 — Traceroute + Hop Analysis
- **Title**: `feat: visual traceroute with geolocation and anomaly detection`
- **Labels**: `priority: P2`, `type: feature`, `size: L`
- **Effort**: L (extends existing `feature:traceroute` significantly)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Each hop: IP, reverse hostname, geolocation via IPinfo, latency
  - Country flags on hops
  - Flags anomalous routing (unexpected foreign country, latency spikes)
  - Results exportable
- **Implementation notes**:
  - Existing `feature:traceroute` has basic traceroute
  - Add IPinfo lookup per hop IP (batch)
  - Add anomaly detection heuristics
- **Depends on**: #4

### Issue #13 — Quick Settings Tile
- **Title**: `feat: quick settings tile showing security score`
- **Labels**: `priority: P3`, `type: feature`, `size: S`
- **Effort**: S (single `TileService` class)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Shows current security score letter grade
  - One-tap triggers fresh scan
  - Updates on network change and scan completion
- **Implementation notes**:
  - New `PostureTileService` extending `TileService`
  - Read posture score from DataStore
  - Register in manifest with `<service android:name=".PostureTileService" ...>`
- **Depends on**: #1

### Issue #14 — Tasker / Android Shortcuts
- **Title**: `feat: Tasker intents and Android App Shortcuts`
- **Labels**: `priority: P3`, `type: feature`, `size: M`
- **Effort**: M (intent handling + shortcuts XML + documentation)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Intents: `RUN_SCAN`, `GET_STATUS`, `GET_SCORE`, `GET_CONNECTED_DEVICES`, `RUN_SPEED_TEST`
  - All documented in README with Tasker examples
  - Registered as Android App Shortcuts for long-press launcher
- **Implementation notes**:
  - Add `<intent-filter>` entries in manifest for each action
  - Implement `BroadcastReceiver` or handle in `MainActivity`
  - `shortcuts.xml` for launcher shortcuts
- **Depends on**: #1, #2, #5

### Issue #15 — Widget Implementation
- **Title**: `feat: redesign home screen widget in 3 sizes with security score`
- **Labels**: `priority: P3`, `type: feature`, `size: XL`
- **Effort**: XL (complete widget rewrite, 3 sizes, deep link fixes, data contracts)
- **Android targets**: 12, 13, 14, 15
- **AC**:
  - Three sizes: 2x1 compact, 2x2 standard, 4x2 full dashboard
  - Security score badge as hero element
  - All taps deep-link to specific screens
  - Zero polling — event-driven updates only
  - Graceful degradation on missing permissions or stale data
- **Implementation notes**:
  - See Task 2 below for full implementation plan
  - Hard dependencies: #1 (score), #4 (IPinfo), #5 (speed), #7 (latency) must have data contracts defined
  - Can ship with stub data and "Not scanned"/"Not tested" states
- **Depends on**: #1, #4, #5, #7 (data contracts only — stubs acceptable)

---

# TASK 2: Widget Implementation Plan

## Architecture Decision: Keep Glance

The spec says "AppWidgetProvider + PendingIntent + RemoteViews". Glance satisfies all three:
- `GlanceAppWidgetReceiver` extends `AppWidgetProvider`
- `actionStartActivity()` creates `PendingIntent`
- Glance compiles to `RemoteViews` under the hood
- Padding in Glance uses dp natively (no `setViewPadding` workaround needed)

Rewriting to raw RemoteViews would triple the code for zero functional benefit. **Keep Glance.**

## Widget Size Mapping

| Spec Size | Glance Receiver | Widget Info XML | Cell Size |
|---|---|---|---|
| 2x1 compact | `CompactWidgetReceiver` | `widget_compact.xml` | minWidth=110dp, minHeight=40dp |
| 2x2 standard | `StandardWidgetReceiver` | `widget_standard.xml` | minWidth=180dp, minHeight=110dp |
| 4x2 full dashboard | `DashboardWidgetReceiver` | `widget_dashboard.xml` | minWidth=250dp, minHeight=110dp |

Replaces current SMALL/MEDIUM/WIDE/BANNER sizes entirely.

## New Widget State Model

Replace `IpWidgetState` with:
```kotlin
data class WidgetState(
    // Posture score
    val scoreGrade: String = "",       // A, B, C, D, F, or "" (unscanned)
    val scoreColorArgb: Int = 0,       // Color int for grade
    val issueCount: Int = 0,
    val topIssue: String = "",         // "WEP encryption detected" or ""
    val topIssueId: String = "",       // Deep link ID for the issue

    // Network
    val isConnected: Boolean = false,
    val ssid: String? = null,
    val encryptionType: String = "",   // "WPA3", "WPA2", "WEP", ""
    val isEncryptionSecure: Boolean = true,

    // IP
    val publicIp: String = "",
    val countryFlag: String = "",      // Flag emoji from IPinfo
    val countryName: String = "",
    val ispName: String = "",
    val asnName: String = "",

    // Speed
    val speedMbps: Float = -1f,        // -1 = not tested
    val speedLabel: String = "",       // "Fast", "Medium", "Slow", ""
    val speedTimestamp: Long = 0L,

    // Latency
    val latencyMs: Long = -1L,         // -1 = not measured

    // Devices
    val deviceCount: Int = 0,

    // Scan status
    val lastScanTimestamp: Long = 0L,
    val isScanRunning: Boolean = false,
)
```

## Deep Link Constants

Extend `Deeplink.kt`:
```kotlin
object Deeplink {
    const val SCHEME = "netlens"
    const val HOST = "feature"

    const val HOME = "$SCHEME://$HOST/home"
    const val POSTURE = "$SCHEME://$HOST/posture"
    const val WIFI_AUDIT = "$SCHEME://$HOST/wifiaudit"
    const val IPINFO = "$SCHEME://$HOST/ipinfo"
    const val SPEED_TEST = "$SCHEME://$HOST/speedtest"
    const val LATENCY = "$SCHEME://$HOST/ping"
    const val DEVICES = "$SCHEME://$HOST/lanscan"
    const val DNS = "$SCHEME://$HOST/dns"

    fun issue(issueId: String) = "$SCHEME://$HOST/issue/$issueId"
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `widget/src/main/kotlin/.../WidgetState.kt` | CREATE | New widget state model replacing IpWidgetState |
| `widget/src/main/kotlin/.../WidgetStateDefinition.kt` | CREATE | DataStore definition for new WidgetState |
| `widget/src/main/kotlin/.../NetLensWidget.kt` | REWRITE | New widget composables for 3 sizes |
| `widget/src/main/kotlin/.../CompactWidgetContent.kt` | CREATE | 2x1 compact widget composable |
| `widget/src/main/kotlin/.../StandardWidgetContent.kt` | CREATE | 2x2 standard widget composable |
| `widget/src/main/kotlin/.../DashboardWidgetContent.kt` | CREATE | 4x2 full dashboard widget composable |
| `widget/src/main/kotlin/.../WidgetTheme.kt` | CREATE | Score-driven color theming for widget |
| `widget/src/main/kotlin/.../receiver/CompactWidgetReceiver.kt` | CREATE | Receiver for 2x1 |
| `widget/src/main/kotlin/.../receiver/StandardWidgetReceiver.kt` | CREATE | Receiver for 2x2 |
| `widget/src/main/kotlin/.../receiver/DashboardWidgetReceiver.kt` | CREATE | Receiver for 4x2 |
| `widget/src/main/kotlin/.../NetLensWidgetReceiver.kt` | DELETE | Old receiver replaced by 3 new ones |
| `widget/src/main/kotlin/.../IpWidgetState.kt` | DELETE | Replaced by WidgetState |
| `widget/src/main/kotlin/.../IpWidgetStateDefinition.kt` | DELETE | Replaced by WidgetStateDefinition |
| `widget/src/main/kotlin/.../IpWidgetRefreshWorker.kt` | REWRITE | New worker populating WidgetState |
| `widget/src/main/kotlin/.../WidgetRefresh.kt` | UPDATE | Add broadcast-driven refresh |
| `widget/src/main/kotlin/.../util/Deeplink.kt` | UPDATE | Add all deep link constants |
| `widget/src/main/kotlin/.../action/OpenDeeplinkAction.kt` | UPDATE | Handle new deep link routes |
| `widget/src/main/kotlin/.../action/TriggerScanAction.kt` | CREATE | Action to trigger background scan |
| `widget/src/main/kotlin/.../model/WidgetPreferences.kt` | UPDATE | Replace WidgetSize enum |
| `widget/src/main/res/xml/widget_compact.xml` | CREATE | Widget info for 2x1 |
| `widget/src/main/res/xml/widget_standard.xml` | CREATE | Widget info for 2x2 |
| `widget/src/main/res/xml/widget_dashboard.xml` | CREATE | Widget info for 4x2 |
| `widget/src/main/res/xml/netlens_widget_info.xml` | DELETE | Replaced by 3 new XMLs |
| `widget/src/main/res/xml/widget_wide.xml` | DELETE | Replaced |
| `widget/src/main/res/xml/widget_banner.xml` | DELETE | Replaced |
| `widget/src/main/res/values/strings.xml` | UPDATE | Add all widget string resources |
| `widget/src/main/res/drawable/ic_shield_*.xml` | CREATE | Score badge shield drawables (A-F + gray) |
| `app/src/main/AndroidManifest.xml` | UPDATE | Fix old package names, register 3 new receivers, remove old ones |
| `app/.../MainActivity.kt` | UPDATE | Add deep link intent routing |
| `app/.../navigation/NetLensNavHost.kt` | UPDATE | Wire deep link routes to screens |
| `feature/widgetsettings/...` | UPDATE | Adapt to new 3-size model |

## NOT Building

- Raw RemoteViews implementation (Glance is superior and already integrated)
- Security posture engine (backlog item #1 — widget uses data contract stubs)
- Speed test engine (backlog item #5 — widget reads cached result)
- Wi-Fi audit engine (backlog item #8 — widget reads cached result)
- IPinfo SDK integration (backlog item #4 — widget reads cached data from DataStore)
- Pulsing red dot animation (Glance doesn't support frame-by-frame animation — use static red dot indicator)
- Widget configuration Activity (keep existing WidgetSettings screen approach)

---

## Step-by-Step Tasks

### Task 1: Create WidgetState model
- **ACTION**: Create `widget/src/main/kotlin/com/ventoux/netlens/widget/WidgetState.kt`
- **IMPLEMENT**: Data class with all fields from the "New Widget State Model" section above. Include companion object with default/stub factory: `fun stub() = WidgetState()` and `fun isStale(thresholdMs: Long = 300_000L)` computed property.
- **MIRROR**: `IpWidgetState` pattern — flat data class, no sealed hierarchy
- **IMPORTS**: none (pure data class)
- **GOTCHA**: `speedMbps = -1f` and `latencyMs = -1L` as sentinel values for "not measured" — never display these raw
- **VALIDATE**: Compiles, no references to old `IpWidgetState` yet

### Task 2: Create WidgetStateDefinition
- **ACTION**: Create `widget/src/main/kotlin/com/ventoux/netlens/widget/WidgetStateDefinition.kt`
- **IMPLEMENT**: DataStore-backed `GlanceStateDefinition<Preferences>` with `PreferencesKey` for every field in `WidgetState`. Include `Preferences.toWidgetState()` extension and `WidgetState.toPreferences()` mapper. Keep `CAROUSEL_PAGE_KEY` for backward compat if needed by standard widget.
- **MIRROR**: Existing `IpWidgetStateDefinition` pattern
- **IMPORTS**: `androidx.datastore.preferences.core.*`, `androidx.glance.state.GlanceStateDefinition`
- **GOTCHA**: DataStore file name must differ from old `ip_widget_prefs` to avoid migration issues — use `netlens_widget_state`
- **VALIDATE**: Both mappers are inverse of each other (round-trip test)

### Task 3: Extend Deeplink constants
- **ACTION**: Update `widget/src/main/kotlin/com/ventoux/netlens/widget/util/Deeplink.kt`
- **IMPLEMENT**: Add constants for HOME, POSTURE, WIFI_AUDIT, SPEED_TEST, LATENCY, DEVICES. Add `fun issue(id: String)` and `fun triggerScan()` constants.
- **MIRROR**: Existing Deeplink object pattern
- **VALIDATE**: All constants use `SCHEME://HOST/route` format matching `ToolDestination.route` values

### Task 4: Create WidgetTheme
- **ACTION**: Create `widget/src/main/kotlin/com/ventoux/netlens/widget/ui/WidgetTheme.kt`
- **IMPLEMENT**: Object with functions:
  - `scoreColor(grade: String): Color` — green for A/B, amber for C, red for D/F, gray for empty
  - `scoreBorderColor(grade: String): Color` — same logic, slightly different shade for border
  - `encryptionIcon(type: String): String` — "✓" for WPA3, "" for WPA2, "⚠" for WEP
  - `encryptionColor(type: String): Color` — green for WPA3, default for WPA2, red for WEP
  - `speedColor(label: String): Color` — green/amber/red
  - Constants: `WIDGET_BG_COLOR`, `WIDGET_CORNER_RADIUS = 16.dp`, `WIDGET_PADDING = 12.dp`
- **MIRROR**: Existing `WidgetColor` approach but simplified — no user-configurable colors, just score-driven
- **VALIDATE**: All color functions handle empty/null inputs gracefully

### Task 5: Create CompactWidgetContent (2x1)
- **ACTION**: Create `widget/src/main/kotlin/com/ventoux/netlens/widget/ui/CompactWidgetContent.kt`
- **IMPLEMENT**: `@Composable fun CompactWidgetContent(state: WidgetState)`
  - Layout: `Row` — shield badge left, network name center, status line right
  - Shield: Text emoji "🛡" colored by score grade, or gray circle if unscanned
  - Network: `state.ssid ?: "Mobile"` truncated to 1 line
  - Status: "All clear" / "${issueCount} issues" / "Not scanned"
  - Entire widget clickable → `Deeplink.HOME`
  - Disconnected state: "No connection" in muted text
  - Missing permissions: "Grant location to scan"
- **MIRROR**: Existing `SmallWidgetContent` structure (Column with modifier chain)
- **IMPORTS**: `androidx.glance.*`, `WidgetTheme`, `WidgetState`, `Deeplink`
- **GOTCHA**: Glance `Text` doesn't support `SpannableString` — use separate Text composables for different colors
- **VALIDATE**: Preview in widget host, all three states render (connected, disconnected, no permission)

### Task 6: Create StandardWidgetContent (2x2)
- **ACTION**: Create `widget/src/main/kotlin/com/ventoux/netlens/widget/ui/StandardWidgetContent.kt`
- **IMPLEMENT**: `@Composable fun StandardWidgetContent(state: WidgetState)`
  - Header row: Large score badge (left, dominant) + Network name + encryption badge (right)
  - IP row: Flag emoji + public IP — both clickable → `Deeplink.IPINFO`
  - Stats row: Latency chip + Device count chip — each clickable to respective screen
  - Footer: "Last scanned X min ago" — clickable → trigger scan action
  - Score badge: large colored letter (A-F) or "?" if unscanned
  - Score color drives a subtle left border accent
  - Stale data: footer shows "⏳ Stale" prefix if >5 min
  - Scan running: footer shows "Scanning..." instead of timestamp
- **MIRROR**: Existing `MediumWidgetContent` structure
- **IMPORTS**: Same as Task 5 plus `TriggerScanAction`
- **GOTCHA**: Glance `Row` weight distribution via `GlanceModifier.defaultWeight()` — score badge should be fixed width, rest fills
- **VALIDATE**: All tap targets fire distinct deep links

### Task 7: Create DashboardWidgetContent (4x2)
- **ACTION**: Create `widget/src/main/kotlin/com/ventoux/netlens/widget/ui/DashboardWidgetContent.kt`
- **IMPLEMENT**: `@Composable fun DashboardWidgetContent(state: WidgetState)`
  - Header row: "NetLens" text left + Score badge right (score color = accent border)
  - Network row: SSID + encryption badge + ISP/ASN name muted below
  - IP row: Flag emoji + public IP + country name — clickable → IP Info
  - Speed row: Speed pill — "⚡ Fast · 94 Mbps" / "~ Medium · 22 Mbps" / "▼ Slow · 4 Mbps" / "Not tested" — clickable → Speed Test
  - Alert row: Top issue with "→" or "✓ No threats detected" — clickable → issue detail or home
  - Stats row: Latency chip + Device count chip
  - Footer: "Last scanned X min ago" — clickable → trigger scan
  - Red dot indicator: if `issueCount > 0` or `topIssue.isNotEmpty()`, show "🔴" prefix on alert row
- **MIRROR**: Existing `WideWidgetContent` structure (two-section layout)
- **GOTCHA**: 4x2 has more vertical space but still limited — keep rows compact, single line each
- **VALIDATE**: All 7 rows render without overflow; all 6+ tap targets work

### Task 8: Rewrite NetLensWidget to route to new sizes
- **ACTION**: Rewrite `widget/src/main/kotlin/com/ventoux/netlens/widget/NetLensWidget.kt`
- **IMPLEMENT**: Three separate `GlanceAppWidget` subclasses:
  - `CompactWidget` → uses `CompactWidgetContent`
  - `StandardWidget` → uses `StandardWidgetContent`
  - `DashboardWidget` → uses `DashboardWidgetContent`
  Each reads `WidgetState` from the new `WidgetStateDefinition`. Apply dark background with score-driven border color via `WidgetTheme`.
- **MIRROR**: Existing `NetLensWidget.provideGlance()` pattern
- **GOTCHA**: All three widgets share the same DataStore for state — only the layout differs
- **VALIDATE**: Each widget class compiles independently

### Task 9: Create widget receivers
- **ACTION**: Create 3 receiver classes in `widget/src/main/kotlin/com/ventoux/netlens/widget/receiver/`
- **IMPLEMENT**:
  ```kotlin
  class CompactWidgetReceiver : GlanceAppWidgetReceiver() {
      override val glanceAppWidget = CompactWidget()
  }
  class StandardWidgetReceiver : GlanceAppWidgetReceiver() {
      override val glanceAppWidget = StandardWidget()
  }
  class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
      override val glanceAppWidget = DashboardWidget()
  }
  ```
  Each registers `ConnectivityManager.NetworkCallback` in `onEnabled()` to trigger refresh on network change (mirror existing `NetLensWidgetReceiver` pattern).
- **MIRROR**: Existing `NetLensWidgetReceiver` network callback registration
- **VALIDATE**: Each receiver resolves in manifest

### Task 10: Create widget info XMLs
- **ACTION**: Create 3 XML files in `widget/src/main/res/xml/`
- **IMPLEMENT**:
  - `widget_compact.xml`: minWidth=110dp, minHeight=40dp, targetCellWidth=2, targetCellHeight=1, previewLayout, description
  - `widget_standard.xml`: minWidth=180dp, minHeight=110dp, targetCellWidth=2, targetCellHeight=2
  - `widget_dashboard.xml`: minWidth=250dp, minHeight=110dp, targetCellWidth=4, targetCellHeight=2
  All: `android:resizeMode="horizontal|vertical"`, `android:widgetCategory="home_screen"`, `android:updatePeriodMillis="0"` (we handle updates via broadcasts, not system timer)
- **MIRROR**: Existing `widget_wide.xml` pattern
- **GOTCHA**: `updatePeriodMillis=0` means system won't auto-update — we control all updates. Must not be >0 or it adds polling.
- **VALIDATE**: XMLs parse correctly

### Task 11: Create TriggerScanAction
- **ACTION**: Create `widget/src/main/kotlin/com/ventoux/netlens/widget/action/TriggerScanAction.kt`
- **IMPLEMENT**: `ActionCallback` that:
  1. Sets `isScanRunning = true` in WidgetState DataStore
  2. Refreshes all widgets (shows spinner in footer)
  3. Fires `enqueueWidgetRefresh(context)` to run the refresh worker
  Note: actual scan service doesn't exist yet — for now, the refresh worker just refreshes network data. When posture score ships, this will trigger a full scan.
- **MIRROR**: Existing `CopyPublicIpAction` pattern (ActionCallback with onAction override)
- **VALIDATE**: Tap on footer triggers worker, widget shows "Scanning..." briefly

### Task 12: Rewrite IpWidgetRefreshWorker
- **ACTION**: Rewrite `widget/src/main/kotlin/com/ventoux/netlens/widget/IpWidgetRefreshWorker.kt` → rename to `WidgetRefreshWorker.kt`
- **IMPLEMENT**: `CoroutineWorker` that:
  1. Reads connectivity via `ConnectivityManager`
  2. Gets SSID from `WifiManager` (requires `ACCESS_FINE_LOCATION` on Android 12+)
  3. Gets public IP, country, ISP from current API (ip-api.com for now; IPinfo when #4 ships)
  4. Gets country flag emoji via `countryCode.toFlagEmoji()` (existing util)
  5. Reads cached posture score from DataStore (stub: empty grade)
  6. Reads cached speed test result from DataStore (stub: -1f)
  7. Reads cached latency from DataStore (stub: -1L)
  8. Reads cached device count from DataStore (stub: 0)
  9. Writes full `WidgetState` to DataStore
  10. Calls `updateAll()` on all three widget classes
  11. Sets `isScanRunning = false`
- **MIRROR**: Existing `IpWidgetRefreshWorker` pattern
- **GOTCHA**: Must handle missing location permission gracefully — if no SSID access, set `ssid = null`
- **VALIDATE**: Worker completes successfully, DataStore updated, widgets refresh

### Task 13: Fix AndroidManifest
- **ACTION**: Update `app/src/main/AndroidManifest.xml`
- **IMPLEMENT**:
  1. Remove old receivers: `com.ventoux.netlens.widget.NetLensWidgetReceiver`, `us.beary.netlens.widget.WideWidgetReceiver`, `us.beary.netlens.widget.BannerWidgetReceiver`
  2. Add 3 new receivers with correct `com.ventoux.netlens.widget.receiver.*` package:
     ```xml
     <receiver android:name="com.ventoux.netlens.widget.receiver.CompactWidgetReceiver" android:exported="true">
         <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
         <meta-data android:name="android.appwidget.provider" android:resource="@xml/widget_compact" />
     </receiver>
     ```
     (repeat for Standard and Dashboard)
  3. Keep existing `netlens://feature` intent filter on MainActivity
- **GOTCHA**: Existing users with old widgets will lose them on update — this is acceptable for a full redesign
- **VALIDATE**: `./gradlew :app:assembleDebug` succeeds, no manifest merge errors

### Task 14: Add deep link routing to MainActivity
- **ACTION**: Update `app/src/main/kotlin/com/ventoux/netlens/MainActivity.kt`
- **IMPLEMENT**: Override `onNewIntent()` and extract deep link from `onCreate()`/`onNewIntent()`:
  ```kotlin
  override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      enableEdgeToEdge()
      setContent {
          NetLensTheme {
              NetLensApp(deepLinkUri = intent?.data?.toString())
          }
      }
  }
  override fun onNewIntent(intent: Intent) {
      super.onNewIntent(intent)
      setIntent(intent)
      // NavController handles re-read via rememberSaveable or by re-composing
  }
  ```
  Pass `deepLinkUri` through `NetLensApp` → `NetLensNavHost` → `LaunchedEffect` that navigates to the matching route.
- **MIRROR**: Standard single-Activity deep link pattern
- **GOTCHA**: `launchMode="singleTop"` is already set — `onNewIntent` is needed for warm starts. For cold starts, `onCreate` intent has the URI.
- **VALIDATE**: Tap widget element → app opens to correct screen (not just home)

### Task 15: Wire deep link routing in NetLensNavHost
- **ACTION**: Update `app/src/main/kotlin/com/ventoux/netlens/navigation/NetLensNavHost.kt`
- **IMPLEMENT**: Accept `deepLinkUri: String?` parameter. In a `LaunchedEffect(deepLinkUri)`, parse the URI and navigate:
  ```kotlin
  LaunchedEffect(deepLinkUri) {
      deepLinkUri?.let { uri ->
          val path = Uri.parse(uri).pathSegments.firstOrNull() ?: return@let
          val route = ToolDestination.entries.find { it.route == path }?.route
          if (route != null) navController.navigate(route)
      }
  }
  ```
- **MIRROR**: Existing `composable(ToolDestination.*.route)` mappings
- **GOTCHA**: Must handle unknown routes gracefully (no-op). Must not navigate on config change (use `deepLinkUri` as key, not `Unit`).
- **VALIDATE**: All deep link constants in `Deeplink.kt` resolve to valid `ToolDestination` routes

### Task 16: Add widget string resources
- **ACTION**: Update `widget/src/main/res/values/strings.xml`
- **IMPLEMENT**: Add strings for all widget text:
  ```xml
  <string name="widget_all_clear">All clear</string>
  <string name="widget_issues_count">%1$d issues</string>
  <string name="widget_not_scanned">Not scanned</string>
  <string name="widget_no_connection">No connection</string>
  <string name="widget_grant_permission">Grant location to scan</string>
  <string name="widget_last_scanned">Last scanned %1$s ago</string>
  <string name="widget_scanning">Scanning…</string>
  <string name="widget_stale">Stale</string>
  <string name="widget_not_tested">Not tested</string>
  <string name="widget_no_threats">No threats detected</string>
  <string name="widget_devices">%1$d devices</string>
  <string name="widget_latency_ms">%1$d ms</string>
  <string name="widget_speed_fast">Fast</string>
  <string name="widget_speed_medium">Medium</string>
  <string name="widget_speed_slow">Slow</string>
  <string name="widget_speed_mbps">%1$.0f Mbps</string>
  <string name="widget_netlens">NetLens</string>
  <string name="widget_score_unknown">?</string>
  ```
- **VALIDATE**: No missing resource errors at compile time

### Task 17: Clean up old widget files
- **ACTION**: Delete old widget files that are fully replaced:
  - `widget/src/main/kotlin/.../IpWidgetState.kt`
  - `widget/src/main/kotlin/.../IpWidgetStateDefinition.kt` (if fully replaced)
  - `widget/src/main/kotlin/.../NetLensWidgetReceiver.kt` (old receiver)
  - `widget/src/main/res/xml/netlens_widget_info.xml`
  - `widget/src/main/res/xml/widget_wide.xml`
  - `widget/src/main/res/xml/widget_banner.xml`
  - `widget/src/main/res/xml/widget_small.xml`
  - `widget/src/main/res/xml/widget_medium.xml`
- **ACTION**: Update `feature/widgetsettings/` to reference new widget classes and size enum
- **GOTCHA**: Grep for all references to deleted classes/files before removing. `WidgetSettingsViewModel` references `WidgetSize` enum — must update.
- **VALIDATE**: `./gradlew assembleDebug` succeeds with zero references to deleted types

### Task 18: Update WidgetRefresh for broadcast-driven updates
- **ACTION**: Update `widget/src/main/kotlin/com/ventoux/netlens/widget/WidgetRefresh.kt`
- **IMPLEMENT**:
  - `refreshAllWidgets()` → call `updateAll()` on all 3 widget classes
  - Remove 15-minute periodic polling (spec says zero polling)
  - Keep `enqueueWidgetRefresh()` for one-time refresh triggers
  - Add `fun onNetworkChanged(context: Context)` — called from receiver network callbacks
  - Add `fun onScanCompleted(context: Context)` — called after scan finishes
  - Add `fun onSpeedTestCompleted(context: Context)` — called after speed test
- **MIRROR**: Existing refresh functions
- **GOTCHA**: Must not remove periodic refresh until new broadcast-driven approach is verified working
- **VALIDATE**: Widget refreshes on network change, not on timer

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| WidgetState.isStale | timestamp 6 min ago | true | No |
| WidgetState.isStale | timestamp 2 min ago | false | No |
| WidgetState.isStale | timestamp 0 (never scanned) | true | Yes |
| WidgetTheme.scoreColor("A") | "A" | Green | No |
| WidgetTheme.scoreColor("F") | "F" | Red | No |
| WidgetTheme.scoreColor("") | "" | Gray | Yes |
| WidgetTheme.encryptionIcon("WPA3") | "WPA3" | "✓" | No |
| WidgetTheme.encryptionIcon("WEP") | "WEP" | "⚠" | No |
| Deeplink route parsing | "netlens://feature/ipinfo" | pathSegment = "ipinfo" | No |
| Deeplink route parsing | "netlens://feature/unknown" | no matching ToolDestination | Yes |
| WidgetState round-trip | state → prefs → state | equal | No |
| Speed label from mbps | 30f | "Fast" | No |
| Speed label from mbps | 10f | "Medium" | No |
| Speed label from mbps | 3f | "Slow" | No |
| Speed label from mbps | -1f | "" (not tested) | Yes |

### Edge Cases Checklist
- [ ] Widget with no data (fresh install, never scanned)
- [ ] Widget with stale data (>5 min since last scan)
- [ ] Widget with missing location permission
- [ ] Widget on mobile data (no SSID)
- [ ] Widget when offline (isConnected = false)
- [ ] Deep link to screen that doesn't exist yet (posture, wifiaudit, speedtest)
- [ ] Deep link with malformed URI
- [ ] Widget refresh when app is not running
- [ ] Multiple widgets of different sizes on same home screen
- [ ] Very long SSID (>20 chars) truncation

---

## Validation Commands

### Static Analysis
```bash
./gradlew assembleDebug
```
EXPECT: Zero compilation errors

### Unit Tests
```bash
./gradlew :widget:testDebugUnitTest
```
EXPECT: All tests pass including new WidgetState and WidgetTheme tests

### Full Test Suite
```bash
./gradlew testDebugUnitTest
```
EXPECT: No regressions in any module

### Install and Verify
```bash
./gradlew installDebug
```
EXPECT: Add each widget size to home screen; verify layout, colors, tap behaviors

### Manual Validation
- [ ] Add 2x1 compact widget — score badge visible, SSID shown, status line correct
- [ ] Add 2x2 standard widget — score badge dominant, IP row shows flag + IP, stats row shows latency + devices
- [ ] Add 4x2 dashboard widget — all 7 rows render, speed pill shown, alert row shown
- [ ] Tap score badge → app opens (to home for now, posture when built)
- [ ] Tap flag/IP → IP Info screen opens
- [ ] Tap latency chip → Ping screen opens
- [ ] Tap device count → LAN Scan screen opens
- [ ] Tap footer → widget shows "Scanning..." briefly, then refreshes
- [ ] Turn Wi-Fi off → widget shows "No connection"
- [ ] Turn Wi-Fi on → widget refreshes automatically via network callback
- [ ] Unscanned state: score shows "?" in gray, status shows "Not scanned"
- [ ] Old us.beary.netlens receivers no longer in manifest

---

## Acceptance Criteria
- [ ] GitHub issues created for all 15 backlog items with labels, effort, AC
- [ ] Three widget sizes render correctly on a physical device
- [ ] Security score badge is the most visually dominant element in all sizes
- [ ] Score color drives widget accent (green/amber/red/gray)
- [ ] All tap targets deep-link to correct screens
- [ ] Deep links work from cold start (app fully stopped)
- [ ] Widget refreshes on network change (not on timer)
- [ ] Stale data shows "Stale" indicator but still displays old values
- [ ] Missing permissions show helpful message, not blank/crash
- [ ] Manifest bug with old package name is fixed
- [ ] Zero polling in widget code
- [ ] All existing tests pass

## Completion Checklist
- [ ] Code follows discovered patterns (Glance composables, DataStore state, ActionCallback)
- [ ] Error handling: graceful fallback on missing data, permissions, network
- [ ] Tests follow test patterns (JUnit 5, backtick names, hand-written fakes)
- [ ] No hardcoded values (string resources used)
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation
- [ ] Old widget code fully removed (no dead WidgetSize.BANNER references)
- [ ] WidgetSettings screen updated for new 3-size model

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Glance layout limitations for 4x2 dashboard | Medium | Medium | Test on device early; simplify rows if needed |
| Deep link routing breaks on config change | Low | High | Use `LaunchedEffect` keyed on URI, not Unit |
| Existing widget users lose widgets on update | High | Low | Acceptable for major redesign; no migration path for Glance widgets |
| Stale DataStore from old widget version | Medium | Low | New DataStore file name avoids conflict |
| `toFlagEmoji()` rendering on older devices | Low | Low | Existing util already works in current widget |
| WidgetSettings screen breaks after size enum change | High | Medium | Task 17 explicitly handles this |

## Notes

### Implementation Order
The recommended implementation order is:
1. Tasks 1-4 (data model + deep links + theme) — foundation
2. Tasks 5-7 (three widget composables) — UI
3. Tasks 8-10 (widget classes + receivers + XML) — wiring
4. Task 11 (scan action) — interaction
5. Task 12 (refresh worker) — data population
6. Tasks 13-15 (manifest + deep link routing) — deep links
7. Task 16 (strings) — can be done anytime
8. Tasks 17-18 (cleanup + refresh updates) — finalization

### GitHub Issues
Task 1 (backlog) should be executed first as a separate commit. Use `gh issue create` for each issue. Apply labels: `priority: P0/P1/P2/P3`, `type: feature`, `size: S/M/L/XL`. Reference this plan in each issue body.

### Data Contract Philosophy
The widget is designed to work with stub data from day one. As each backing feature ships (#1, #4, #5, #7, #8), it writes its data to the shared DataStore, and the widget picks it up on next refresh — no widget code changes needed. This is the key architectural decision that unblocks widget development from the full feature backlog.
