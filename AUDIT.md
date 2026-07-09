# NetLens UI/UX Audit — Pre-Redesign Baseline

Date: 2026-07-08. Scope: presentation layer only (screens, navigation, theme, widgets).
Produced as step 1 of the approved redesign; no code has been changed.

---

## 1. Screen inventory

**24 `ToolDestination` entries** (`app/src/main/kotlin/com/ventouxlabs/netlens/navigation/ToolDestination.kt:37-214`),
**23 feature modules** (CLAUDE.md's "20 tools" is stale — `vpnstatus` exists as a feature and destination).

| Category | Destinations |
|---|---|
| Network Info | Posture (`:45`, hidden from grid), IpInfo, Whois, CellTower |
| Connectivity | Ping, Dns, Traceroute, SpeedTest |
| Discovery | LanScan, Mdns, WifiAnalyzer, PortScan |
| Security & Web | WifiAudit, VpnStatus, DnsLeak, Tls, HttpTester |
| Tools | Wol, Monitor, IpCalc, NetLog, History, WidgetSettings (`:200`, hidden from grid) |

Every feature module follows `<Name>Screen.kt` + `<Name>ViewModel.kt`; all 23 screens use their own
`Scaffold` + `TopAppBar` + back arrow. Two body archetypes:

- **Input-driven** (12 screens: dns, ping, traceroute, portscan, whois, tls, ipcalc, lanscan, wol, monitor, ipinfo, history): text field + Start button + results.
- **Auto-run/status** (posture, wifiaudit, wifi, celltower, vpnstatus, netlog, speedtest): run on entry, render cards/lists.

Results containers are split 14 `LazyColumn` vs 7 `Column(verticalScroll)` (dnsleak, ipcalc, ipinfo, posture, tls, vpnstatus, widgetsettings) — long technical output in non-lazy columns.

## 2. Navigation structure

- **Hub-and-spoke, one effective top-level destination.** `NetLensApp.kt:13-14` hosts a bare `NavHost` (no app-level Scaffold, no bottom nav, no drawer). `startDestination = "home"` (`NetLensNavHost.kt:49`). Every tool is a flat sibling route.
- **Home is a hybrid launcher/dashboard** (`app/.../ui/home/HomeScreen.kt`): `NetworkStatusCard` (connection + posture grade, taps → Posture) and `LatencyMonitorCard` at top, then search bar, Favorites/Recent chip rows, then **22 tools** as a fake grid (`chunked(2)` manual Rows with trailing `Spacer`, `HomeScreen.kt:205-214, 268-295` — not `LazyVerticalGrid`; fixed 2 columns regardless of screen width).
- **No app settings screen exists at all** — no theme toggle, no preferences UI. The only settings surface is `feature/widgetsettings`, which is `isVisibleInGrid = false` and reachable **only** via `netlens://feature/widgetsettings` deep link (`DeepLinkRouter.kt:21`) — no in-app path.
- **Stale deep-link fallbacks**: `DeepLinkRouter.kt:22-28` maps `wifiaudit`, `speedtest`, `scan` to `"home"` with a stale "screens that don't exist yet" comment, even though WifiAudit and SpeedTest screens exist — widget taps land on the wrong screen.
- Posture (the closest thing to a "security dashboard") is reachable only through the home status card.

## 3. Theme / token setup

Files: `app/.../ui/theme/Color.kt`, `Theme.kt`, `Type.kt`. No `Shapes.kt` (M3 defaults).

- **Palette**: Teal/Cyan Material scheme (Teal200/500/700/800, Cyan200/500/900) + dark surfaces (`0xFF0F1318` bg, `0xFF1A1F27` surface) and light surfaces (`0xFFF8FAFA` bg, white surface). Both `LightColorScheme` and `DarkColorScheme` exist (`Theme.kt:16-38`) — **but only 9 of the M3 color roles are set**; `primaryContainer`, `secondaryContainer`, `error`, `outline` etc. fall back to M3 defaults. `ToolChip` renders on `secondaryContainer` (`ToolChip.kt:33,46,52`) → off-brand default lavender chips.
- **Dynamic color silently defeats the palette**: `dynamicColor = true` on Android 12+ (`Theme.kt:43,47-49`) replaces the hand-built schemes with wallpaper-derived ones. On most modern devices the curated palette never ships. No opt-out toggle.
- **Light/dark**: follows `isSystemInDarkTheme()` only (`Theme.kt:42`) — no manual override anywhere (and no settings screen to put one in).
- **Typography** (`Type.kt`): `InterFontFamily` (Regular/Medium/SemiBold/Bold) for everything, `MonoFontFamily` (JetBrains Mono) on `labelSmall` only — the app's convention for technical data. No tabular-figure settings anywhere.
- **Status tokens exist but are barely adopted**: `core/ui/.../StatusColors.kt` defines `pass/warn/fail/info/muted` (`0xFF4CAF50 / F59E0B / EF4444 / 3B82F6 / 9E9E9E`) via `LocalStatusColors` (`Theme.kt:59-62`). Fixed hex, **identical in light and dark** (not theme-aware). Adopted by only 3 screens (portscan, ipinfo, lanscan `HostDetailSheet`).
- **Spacing tokens exist with ZERO adoption**: `core/ui/Spacing.kt` (4/8/12/16/24/32) — `grep Spacing.` in `feature/` returns nothing.

## 4. Status-color chaos (the core cross-cutting jank)

The same good/warn/bad concept is expressed through **four parallel mechanisms** with different hexes:

| Meaning | `core/ui` StatusColors | posture (`PostureScreen.kt:64-69,196-198`) | vpnstatus (`VpnStatusScreen.kt:42-45`) | widget (`WidgetTheme.kt:18-28`) | wifiaudit/dnsleak/monitor |
|---|---|---|---|---|---|
| good | `4CAF50` | `4CAF50` + `8BC34A` | `388E3C` | `4CAF50` / `388E3C` | colorScheme `primary(Container)` |
| warn | `F59E0B` | `FFC107` + `FF9800` | `F57C00` | `FFC107` / `F57C00` | `tertiaryContainer` (wifiaudit); **monitor & dnsleak have no warn state at all** |
| bad | `EF4444` | `F44336` | `D32F2F` | `F44336` / `D32F2F` | colorScheme `error(Container)` |

Grade colors are additionally duplicated in three widget locations (`WidgetTheme.scoreColor:39-44`, `WidgetRefreshWorker.computeWidgetScore:293-297`, `gradeColorArgb:308-312`).
`PostureScreen.kt:208,210` paints `Color.White` on fixed-hex chips — contrast locked regardless of theme.

## 5. Widget implementation

**Framework: Jetpack Glance 1.1.1** (`gradle/libs.versions.toml:9,55-56`; `widget/build.gradle.kts:19-20`). `glance-material3` is a dependency but `GlanceTheme` is **never used**.

**Four widgets**, receivers registered in `app/src/main/AndroidManifest.xml:42-84`, provider XML in `widget/src/main/res/xml/`:

| Widget | Cell | SizeMode | Content |
|---|---|---|---|
| Compact | 2×1 | Single (default) | flag + VPN lock + WAN IP; signal + LAN IP |
| Standard | 2×2 | Single (default) | grade badge, SSID + encryption, IP, latency + device count, staleness footer |
| Dashboard | 4×1 | Exact | WAN/LAN IPs + refresh, DNS/RSSI row, 4 action chips |
| FourByTwo | 4×2 | Exact | header + Dashboard content + status line + tool chips |

All: `updatePeriodMillis = 1800000` (30 min), `resizeMode="horizontal|vertical"`, **no `previewImage`/`previewLayout`** (generic picker preview), initial layout is a bare "NetLens" TextView (`widget/src/main/res/layout/widget_initial.xml`).

**Data pipeline** (sound — keep as-is): `WidgetRefreshWorker.kt:43-255` (30-min WorkManager periodic + connectivity-callback one-shots, `WidgetRefresh.kt:45-82`) gathers ~40 keys → Glance DataStore `netlens_widget_state` (`WidgetStateDefinition.kt:16-79`) → `updateAll()`. Widgets render only from DataStore; public-IP fetch is consent-gated.

**Widget problems:**
1. **Dead settings pipeline (worst finding).** `feature/widgetsettings` collects background color/opacity/accent/text size/corner radius/size into a *separate* DataStore (`widget_preferences`, `WidgetPreferencesRepository.kt:19-73`) — **no widget composable ever reads it**. "Apply" (`WidgetSettingsViewModel.kt:35-39`) just refreshes data. Every widget-appearance setting is a non-functional mock.
2. **Hardcoded dark-only theming.** `WidgetTheme.kt:6-96`: fixed `0xE6161616`/navy backgrounds, white-alpha text, fixed hex status colors. No `GlanceTheme`, no day/night `ColorProvider`, no `values-night/`. Same dark card on every wallpaper/theme.
3. **SizeMode inconsistency, no responsive layouts.** Compact/Standard = `Single`, Dashboard/4×2 = `Exact`, none `Responsive` — resizable widgets just scale/clip one layout.
4. **Emoji as icons** (🔍📡🌐🚪🛡⚡, `DashboardFullContent.kt:216-247`) vs the app's vector icons — inconsistent cross-device rendering.
5. **No loading/error states** — only em-dash placeholders (`CompactFullContent.kt:142`, `StandardWidgetContent.kt:56,127`); worker failure = silently stale.
6. Glance constraint documented in-code: extracting weighted Rows renders blank (`DashboardFullContent.kt:40-47`) — redesign must respect this.
7. Unused duplicate palette in `widget/src/main/res/values/colors.xml:3-5`.

## 6. Jank list (file/line-specific)

**Theme/tokens**
- Dynamic color bypasses brand palette on API 31+ — `Theme.kt:43,47-49`.
- Incomplete color schemes (9 roles) → default-M3 chips — `Theme.kt:16-38`, `ToolChip.kt:33,46,52`.
- Status hex not theme-aware, duplicated inline — `StatusColors.kt:21-27`, `PostureScreen.kt:64-69`, `VpnStatusScreen.kt:42-45`, `WidgetTheme.kt:18-44`.
- 55 `Color(0x…)` literals repo-wide: widget 20, app-theme 17 (contained), posture 9, vpnstatus 4, core/ui 5.

**Layout/spacing**
- Spacing tokens unused; 16dp×129 / 8dp×157 / 4dp×121 / 12dp×82 dominate but 1/2/3/6/10/14/18/20dp drift is common (worst: widget 21 outlier sites, then speedtest, lanscan, netlog, httptester).
- Fake home grid via `chunked(2)` — `HomeScreen.kt:205-214,268-295`; fixed-width 80dp chips truncate — `ToolChip.kt:31`.
- Lifecycle observer wired inside a `LazyColumn` item — `HomeScreen.kt:121-146` (detaches when scrolled off).

**Typography**
- ~70 `fontSize =` bypasses; app-side: `NetworkStatusCard.kt:62` (22sp), `PostureScreen.kt:228` (64sp), `VpnStatusScreen.kt:139` (24sp), `ChannelGraph.kt:84,155,183` (8–9sp Canvas).
- Two mono conventions: `MonoFontFamily` via `labelSmall`/`.copy(...)` vs raw `FontFamily.Monospace` in `CellTowerScreen.kt:280,398,416`.
- **No tabular figures anywhere** — live numerics reflow/jitter in ping (`PingScreen.kt:336,415,454`) and speedtest (`SpeedTestScreen.kt:315`); `StatItem` is duplicated in both rather than shared.
- Manual sp arithmetic ~15× in `WidgetSettingsScreen.kt:150-251`.

**Hierarchy/UX**
- Unlabeled key/value card dumps: `IpInfoScreen.kt:330-386` (7 rows, no section title); celltower raw RF metric grid (`CellTowerScreen.kt:309,383`).
- 3 competing top-bar actions in netlog — `NetLogScreen.kt:96-135`.
- Monitor status is color-only (dot, `MonitorScreen.kt:255-266`) — accessibility risk; no warn state.
- Sub-48dp tap target: wifiaudit dismiss `IconButton(32.dp)` — `WifiAuditScreen.kt:253`.
- Home `TopAppBar` unthemed, no scroll behavior — `HomeScreen.kt:95-97`.
- Elevation is uniformly flat/default everywhere except one `elevatedCardColors` outlier (`DnsLeakScreen.kt:238`) and a raw `.background(color, CardDefaults.shape)` (`WolScreen.kt:237`).
- 16 hardcoded literal strings + emoji in widgetsettings preview (`WidgetSettingsScreen.kt`); other screens fully string-resourced.

**What's already good (build on, don't replace)**
- 100% `Scaffold`+`TopAppBar` shell consistency; 300+ `MaterialTheme.colorScheme` usages.
- wifiaudit's `FindingCard` (`WifiAuditScreen.kt:195-272`): severity → theme containers, plain-language title/description/guidance, dismiss (Critical non-dismissible), summary count chips — the model for the alert pattern in goal 5.
- netlog's date-grouped timeline + filter chips; existing hand-drawn theme-colored Canvas charts (wifi `ChannelGraph`, speedtest `SpeedGauge`+`SparklineChart`, monitor/ping bar charts) — good raw material for dashboard sparklines.
- Widget data pipeline (WorkManager + DataStore) already respects update-frequency constraints.

## 7. Proposals & open questions for the redesign plan

1. **Warning amber (proposed):** `warn = #B07C22` on light / `#D9A84E` for text/icons on dark, with soft container `#EFE3C8` light / `#3A3020` dark — mirrors the approved accent structure (`#2C6155`/`#8FCABB`, `#DCE7E2`/`#1E3B34`) and holds ≥4.5:1 on paper/paperDark. Final hex to be confirmed at theme-build time with a contrast check.
2. **Typography tension to resolve:** approved direction says numeric/technical data in **Inter with tabular figures**, but the repo convention (CLAUDE.md + 19+ screens) is **JetBrains Mono** via `labelSmall`. Recommendation: Space Grotesk (display) + Inter (body, `tnum` for aligned numerics), keep JetBrains Mono only for true machine strings (IPs, MACs, hex) — decision needed.
3. **Dynamic color:** proposal is to remove/disable it so the paper/ink palette actually ships (it currently overrides everything on API 31+). Flagging since it's behavior removal.
4. **Widget settings:** the appearance settings are a dead pipeline. Options: (a) wire them for real, (b) cut appearance options and keep only meaningful config (which metrics to show), letting the new token system own appearance. Recommendation: (b) — decision needed before the widget pass.
5. **VpnStatus naming:** the feature *detects* VPN presence (fine for a monitoring app), but redesign copy/icons must avoid implying NetLens provides protection (per constraints).
6. **New Settings screen** needed as a first-class destination (theme override toggle lives here, per direction) — currently none exists.
7. **Fix stale deep links** (`DeepLinkRouter.kt:22-28`) as part of the nav-shell pass.
