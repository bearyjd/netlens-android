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
| `./gradlew testFossDebugUnitTest testDebugUnitTest` | All unit tests (what CI runs) |
| `./gradlew :feature:ping:testDebugUnitTest` | Tests for one feature/core module (unflavored) |
| `./gradlew :app:testFossDebugUnitTest` | Tests for `:app` (flavored — only `:app` has flavors) |

## Project Layout

Multi-module Gradle project: `app` + 23 `feature:*` modules + `core:{network,data,billing,ui,oui}` + `widget`, with convention plugins in `build-logic/`. Each feature module follows `Screen.kt` + `ViewModel.kt` (StateFlow UiState) + `di/Module.kt` + `engine/` + `model/`. See `docs/CODEMAPS/` for the full architecture maps and `CLAUDE.md` for pattern details (pro-gating variants, result export, navigation).

## Testing Conventions

- JUnit 5 + Turbine + kotlinx-coroutines-test; test sources in `src/test/` per module.
- Prefer hand-written fakes over mocking frameworks — copy the `Fake*` pattern from `feature/lanscan/src/test/.../engine/`.
- HTTP-touching code uses Ktor `MockEngine` (see `HttpRequesterImplTest.kt`).
- CI picks up tests in any module with a `src/test` tree automatically — no workflow edits needed.
- No Robolectric or instrumentation tests exist; code touching `Context`/system services needs an interface seam to be testable (see `.agent_native/agent_roadmap.md` for known gaps).

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
