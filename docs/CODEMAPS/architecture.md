<!-- Generated: 2026-08-17 | Files scanned: 36 modules | Token estimate: ~870 -->

# Architecture

Package: `com.ventouxlabs.netlens`

## Module Graph

```
app (single Activity)
├── feature:* × 24      (all apply netlens.android.feature = library + compose +
│                        hilt + lifecycle + nav + core:billing + core:ui;
│                        per-tool index lives in features.md, not duplicated here)
├── widget              (Glance home screen widget; pure derivations extracted to
│                        WidgetSnapshot.kt / WidgetRefresh.kt for JVM testing)
├── core:network        (connectivity, SSRF guard, ResultExporter, DisplayText.flatten —
│                        sanitises network-supplied names at ingestion)
├── core:data           (Room DB v16, 22 entities, 19 DAOs)
├── core:scan           (LAN scan engines + DeviceInventoryRepository + NewDeviceNotifier
│                        + port/service domain: PortScanner, PortResult, PortRiskLevel,
│                        WellKnownPorts, ServiceLauncher, ServiceIntentLauncher)
├── core:billing        (ProStatus interface, LocalProStatus)
├── core:ui             (StatusColors, Spacing, StampChip, StatItem, UiText,
│                        ResultActions — the shared TopAppBar copy/share row, 14 screens)
├── core:oui            (MAC vendor lookup)
└── baselineprofile     (Macrobenchmark profile generation)

test-only, never on a runtime classpath (consumed via testImplementation):
  core:scan-testing     (Fake* engine doubles incl. FakePortScanner)
  core:data-testing     (FakeNetworkEventDao)
  core:network-testing  (SsrfRedirectProbe)
```

**Feature modules must not depend on each other.** The graph is flat: `feature:* → core:*`
only, enforced as of PR #129 — `feature:lanscan` previously pulled in the whole of
`feature:portscan` for nine shared port types, which put portscan's UI, DI graph and
strings on lanscan's runtime classpath. Anything two features both need goes in `core:*`.

`:baselineprofile` is on master (`settings.gradle.kts:55`).

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
| `netlens.android.feature` | library + compose + hilt + lifecycle + nav + billing + core:ui |
| `netlens.android.screenshot` | Paparazzi composition smoke tests (no golden images) |
| `netlens.android.robolectric` | Opt-in Robolectric (JUnit4) — applied only to `core:data` and `widget` |

## Navigation

String-based routes via `ToolDestination` enum. Cross-tool navigation via `onNavigateToTool: (route, query) -> Unit`. Deep links resolved in `MainActivity.onNewIntent`.

## SDK Targets

compileSdk 35, minSdk 29, Java 17.
