# Plan: Port Scanner

## Summary

Implement a port scanner that checks TCP connectivity against the top 1000 common ports (loaded from a bundled CSV asset), displaying open ports with service names and response latency in a live-updating list.

## User Story

As a user, I want to scan a host for open TCP ports so that I can identify running services and troubleshoot connectivity issues.

## Metadata

- **Complexity**: Medium
- **Branch**: feat/port-scanner
- **PR**: PR-03
- **Depends On**: scaffold
- **Estimated Files**: 10
- **New Modules**: none (feature/portscan already exists)

## Patterns to Mirror

### FEATURE_MODULE
// SOURCE: feature/portscan/build.gradle.kts — depends on :core:network

### ASSET_LOADING
// SOURCE: core/oui/src/main/kotlin/com.ventouxlabs.netlens/core/oui/OuiLookup.kt
- context.assets.open() pattern for loading CSV from assets

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `feature/portscan/src/main/assets/top-1000-ports.csv` | CREATE | CSV: port,service_name (e.g., 22,SSH / 80,HTTP / 443,HTTPS) |
| `feature/portscan/src/main/kotlin/com.ventouxlabs.netlens/feature/portscan/model/PortInfo.kt` | CREATE | data class PortInfo(val port: Int, val serviceName: String) |
| `feature/portscan/src/main/kotlin/com.ventouxlabs.netlens/feature/portscan/model/PortResult.kt` | CREATE | data class PortResult(val port: Int, val serviceName: String, val isOpen: Boolean, val latencyMs: Long) |
| `feature/portscan/src/main/kotlin/com.ventouxlabs.netlens/feature/portscan/model/PortScanUiState.kt` | CREATE | data class: host, results list, isScanning, progress, scannedCount, totalCount, error |
| `feature/portscan/src/main/kotlin/com.ventouxlabs.netlens/feature/portscan/engine/PortScanner.kt` | CREATE | Interface: fun scan(host: String, ports: List<PortInfo>): Flow<PortResult> |
| `feature/portscan/src/main/kotlin/com.ventouxlabs.netlens/feature/portscan/engine/PortScannerImpl.kt` | CREATE | Semaphore(100), Socket().connect(InetSocketAddress(host, port), 1000), measure latency with System.nanoTime, emit PortResult per port |
| `feature/portscan/src/main/kotlin/com.ventouxlabs.netlens/feature/portscan/data/PortListLoader.kt` | CREATE | @Singleton, loads top-1000-ports.csv from assets, caches List<PortInfo> |
| `feature/portscan/src/main/kotlin/com.ventouxlabs.netlens/feature/portscan/di/PortScanModule.kt` | CREATE | @Module @Binds PortScanner |
| `feature/portscan/src/main/kotlin/com.ventouxlabs.netlens/feature/portscan/PortScanViewModel.kt` | CREATE | @HiltViewModel, startScan(host), cancel, accumulate results sorted by port |
| `feature/portscan/src/main/kotlin/com.ventouxlabs.netlens/feature/portscan/PortScanScreen.kt` | CREATE | Host TextField + Scan button, LazyColumn of port results (green=open, gray=closed), progress bar, filter toggle (show all / open only) |
| `app/src/main/kotlin/com.ventouxlabs.netlens/navigation/NetLensNavHost.kt` | UPDATE | Replace PlaceholderScreen for PortScan route with PortScanScreen() |

## Step-by-Step Tasks

### Task 1: Create top-1000-ports.csv
- **ACTION**: Create asset file with header `port,service` and entries like `21,FTP`, `22,SSH`, `23,Telnet`, `25,SMTP`, `53,DNS`, `80,HTTP`, `110,POP3`, `143,IMAP`, `443,HTTPS`, `993,IMAPS`, `995,POP3S`, `3306,MySQL`, `5432,PostgreSQL`, `8080,HTTP-Alt`, etc. Full 1000 ports from IANA common list.
- **VALIDATE**: File exists in assets

### Task 2: Create PortInfo and PortResult models
- **ACTION**: Create data classes as specified
- **VALIDATE**: Compiles

### Task 3: Create PortListLoader
- **ACTION**: `@Singleton class PortListLoader @Inject constructor(@ApplicationContext context: Context)`. Lazy-load CSV, parse into `List<PortInfo>`, cache in memory. Follow OuiLookup pattern with Mutex for thread-safe init.
- **VALIDATE**: Unit test

### Task 4: Create PortScanner engine
- **ACTION**: Interface + impl. Impl uses `channelFlow` with `Semaphore(100)`. For each port: launch async, acquire semaphore, create `Socket()`, measure time, `socket.connect(InetSocketAddress(host, port), 1000)`. Emit `PortResult(port, serviceName, isOpen=true, latencyMs)` on success, `PortResult(..., isOpen=false, latencyMs=-1)` on IOException. Close socket in finally.
- **VALIDATE**: Unit test with localhost server

### Task 5: Create DI module
- **ACTION**: `PortScanModule` with `@Binds` for PortScanner
- **VALIDATE**: Compiles

### Task 6: Create PortScanViewModel
- **ACTION**: `@HiltViewModel`, injects PortScanner + PortListLoader. `startScan(host: String)` loads port list, collects scan flow, accumulates results into state. Tracks scannedCount for progress. `cancelScan()` cancels job.
- **VALIDATE**: Unit test with Turbine

### Task 7: Create PortScanScreen
- **ACTION**: OutlinedTextField for host, "Scan" button, filter chip "Open only". LazyColumn with PortResult items: port number, service name, open/closed icon, latency. LinearProgressIndicator with "N/1000 scanned". TopAppBar "Port Scanner".
- **VALIDATE**: Preview renders

### Task 8: Wire navigation
- **ACTION**: Update NetLensNavHost for PortScan route
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - `PortListLoader` — CSV parsing, cache behavior
  - `PortScannerImpl` — mock socket connections
  - `PortScanViewModel` — Turbine: scan lifecycle, accumulation, cancel
- **Integration tests for**:
  - Full scan against localhost with a known open port

## Validation
