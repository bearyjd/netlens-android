# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NetLens is an Android network diagnostics toolkit (package `com.ventouxlabs.netlens`). It provides 20 network tools — ping, traceroute, DNS lookup, LAN scan, port scan, WHOIS, TLS inspector, HTTP tester, mDNS browser, WiFi analyzer, Wake-on-LAN, IP info, IP/subnet calculator, endpoint monitor, network log, speed test, security posture, cell tower, Wi-Fi audit, and DNS leak test — each in its own feature module.

For known verification gaps, missing test coverage, and prioritized next steps for making autonomous agent work on this repo more reliable, see `.agent_native/agent_roadmap.md`.

## Build Commands

```bash
./gradlew assembleFossDebug                    # Build FOSS debug APK (no billing)
./gradlew assembleGplayDebug                   # Build Google Play debug APK (with billing)
./gradlew :feature:ping:testFossDebugUnitTest  # Run tests for one module
./gradlew testFossDebugUnitTest                # Run all unit tests
```

Two product flavors: `foss` (F-Droid / source builds, Pro always on) and `gplay` (Google Play, Pro via in-app purchase). Only `:app` is flavored — feature/core/widget modules build and test via the unflavored task names (`assembleDebug`, `testDebugUnitTest`).

CI (`.github/workflows/ci.yml`) builds `assembleFossDebug`, then runs `testFossDebugUnitTest testGplayDebugUnitTest testDebugUnitTest` — **all three**, and they cover disjoint source sets. `testFossDebugUnitTest` reaches `src/test` + `src/testFoss`, `testDebugUnitTest` reaches the unflavored core/feature/widget modules, and `testGplayDebugUnitTest` is the only one that reaches `src/testGplay`, where the billing tests live. Dropping one skips tests silently instead of failing, so a two-task run reads green while proving nothing about gplay (`ci.yml:36-41` says the same). Between them they cover **every** module that has a `src/test` tree, not a fixed subset. If you add tests to a previously-untested module, CI picks them up automatically — no workflow edit needed.

**SDK targets**: compileSdk 35, minSdk 29, Java 17.

## Architecture

### Module Graph

```
app ──┬── feature:* (22 modules)  ── core:network
      ├── core:network                core:data
      ├── core:data                   core:billing
      ├── core:billing                core:oui
      ├── core:oui                    core:scan
      ├── core:scan                   core:ui
      ├── core:ui
      └── widget

test-only, never on a runtime classpath:
  core:scan-testing ── core:scan     (consumed via testImplementation)
  core:data-testing ── core:data
```

- **`app`** — single Activity (`MainActivity`), hosts `NetLensNavHost` which routes to all feature screens. Navigation uses string routes defined in the `ToolDestination` enum (`app/.../navigation/ToolDestination.kt`).
- **`core:network`** — connectivity monitoring (`NetworkMonitor`), SSRF guard, coroutine utilities, and result export (`export/ResultExporter`). No HTTP client library (features use Ktor or raw sockets directly).
- **`core:data`** — Room database (`NetLensDatabase`) with DAOs for endpoints, network events, WoL targets. Provides Hilt `DataModule`. Every schema change needs a `Migration` in `DataModule` — the builder only falls back destructively on *downgrade*.
- **`core:billing`** — `ProStatus` interface (`isPro: StateFlow<Boolean>`, `launchPurchase(activity)`) and `LocalProStatus` CompositionLocal (safe no-op default). Flavor-specific implementations: `app/src/foss/` has `FossProStatus` (always Pro), `app/src/gplay/` has `GplayProStatus` (Google Play Billing with `BillingClientWrapper` for testability, `EncryptedSharedPreferences` for purchase state, reconnect counter with max 3 attempts).
- **`core:oui`** — MAC address vendor lookup from OUI database.
- **`core:scan`** — LAN scanning engines (`SubnetScanner`, `ArpTableReader`, `SsdpScanner`, `NetBiosProber`, `LanMdnsScanner`, `PortScanner`), device fingerprinting, port/service domain models (`PortResult`, `PortRiskLevel`, `PortRiskClassifier`, `WellKnownPorts`, `ServiceLauncher`, `ServiceIntentLauncher`), and `DeviceInventoryRepository`. **Feature modules must not depend on each other** — `feature:lanscan` used to pull in the whole of `feature:portscan` for these nine port types, which put portscan's UI, DI graph and strings on lanscan's runtime classpath and let a portscan UI edit break lanscan's build. Anything two features both need belongs here.
- **`core:scan-testing` / `core:data-testing` / `core:network-testing`** — shared test doubles, consumed only via `testImplementation`. See "Testing".
- **`widget`** — Glance-based home screen widget.
- **`feature:*`** — each feature is self-contained with its own screen, ViewModel, DI module, and engine/domain layer. Share-export is gated behind `isPro` via `LocalProStatus`.

### Convention Plugins (`build-logic/`)

Feature modules apply `netlens.android.feature` which bundles: `netlens.android.library` + `netlens.android.compose` + `netlens.hilt` + lifecycle/navigation/billing dependencies. Core modules apply `netlens.android.library` + `netlens.hilt` individually.

### Feature Module Pattern

Each feature follows this structure:
```
feature/<name>/src/main/kotlin/com.ventouxlabs.netlens/feature/<name>/
├── <Name>Screen.kt          # Composable UI
├── <Name>ViewModel.kt       # @HiltViewModel with MutableStateFlow
├── di/<Name>Module.kt       # @Module/@InstallIn Hilt bindings
├── engine/                   # Domain logic (parser, client, etc.)
└── model/                    # UiState data class, domain models
```

**UI state pattern**: `MutableStateFlow<UiState>` exposed as `StateFlow`, updated via `.update { it.copy(...) }`. No MVI event sealed class — ViewModels expose individual action methods.

**Lazy list keys — namespace whenever two `items()` share a container.** If a `LazyColumn`/`LazyRow`/`LazyVerticalGrid` holds more than one keyed `items()` call over *different* lists, prefix each key with its section (`"new_${it.id}"`, `"survey-point-$id"`, `"${it.toolFilter.name}_${it.id}"`). Do this **even when the ids look disjoint**: `IllegalArgumentException: Key "1" was already used` crashed the Wi-Fi survey on its first capture because points and sessions come from tables with independent autoincrement sequences. Include every field that forms the row's identity — `HostDetailSheet` keyed on `riskLevel_port` while `HostPortResult` also carries a `protocol`, so 80/TCP and 80/UDP would have collided the moment anything emitted UDP.

A full sweep on 2026-08-01 found all 28 keyed call sites safe, so this is about the next one, not a live bug. The distinction that matters: keys that are safe *by construction* (namespaced) versus safe *by an invariant that lives somewhere else* — `DevicesScreen` was safe only because of a `partition` call 14 lines away. Single-list containers (portscan, ping, traceroute, wifiaudit) need no prefix. `groupBy` slices of one list (netlog, history's dates) are already disjoint; history namespaces anyway because its rows come from 11 different tables.

Guard it with a composition smoke test — see "Testing". Nine screens have them (wifi, lanscan, dns, monitor, ping, portscan, traceroute, devices, home). `DevicesScreen` and `HomeScreen` were the last two holdouts: their lists were inline in composables that take `hiltViewModel()`, which is unrenderable, so each needed the list lifted into a stateless `internal fun *Content(...)` first (`HomeScreen.kt:108`, `DevicesScreen.kt:136`). That extraction is the move whenever a screen resists a render test.

**Result export pattern**: All 18 tool ViewModels (Ping, Traceroute, DNS, PortScan, WHOIS, HttpTester, LanScan, TLS, IpInfo, IpCalc, mDNS, SpeedTest, WiFi, WifiSurvey, CellTower, Devices, DnsLeak, VpnStatus) expose `fun buildExportText(): String` which serialises current UI state to a plain-text string. Screens call `ResultExporter.shareAsText()` or `ResultExporter.copyToClipboard()` (both in `core:network/export/ResultExporter.kt`) from Share/Copy IconButtons in each screen's `TopAppBar`. Modules that did not previously depend on `core:network` or `compose.material.icons` had those dependencies added as part of this feature (ipcalc: both; whois, httptester, tls, mdns: `compose.material.icons`).

**Device inventory (`known_devices`)**: scan-derived columns (`hostname`, `ip`, `vendor`, `deviceType`, `osGuess`) are owned by `DeviceInventoryRepository.persistScan` via `KnownDeviceDao.updateLastSeen`; user-authored columns (`customName`, `tags`, `notes`, `location`) are owned by the Devices detail sheet via `KnownDeviceDao.updateUserDetails`. Keep those two write paths disjoint — a re-scan must never clobber what the user typed. Tags are a normalised comma-separated column; always read/write them through `DeviceTags` (`core:data`), and filter/search rows through `KnownDeviceSearch` so Devices and LAN Scan's Inventory tab stay in step.

**Wi-Fi coverage survey** (`feature:wifi`): `WifiSurveyViewModel` samples the live association through the `WifiSignalSampler` seam (fake it in tests — see `FakeWifiSignalSampler`) rather than `WifiManager.startScan()`, which is throttled to 4 scans/2 min on API 28+ and far too slow to walk with. A captured "spot" is an aggregate of `CAPTURE_SAMPLE_TARGET` samples, not one reading; aggregation lives in the pure `SurveyAggregator`.

**Service launch** (`core:scan`, used by portscan and lanscan): `ServiceLauncher.forPort` maps an open port to a URI and `ServiceIntentLauncher` fires it as `ACTION_VIEW`. It deliberately does not call `resolveActivity` first — Android 11 package visibility would make that report "no handler" for schemes that do work — so callers must handle the `false` return and tell the user.

**Pro-gating patterns** (3 variants, choose based on screen architecture):
1. **Direct `if (isPro)`** (11 screens: DNS, HTTP, IpCalc, IpInfo, Ping, PortScan, SpeedTest, TLS, Traceroute, WHOIS, WiFi share button) — read `LocalProStatus.current` in the screen composable, wrap share `IconButton` in `if (isPro) { ... }`.
2. **Nullable lambda** (LanScan, mDNS) — for screens with a separate `Content` composable that receives callbacks: pass `onShareResults = null` when `!isPro`, make the parameter `(() -> Unit)?`, gate with `if (onShareResults != null)`.
3. **Boolean parameter** (WiFi `ChannelGraph`) — when `isPro` gates non-action UI (not a button), pass it as a Boolean param to the inner composable.

### Navigation

Routes are string-based, defined as `ToolDestination` enum entries with `route`, `icon`, `label`, `description`, and `category`. The home screen groups tools by `ToolCategory`. To add a new tool: add a `ToolDestination` entry, create the feature module, add `composable(route) { Screen() }` in `NetLensNavHost`, and add `implementation(project(":feature:<name>"))` in `app/build.gradle.kts`.

### DI

Hilt throughout. `@HiltAndroidApp` on `NetLensApplication`, `@AndroidEntryPoint` on `MainActivity`, `@HiltViewModel` on ViewModels. Feature DI modules use `@InstallIn(ViewModelComponent::class)` or `SingletonComponent`.

## Typography

Inter (Regular, Medium, SemiBold, Bold) for all UI text. JetBrains Mono (Regular, Medium) for technical data (IPs, ports, MACs, TTLs). Defined in `app/.../ui/theme/Type.kt` as `InterFontFamily` and `MonoFontFamily`. Static TTF files in `app/src/main/res/font/`. `labelSmall` uses `MonoFontFamily` and is referenced across 19+ screens.

## Testing

JUnit 5 + Turbine (Flow testing) + kotlinx-coroutines-test. Test sources live in `src/test/` per module. Prefer hand-written fakes over mocking frameworks. Canonical example of the fake-per-engine pattern: `core/scan-testing/.../engine/Fake*.kt` (fakes for `ArpTableReader`, `NetBiosProber`, `SsdpScanner`, `SubnetScanner`, `LanMdnsScanner`, `OuiLookup`) — copy this shape for new engine tests rather than reaching for a mocking library. For HTTP-touching code (`httptester`, `monitor`), use Ktor's `MockEngine`, matching the existing `HttpRequesterImplTest.kt` / `EndpointCheckerImplTest.kt` setup. To assert the SSRF guard, use `SsrfRedirectProbe` from `core:network-testing` rather than hand-rolling the redirect engine — it keeps the redirect on `https`, and getting that wrong is subtle: downgrade the scheme and Ktor refuses because of its own https→http protection, so the test passes whether or not the guard exists.

**Composition smoke tests** (`netlens.android.screenshot` + Paparazzi) render a screen on the JVM and fail if it cannot compose — catching duplicate keys, composition errors and measure/layout crashes. **No golden images**: nothing is recorded, no PNG is committed, `verifyPaparazzi` never runs. Apply the plugin, add a `*ContentRenderTest`, and render each meaningful state. Three things to know: the render exception escapes via the JUnit rule rather than out of `snapshot { }` (so assert nothing — a `try/catch` silently passes); only state-driven composables work, which is why the screen-level `Content` functions are `internal` rather than `private`; and a screen calling `rememberLauncherForActivityResult` needs `LocalActivityResultRegistryOwner` provided (see `PingContentRenderTest`).

**Shared test doubles live in `core:scan-testing` and `core:data-testing`.** Depend on them with `testImplementation(project(":core:scan-testing"))`; they are plain library modules, not `testFixtures` source sets, because AGP registers a `testFixtures` variant but Kotlin 2.1.0 registers no Kotlin compilation for it — you get a `compileDebugTestFixturesJavaWithJavac` task and no Kotlin equivalent, so Kotlin fixtures silently never compile. **Do not copy a fake into your own module.** The three that were copied all drifted weaker than the original: `:feature:devices`' `FakeOuiLookup` matched a whole MAC where the real database matches an OUI prefix, and `:feature:wifiaudit`'s `FakeNetworkEventDao` accepted the type/from/to arguments and ignored them, so any read-path test written against it would have passed regardless of the real `@Query`. A double that is looser than production turns a red test green. Both shared fakes now have their own tests pinning the strong behaviour.

`PortScanner` was the same story a third time: `:feature:lanscan` carried **two** private copies (`StubPortScanner` in `DeviceInventoryTest`, `FakePortScanner` in `LanScanBuildExportTextTest`), both hardcoded to `emptyFlow()` with no way to emit results or raise an error — strictly weaker than `:feature:portscan`'s, which had the usual `results`/`error` seams. All three are gone; the strong one now lives in `core:scan-testing` alongside the other engine fakes. Note the smell that predicts this: a fake defined `private` inside a test file is invisible to everyone else, so the next module writes its own instead of finding it.

**Known gaps** (tracked in `.agent_native/agent_roadmap.md`): every module now has unit tests. `feature:history` was the last holdout and gained 13 (`HistoryViewModelTest`) once `HistoryRepository` was split into an interface plus `HistoryRepositoryImpl` — the ViewModel had depended on the concrete class, which wraps eleven Room DAOs and `withTransaction` and cannot be stood up in a JVM test. If you hit the same wall elsewhere, that split is the move; `@Binds` lives in `core/data/di/RepositoryModule.kt`. `feature:widgetsettings` **also no longer belongs on this list**: `WidgetSettingsViewModelTest` covers it with 3 tests, and the seam this file used to describe as missing now exists — `UserPreferencesRepository` takes an injectable `DataStore<Preferences>` (fake it with `FakeDataStore`), and `Application()` is constructible directly in a JVM test because `:app` sets `unitTests.isReturnDefaultValues = true`. Copy that shape for anything else that looks Context-bound. `feature:celltower`, `feature:wifiaudit`, `core:billing`, and `core:oui` were fixed in the 2026-07-07 pass — `CellTowerReader` and `WifiInfoReader` already had interface seams (mirroring `lanscan`'s `Fake*` pattern), they just lacked fakes/tests; `core:oui`'s parsing logic was extracted into testable companion functions the same way `ArpTableReaderImpl.parseArpTable` is. `history`/`widgetsettings` are harder: `WidgetSettingsViewModel` reads a real `Application` `Context` directly into a DataStore-backed singleton with no seam, and `HistoryViewModel` depends on a **concrete** `HistoryRepository` (not an interface) wrapping 11 Room DAOs plus `NetLensDatabase.withTransaction`. There is no Robolectric anywhere in the repo and no instrumentation (`androidTest`) or screenshot tests at all — code that touches `Context`, `WifiManager`, `TelephonyManager`, or a live `Room`/`DataStore` instance cannot be verified without a physical device, emulator, or Robolectric today. If you're an agent picking up a bug in `history` or `widgetsettings`, flag the verification gap rather than assuming a test can be added the same way as elsewhere.

## Key Dependencies

| Concern | Library |
|---------|---------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt 2.53 |
| Navigation | Navigation Compose 2.8.5 |
| Database | Room 2.6.1 |
| HTTP | Ktor 3.0.3 (CIO engine) |
| DNS | dnsjava 3.6.2 |
| Widget | Glance 1.1.1 |
| Serialization | kotlinx.serialization 1.7.3 |
| Billing | Google Play Billing 6.2.1 (gplay only) |
| Security | AndroidX security-crypto 1.0.0 (gplay only) |
| Typography | Inter, JetBrains Mono (bundled TTF) |

## Strings

User-facing strings are extracted to `res/values/strings.xml` in each feature module. Use string resources, not hardcoded text in composables.

## Skill routing for this repo

Project-scoped skills live under `.claude/skills/`. When invoking Claude Code in this repo, prefer:

- "release", "ship release", "tag and release", "cut v…" → `/android-release` (the skill at `.claude/skills/android-release/SKILL.md`). Refuses to tag if the CHANGELOG entry, F-Droid changelog file, signed local build, cert continuity, or tag-not-already-existing checks fail. Encodes the lessons from the v1.1.0 → v1.1.1 incident.
- "review my changes", "code review" → `/review` or `/code-review`
- "security audit" → `/cso`
- "investigate bug", "why is X broken" → `/investigate`

## Release signing

- Signing resolves **per field, not all-or-nothing** (`app/build.gradle.kts:25-42`): each of `release.storeFile` / `release.storePassword` / `release.keyAlias` / `release.keyPassword` is read from `local.properties` first, falling through to `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` when that key is absent **or blank**. A half-filled `local.properties` therefore takes some values from one source and some from the other, silently.
- **Don't assume which source is in play.** `local.properties` is git-ignored and machine-local, so it differs per checkout — this file previously asserted it "contains only `sdk.dir`", which was untrue on the primary dev machine (all four `release.*` keys are set there) and sent an agent down the wrong path. Check, don't assume.
- A signed local build is the pre-flight for any release. If `assembleRelease` produces `*-unsigned.apk`, signing resolved to nothing — fix the wiring, do not push.
- **Cert continuity baseline** — release APKs must be signed by `CN=Ventoux Advisory Co, O=Ventoux Advisory Co, C=US`, cert SHA-256 `8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4` (v2 scheme; v1 and v3 are off). Verify with `apksigner verify --print-certs --verbose <apk>`. A mismatch means users cannot upgrade in place and F-Droid/Play will reject the build. Note the `signatures:[…]` value in `adb shell dumpsys package` is a Java object hashCode, **not** a cert digest — it cannot be used for this check.
- The release CI workflow at `.github/workflows/release.yml` decodes the keystore from `RELEASE_KEYSTORE_BASE64` (GitHub secret) and signs at the `assembleRelease`/`bundleRelease` step. Tag-vs-`gradle.properties` mismatch fails the workflow at the version-verification step.

## Versioning

`gradle.properties` is the source of truth for `netlens.versionName` and `netlens.versionCode`. The tag (`v<versionName>`) and CHANGELOG header (`## [<versionName>] - <date>`) must match. The F-Droid changelog file at `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` is required for F-Droid metadata to pick up the release; missing this file means F-Droid silently ships the new code without a release note.
