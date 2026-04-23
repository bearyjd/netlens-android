# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NetLens is an Android network diagnostics toolkit (package `us.beary.netlens`). It provides 13 network tools — ping, traceroute, DNS lookup, LAN scan, port scan, WHOIS, TLS inspector, HTTP tester, mDNS browser, Wake-on-LAN, IP info, endpoint monitor, and network log — each in its own feature module.

## Build Commands

```bash
./gradlew assembleDebug                    # Build debug APK
./gradlew :feature:ping:testDebugUnitTest  # Run tests for one module
./gradlew testDebugUnitTest                # Run all unit tests
```

CI currently tests only: `:core:network`, `:feature:lanscan`, `:feature:whois`, `:feature:monitor`.

**SDK targets**: compileSdk 35, minSdk 29, Java 17.

## Architecture

### Module Graph

```
app ──┬── feature:* (13 modules)  ── core:network
      ├── core:network                core:data
      ├── core:data                   core:oui
      ├── core:oui
      └── widget
```

- **`app`** — single Activity (`MainActivity`), hosts `NetLensNavHost` which routes to all feature screens. Navigation uses string routes defined in the `ToolDestination` enum (`app/.../navigation/ToolDestination.kt`).
- **`core:network`** — connectivity monitoring (`NetworkMonitor`), SSRF guard, coroutine utilities. No HTTP client library (features use Ktor or raw sockets directly).
- **`core:data`** — Room database (`NetLensDatabase`) with DAOs for endpoints, network events, WoL targets. Provides Hilt `DataModule`.
- **`core:oui`** — MAC address vendor lookup from OUI database.
- **`widget`** — Glance-based home screen widget.
- **`feature:*`** — each feature is self-contained with its own screen, ViewModel, DI module, and engine/domain layer.

### Convention Plugins (`build-logic/`)

Feature modules apply `netlens.android.feature` which bundles: `netlens.android.library` + `netlens.android.compose` + `netlens.hilt` + lifecycle/navigation dependencies. Core modules apply `netlens.android.library` + `netlens.hilt` individually.

### Feature Module Pattern

Each feature follows this structure:
```
feature/<name>/src/main/kotlin/us/beary/netlens/feature/<name>/
├── <Name>Screen.kt          # Composable UI
├── <Name>ViewModel.kt       # @HiltViewModel with MutableStateFlow
├── di/<Name>Module.kt       # @Module/@InstallIn Hilt bindings
├── engine/                   # Domain logic (parser, client, etc.)
└── model/                    # UiState data class, domain models
```

**UI state pattern**: `MutableStateFlow<UiState>` exposed as `StateFlow`, updated via `.update { it.copy(...) }`. No MVI event sealed class — ViewModels expose individual action methods.

### Navigation

Routes are string-based, defined as `ToolDestination` enum entries with `route`, `icon`, `label`, `description`, and `category`. The home screen groups tools by `ToolCategory`. To add a new tool: add a `ToolDestination` entry, create the feature module, add `composable(route) { Screen() }` in `NetLensNavHost`, and add `implementation(project(":feature:<name>"))` in `app/build.gradle.kts`.

### DI

Hilt throughout. `@HiltAndroidApp` on `NetLensApplication`, `@AndroidEntryPoint` on `MainActivity`, `@HiltViewModel` on ViewModels. Feature DI modules use `@InstallIn(ViewModelComponent::class)` or `SingletonComponent`.

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

## Strings

User-facing strings are extracted to `res/values/strings.xml` in each feature module. Use string resources, not hardcoded text in composables.
