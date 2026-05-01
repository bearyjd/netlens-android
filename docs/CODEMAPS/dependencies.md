<!-- Generated: 2026-05-01 | Files scanned: 411 | Token estimate: ~600 -->

# Dependencies

## Key Libraries

| Concern | Library | Version |
|---------|---------|---------|
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| DI | Hilt | 2.53 |
| Navigation | Navigation Compose | 2.8.5 |
| Database | Room | 2.6.1 |
| HTTP | Ktor (CIO engine) | 3.0.3 |
| DNS | dnsjava | 3.6.2 |
| Widget | Glance | 1.1.1 |
| Serialization | kotlinx.serialization | 1.7.3 |
| Coroutines | kotlinx.coroutines | 1.9.0 |
| Lifecycle | lifecycle-runtime/viewmodel | 2.8.7 |
| DataStore | datastore-preferences | 1.1.1 |
| WorkManager | work-runtime-ktx | 2.10.0 |
| Billing | billing-ktx (gplay only) | 6.2.1 |

## Build Toolchain

| Tool | Version |
|------|---------|
| AGP | 8.9.0 |
| Kotlin | 2.1.0 |
| KSP | 2.1.0-1.0.29 |
| Gradle | 8.14 |

## Test Dependencies

| Library | Purpose |
|---------|---------|
| JUnit 5 (5.11.4) | Test framework |
| Turbine (1.2.0) | Flow/StateFlow testing |
| kotlinx-coroutines-test | TestDispatcher, runTest |
| Ktor MockEngine | HTTP client mocking |

## Convention Plugins (build-logic/)

| ID | What it configures |
|----|-------------------|
| `netlens.android.application` | AGP app, Kotlin, compileSdk 35, minSdk 29, Java 17 |
| `netlens.android.library` | AGP library, same SDK/Java targets |
| `netlens.android.compose` | Compose compiler plugin + BOM + Material3 + icons + tooling |
| `netlens.hilt` | Hilt plugin + KSP + hilt-android + hilt-compiler |
| `netlens.android.feature` | library + compose + hilt + lifecycle + nav + core:billing |

## CI

GitHub Actions: `.github/workflows/ci.yml`
- Builds `foss` flavor (`assembleFossDebug`)
- Tests: `:core:network`, `:feature:lanscan`, `:feature:whois`, `:feature:monitor`
