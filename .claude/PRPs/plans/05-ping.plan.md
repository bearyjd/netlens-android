# Plan: Ping

## Summary

Implement a ping tool that executes ICMP ping via the system `ping` binary, parses per-line RTT results, and displays a live line chart of round-trip times alongside statistics.

## User Story

As a user, I want to ping a host and see real-time round-trip times on a chart, so that I can diagnose latency and packet loss.

## Metadata

- **Complexity**: Small
- **Branch**: feat/ping
- **PR**: PR-05
- **Depends On**: scaffold
- **Estimated Files**: 8
- **New Modules**: none (feature/ping already exists)

## Patterns to Mirror

### FEATURE_MODULE
// SOURCE: feature/ping/build.gradle.kts — depends on :core:network

### PROCESS_BUILDER
// Uses ProcessBuilder for system command execution — similar pattern to running shell commands

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `feature/ping/src/main/kotlin/us/beary/netlens/feature/ping/model/PingResult.kt` | CREATE | data class: sequenceNumber, host, ttl, timeMs, isTimeout |
| `feature/ping/src/main/kotlin/us/beary/netlens/feature/ping/model/PingSummary.kt` | CREATE | data class: transmitted, received, lossPercent, minMs, avgMs, maxMs |
| `feature/ping/src/main/kotlin/us/beary/netlens/feature/ping/model/PingUiState.kt` | CREATE | data class: host, results list, summary, isPinging, error |
| `feature/ping/src/main/kotlin/us/beary/netlens/feature/ping/engine/PingEngine.kt` | CREATE | Interface: fun ping(host: String, count: Int = 4): Flow<PingResult> |
| `feature/ping/src/main/kotlin/us/beary/netlens/feature/ping/engine/PingEngineImpl.kt` | CREATE | ProcessBuilder("ping", "-c", count.toString(), host). Read stdout line-by-line. Parse "icmp_seq=N ttl=T time=X.XX ms" with regex. Emit PingResult per line. Parse summary line for stats. |
| `feature/ping/src/main/kotlin/us/beary/netlens/feature/ping/di/PingModule.kt` | CREATE | @Module @Binds PingEngine |
| `feature/ping/src/main/kotlin/us/beary/netlens/feature/ping/PingViewModel.kt` | CREATE | @HiltViewModel, startPing(host, count), cancel, accumulate results |
| `feature/ping/src/main/kotlin/us/beary/netlens/feature/ping/PingScreen.kt` | CREATE | Host TextField + count selector + Start button, Canvas line chart for RTT values, stats row (min/avg/max/loss), LazyColumn of per-ping results |
| `app/src/main/kotlin/us/beary/netlens/navigation/NetLensNavHost.kt` | UPDATE | Replace PlaceholderScreen for Ping route |

## Step-by-Step Tasks

### Task 1: Create PingResult and PingSummary models
- **ACTION**: `data class PingResult(val sequenceNumber: Int, val host: String, val ttl: Int, val timeMs: Float, val isTimeout: Boolean)`. `data class PingSummary(val transmitted: Int, val received: Int, val lossPercent: Float, val minMs: Float, val avgMs: Float, val maxMs: Float)`.
- **VALIDATE**: Compiles

### Task 2: Create PingEngine
- **ACTION**: Interface + impl. Impl: `channelFlow` on `Dispatchers.IO`. `ProcessBuilder("ping", "-c", count.toString(), host).redirectErrorStream(true).start()`. Read `process.inputStream` line by line with `BufferedReader`. Regex `icmp_seq=(\d+).*ttl=(\d+).*time=([\d.]+)`. On timeout lines, emit `PingResult(isTimeout=true)`. On `awaitClose`, `process.destroy()`.
- **VALIDATE**: Unit test with process output samples

### Task 3: Create DI module
- **ACTION**: Standard `@Binds` for PingEngine
- **VALIDATE**: Compiles

### Task 4: Create PingViewModel
- **ACTION**: `@HiltViewModel`, `fun startPing(host: String, count: Int = 4)`. Collects flow, accumulates results, computes running summary. `cancelPing()` cancels job.
- **VALIDATE**: Unit test with Turbine and fake PingEngine

### Task 5: Create PingScreen
- **ACTION**: OutlinedTextField for host. Dropdown/chips for count (4, 10, 20, continuous). Start/Stop button. Canvas line chart: X=sequence, Y=RTT ms, draw Path through points. Stats row: min/avg/max/loss%. LazyColumn of individual results.
- **VALIDATE**: Preview renders

### Task 6: Wire navigation
- **ACTION**: Update NetLensNavHost for Ping route
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - `PingEngineImpl` — parse sample ping output lines (Linux format)
  - `PingViewModel` — Turbine: start → results accumulate → summary updates
- **Integration tests for**:
  - Ping localhost (device/emulator test)

## Validation
