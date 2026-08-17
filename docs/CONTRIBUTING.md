# Contributing to NetLens

<!-- AUTO-GENERATED from gradle.properties, build-logic/, and CLAUDE.md — regenerate with /update-docs. Hand-edit only the sections marked MANUAL. -->

## Prerequisites

- JDK 17
- Android SDK with compileSdk 35 (minSdk 29)
- Android Studio Ladybug or later (recommended)

No `local.properties` setup is needed beyond `sdk.dir` — release signing is only required for release builds (see [RELEASING.md](RELEASING.md)).

## Build & Test Commands

| Command | Purpose |
|---------|---------|
| `./gradlew assembleFossDebug` | FOSS debug APK (no billing, Pro always on) |
| `./gradlew assembleGplayDebug` | Google Play debug APK (with billing) |
| `./gradlew testFossDebugUnitTest testGplayDebugUnitTest testDebugUnitTest` | All unit tests (what CI runs) |
| `./gradlew :feature:ping:testDebugUnitTest` | Tests for one feature/core module (unflavored) |
| `./gradlew :app:testFossDebugUnitTest` | Tests for `:app` (flavored — only `:app` has flavors) |

**All three test tasks, every time.** They cover disjoint source sets and dropping one silently skips tests rather than failing: `testFossDebugUnitTest` reaches `src/test` + `src/testFoss`, `testDebugUnitTest` reaches the unflavored library modules, and `testGplayDebugUnitTest` is the *only* one that reaches `src/testGplay` — where the billing tests live (`ITEM_ALREADY_OWNED`, purchase acknowledgement). Those were once written but never executed. `.github/workflows/ci.yml:36-41` carries the same warning; a two-task run looks green while proving nothing about gplay.

Test failures print the full exception and stack trace (`testLogging` in `AndroidLibraryConventionPlugin` and `app/build.gradle.kts`). If you only see a test name, you are on an older revision — nothing else in the build prints the cause, and reconstructing it means hand-parsing `build/test-results/**/TEST-*.xml`.

## Project Layout

Multi-module Gradle project: `app` + 24 `feature:*` modules + 9 `core:*` modules + `widget` + `baselineprofile`, with convention plugins in `build-logic/`. The core modules are `network`, `data`, `billing`, `ui`, `oui`, `scan`, plus three test-only modules (`scan-testing`, `data-testing`, `network-testing`) that are consumed via `testImplementation` and never appear on a runtime classpath. **Feature modules must not depend on each other** — anything two features both need belongs in `core:*`. Each feature module follows `Screen.kt` + `ViewModel.kt` (StateFlow UiState) + `di/Module.kt` + `engine/` + `model/`. See `docs/CODEMAPS/` for the full architecture maps and `CLAUDE.md` for pattern details (pro-gating variants, result export, navigation).

## Testing Conventions

- JUnit 5 + Turbine + kotlinx-coroutines-test; test sources in `src/test/` per module.
- Prefer hand-written fakes over mocking frameworks.
- **Do not copy a fake into your own module.** Shared doubles live in `core:scan-testing` and `core:data-testing`; depend on them with `testImplementation(project(":core:scan-testing"))`. Every copy made so far drifted *weaker* than the original — `FakeOuiLookup` matched a whole MAC where the real database matches an OUI prefix, `FakeNetworkEventDao` accepted its filter arguments and ignored them, and two private `PortScanner` copies hardcoded `emptyFlow()` so they could not emit a result or raise an error. A double looser than production turns a red test green. The shared fakes have their own tests pinning that behaviour (`FakeOuiLookupTest`, `FakePortScannerTest`) — add one if you add a fake.
- HTTP-touching code uses Ktor `MockEngine` (see `HttpRequesterImplTest.kt`). To assert the SSRF guard use `SsrfRedirectProbe` from `core:network-testing` rather than hand-rolling a redirect engine — get the scheme wrong and Ktor's own https→http protection makes the test pass whether or not the guard exists.
- Composition smoke tests (Paparazzi, via `netlens.android.screenshot`) render a screen on the JVM and fail if it cannot compose — catching duplicate `LazyColumn` keys and measure/layout crashes. No golden images are recorded or committed. Only state-driven composables work, which is why screen-level `Content` functions are `internal` rather than `private`.
- CI picks up tests in any module with a `src/test` tree automatically — no workflow edits needed.
- Robolectric is **opt-in and deliberately scoped**: the `netlens.android.robolectric` convention plugin is applied only to `core:data` (real Room SQL + migration tests) and `widget` (framework-bound lifecycle tests). Everywhere else, code touching `Context`/system services gets an interface seam instead — extract the pure logic and test that hard before reaching for Robolectric. Known shadow limits (what Robolectric *cannot* test here) are recorded in `.agent_native/agent_roadmap.md`.
- No instrumentation (`androidTest`) tests exist; anything needing a real device (live `WifiInfo` via `transportInfo`, `SignalStrength` dbm extraction) is flagged in the roadmap rather than faked.

## Code Style

- User-facing strings go in each module's `res/values/strings.xml`, never hardcoded in composables.
- UI state updates via `MutableStateFlow.update { it.copy(...) }` — no mutation.
- Typography: Inter for UI text, JetBrains Mono for technical data (IPs, ports, MACs).

## Submitting Changes

<!-- MANUAL -->
1. Branch from `master`, keep commits atomic with conventional-commit messages (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`).
2. Run the full test task above and make sure `assembleFossDebug` builds.
3. Update `CHANGELOG.md` under an Unreleased/next-version heading for user-visible changes.
4. Open a PR against `master`; CI must be green before review.
