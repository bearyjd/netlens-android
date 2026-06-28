# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NetLens is an Android network diagnostics toolkit (package `com.ventouxlabs.netlens`). It provides 20 network tools — ping, traceroute, DNS lookup, LAN scan, port scan, WHOIS, TLS inspector, HTTP tester, mDNS browser, WiFi analyzer, Wake-on-LAN, IP info, IP/subnet calculator, endpoint monitor, network log, speed test, security posture, cell tower, Wi-Fi audit, and DNS leak test — each in its own feature module.

## Build Commands

```bash
./gradlew assembleFossDebug                    # Build FOSS debug APK (no billing)
./gradlew assembleGplayDebug                   # Build Google Play debug APK (with billing)
./gradlew :feature:ping:testFossDebugUnitTest  # Run tests for one module
./gradlew testFossDebugUnitTest                # Run all unit tests
```

Two product flavors: `foss` (F-Droid / source builds, Pro always on) and `gplay` (Google Play, Pro via in-app purchase).

CI builds `foss` flavor and currently tests only: `:core:network`, `:feature:lanscan`, `:feature:whois`, `:feature:monitor`.

**SDK targets**: compileSdk 35, minSdk 29, Java 17.

## Architecture

### Module Graph

```
app ──┬── feature:* (22 modules)  ── core:network
      ├── core:network                core:data
      ├── core:data                   core:billing
      ├── core:billing                core:oui
      ├── core:oui
      └── widget
```

- **`app`** — single Activity (`MainActivity`), hosts `NetLensNavHost` which routes to all feature screens. Navigation uses string routes defined in the `ToolDestination` enum (`app/.../navigation/ToolDestination.kt`).
- **`core:network`** — connectivity monitoring (`NetworkMonitor`), SSRF guard, coroutine utilities, and result export (`export/ResultExporter`). No HTTP client library (features use Ktor or raw sockets directly).
- **`core:data`** — Room database (`NetLensDatabase`) with DAOs for endpoints, network events, WoL targets. Provides Hilt `DataModule`.
- **`core:billing`** — `ProStatus` interface (`isPro: StateFlow<Boolean>`, `launchPurchase(activity)`) and `LocalProStatus` CompositionLocal (safe no-op default). Flavor-specific implementations: `app/src/foss/` has `FossProStatus` (always Pro), `app/src/gplay/` has `GplayProStatus` (Google Play Billing with `BillingClientWrapper` for testability, `EncryptedSharedPreferences` for purchase state, reconnect counter with max 3 attempts).
- **`core:oui`** — MAC address vendor lookup from OUI database.
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

**Result export pattern**: All 13 tool ViewModels (Ping, Traceroute, DNS, PortScan, WHOIS, HttpTester, LanScan, TLS, IpInfo, IpCalc, mDNS, SpeedTest, WiFi) expose `fun buildExportText(): String` which serialises current UI state to a plain-text string. Screens call `ResultExporter.shareAsText()` or `ResultExporter.copyToClipboard()` (both in `core:network/export/ResultExporter.kt`) from Share/Copy IconButtons in each screen's `TopAppBar`. Modules that did not previously depend on `core:network` or `compose.material.icons` had those dependencies added as part of this feature (ipcalc: both; whois, httptester, tls, mdns: `compose.material.icons`).

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

JUnit 5 + Turbine (Flow testing) + kotlinx-coroutines-test. Test sources live in `src/test/` per module. Prefer hand-written fakes over mocking frameworks.

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

- `local.properties` contains only `sdk.dir`. Release signing falls through **per field** to `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` env vars when a `release.*` key is absent or blank in `local.properties`. See `app/build.gradle.kts:25-42`.
- A signed local build is the pre-flight for any release. If `assembleRelease` produces `*-unsigned.apk`, env wiring is wrong — fix that, do not push.
- The release CI workflow at `.github/workflows/release.yml` decodes the keystore from `RELEASE_KEYSTORE_BASE64` (GitHub secret) and signs at the `assembleRelease`/`bundleRelease` step. Tag-vs-`gradle.properties` mismatch fails the workflow at the version-verification step.

## Versioning

`gradle.properties` is the source of truth for `netlens.versionName` and `netlens.versionCode`. The tag (`v<versionName>`) and CHANGELOG header (`## [<versionName>] - <date>`) must match. The F-Droid changelog file at `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` is required for F-Droid metadata to pick up the release; missing this file means F-Droid silently ships the new code without a release note.
