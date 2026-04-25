# PR Review: #6 — test: add ViewModel unit tests for remaining feature modules

**Reviewed**: 2026-04-23
**Author**: bearyjd
**Branch**: feat/test-coverage → master
**Decision**: REQUEST CHANGES

## Summary

Solid test suite covering 12 feature module ViewModels with consistent patterns (JUnit 5 + Turbine + hand-written fakes). Three HIGH issues around non-deterministic assertions and missing Loading state coverage should be fixed before merge.

## Findings

### CRITICAL
None

### HIGH

1. **`WhoisViewModelTest` uses `Thread.sleep()` + accepts Loading as passing** — `feature/whois/src/test/kotlin/.../WhoisViewModelTest.kt`
   - `Thread.sleep(100)` inside `runTest` blocks the real thread without advancing virtual time
   - `assertTrue(finalState is WhoisUiState.Success || finalState is WhoisUiState.Loading)` makes two tests non-asserting
   - Root cause: `WhoisViewModel.resolveAndReverseDns` uses `withContext(Dispatchers.IO)` with real `InetAddress.getByName`
   - Fix: inject `Dispatchers.IO` as a constructor parameter so tests can substitute `UnconfinedTestDispatcher()`

2. **`IpInfoViewModelTest` skips Loading intermediate state** — `feature/ipinfo/src/test/kotlin/.../IpInfoViewModelTest.kt`
   - `refresh()` sets `Loading` before the repository call, but tests jump straight to `Success`
   - Works under `UnconfinedTestDispatcher` because the entire coroutine completes before `test {}` is entered
   - Would break under `StandardTestDispatcher`; fragile pattern

3. **`TlsViewModelTest` skips Loading intermediate state** — `feature/tls/src/test/kotlin/.../TlsViewModelTest.kt`
   - `inspect()` sets `Loading` synchronously but no test covers this transition
   - Inconsistent with `HttpTesterViewModelTest` which correctly tests all three state transitions (Idle → Loading → Success)

### MEDIUM

4. **`FakeMdnsScanner` shared channel causes fan-out non-determinism** — `feature/mdns/src/test/kotlin/.../engine/FakeMdnsScanner.kt`
   - Single `Channel` shared across 8 `discoverServices` collectors; delivery order is non-deterministic under real dispatchers
   - Fix: return a separate channel per invocation, or use `MutableSharedFlow(replay = ...)`

5. **`NetLogViewModelTest.clearError` is a vacuous test** — `feature/netlog/src/test/kotlin/.../NetLogViewModelTest.kt`
   - Calls `clearError()` when error is already `null`; assertion is trivially true
   - Should trigger an error state first, then clear it

6. **`WolViewModelTest.sendWol` does not consume the delay-triggered auto-clear emission** — `feature/wol/src/test/kotlin/.../WolViewModelTest.kt`
   - `delay(3_000L)` auto-clears `lastSentStatus`; the test block exits without consuming this emission
   - Should call `cancelAndConsumeRemainingEvents()` or explicitly assert the auto-clear

7. **`MonitorViewModelTest.removeEndpoint` reads `viewModel.state.value` after `cancelAndConsumeRemainingEvents()`** — `feature/monitor/src/test/kotlin/.../MonitorViewModelTest.kt`
   - Fragile under `StandardTestDispatcher` where coroutines may still be in-flight

8. **`NetLogViewModelTest.startMonitoring` sends channel event before subscribing** — `feature/netlog/src/test/kotlin/.../NetLogViewModelTest.kt`
   - Correct under `UnconfinedTestDispatcher` but intent would be clearer with send inside `test {}` block

### LOW

9. **Turbine added to all library modules unconditionally** — `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt:40`
   - Consistent with existing `kotlinx-coroutines-test` pattern but adds unnecessary resolution overhead to core modules

10. **`@OptIn(ExperimentalCoroutinesApi::class)` at class level** — all 12 test files
    - Only `setMain`/`resetMain`/`UnconfinedTestDispatcher` require it; class-level is accepted practice

## Validation Results

| Check | Result |
|---|---|
| Type check | Pass (compiled successfully) |
| Lint | Skipped |
| Tests | Pass (`./gradlew testDebugUnitTest` — BUILD SUCCESSFUL) |
| Build | Pass |

## Files Reviewed

All 29 files in the PR diff (1 Modified, 28 Added)
