<!-- Generated: 2026-07-20 | Files scanned: 32 modules | Token estimate: ~980 -->

# Architecture

Package: `com.ventouxlabs.netlens`

## Module Graph

```
app (single Activity)
├── feature:ipinfo      ─┐
├── feature:lanscan      │
├── feature:devices      │  (device inventory + Pro background watch)
├── feature:portscan     │
├── feature:dns          │
├── feature:ping         │
├── feature:traceroute   │
├── feature:wol          │
├── feature:tls          │
├── feature:whois        │
├── feature:httptester   ├─ all apply netlens.android.feature
├── feature:mdns         │  (= library + compose + hilt + billing)
├── feature:netlog       │
├── feature:monitor      │
├── feature:history      │
├── feature:widgetsettings│
├── feature:posture      │
├── feature:ipcalc       │
├── feature:speedtest    │
├── feature:wifi         │
├── feature:dnsleak      │
├── feature:wifiaudit    │
├── feature:vpnstatus    │
├── feature:celltower   ─┘
├── widget              (Glance home screen widget)
├── core:network        (connectivity, SSRF guard, export)
├── core:data           (Room DB, DAOs, entities)
├── core:scan           (LAN scan engines + DeviceInventoryRepository + NewDeviceNotifier;
│                        shared by feature:lanscan and feature:devices)
├── core:billing        (ProStatus interface, LocalProStatus)
├── core:ui             (shared UI tokens: StatusColors, Spacing)
└── core:oui            (MAC vendor lookup)
```

feature:lanscan and feature:devices both depend on core:scan; the feature graph stays
flat (feature:* depend only on core:*). A :baselineprofile Macrobenchmark module exists on
branch `perf/baseline-profile` (draft PR #108), not on master — generation is blocked on
Android 17 tooling.

## Entry Points

- `MainActivity` — single `@AndroidEntryPoint` Activity
- `NetLensApp` — root Composable, hosts scaffold + NavHost
- `NetLensNavHost` — routes `ToolDestination` enum entries to screens
- `HomeScreen` — grid grouped by `ToolCategory`

## Product Flavors

| Flavor | Purpose | Billing |
|--------|---------|---------|
| `foss` | F-Droid / source builds | Pro always on |
| `gplay`| Google Play | In-app purchase (`pro_unlock`) |

Flavor sources: `app/src/foss/` and `app/src/gplay/` (BillingModule + GplayProStatus).

## DI (Hilt)

- `@HiltAndroidApp` on `NetLensApplication`
- `@AndroidEntryPoint` on `MainActivity`
- `@HiltViewModel` on all ViewModels
- Feature DI: `@InstallIn(ViewModelComponent)` or `SingletonComponent`
- ProStatus: `CompositionLocalProvider(LocalProStatus provides proStatus)` in MainActivity

## Convention Plugins (build-logic/)

| Plugin | Applies |
|--------|---------|
| `netlens.android.application` | AGP app + Kotlin + SDK targets |
| `netlens.android.library` | AGP library + Kotlin + SDK targets |
| `netlens.android.compose` | Compose compiler + BOM + Material3 |
| `netlens.hilt` | Hilt + KSP |
| `netlens.android.feature` | library + compose + hilt + lifecycle + nav + billing |

## Navigation

String-based routes via `ToolDestination` enum. Cross-tool navigation via `onNavigateToTool: (route, query) -> Unit`. Deep links resolved in `MainActivity.onNewIntent`.

## SDK Targets

compileSdk 35, minSdk 29, Java 17.
