# Plan: mDNS / Bonjour Browser

## Summary

Implement an mDNS service browser using Android's NsdManager to discover local network services, displaying discovered services with their type, host, and port in a live-updating list.

## User Story

As a user, I want to browse mDNS/Bonjour services on my local network, so that I can discover printers, media servers, IoT devices, and other advertised services.

## Metadata

- **Complexity**: Medium
- **Branch**: feat/mdns-browser
- **PR**: PR-10
- **Depends On**: scaffold
- **Estimated Files**: 9
- **New Modules**: feature/mdns (new)

## Patterns to Mirror

### CALLBACKFLOW
// SOURCE: core/network/src/main/kotlin/com.ventouxlabs.netlens/core/network/ConnectivityManagerNetworkMonitor.kt
- callbackFlow wrapping system callbacks with awaitClose for cleanup

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `feature/mdns/build.gradle.kts` | CREATE | netlens.android.feature, namespace com.ventouxlabs.netlens.feature.mdns, dep :core:network |
| `settings.gradle.kts` | UPDATE | Add `include(":feature:mdns")` |
| `app/build.gradle.kts` | UPDATE | Add implementation project dep |
| `feature/mdns/src/main/kotlin/com.ventouxlabs.netlens/feature/mdns/model/MdnsService.kt` | CREATE | data class: name, type, host, port, txtRecords (Map<String,String>) |
| `feature/mdns/src/main/kotlin/com.ventouxlabs.netlens/feature/mdns/model/MdnsUiState.kt` | CREATE | data class: services list, isDiscovering, error |
| `feature/mdns/src/main/kotlin/com.ventouxlabs.netlens/feature/mdns/engine/MdnsScanner.kt` | CREATE | Interface: fun discover(): Flow<List<MdnsService>> |
| `feature/mdns/src/main/kotlin/com.ventouxlabs.netlens/feature/mdns/engine/MdnsScannerImpl.kt` | CREATE | Uses NsdManager. First discovers "_services._dns-sd._udp" to get service types, then discovers each type. Wraps DiscoveryListener in callbackFlow. Resolves each found service for host/port/txt. |
| `feature/mdns/src/main/kotlin/com.ventouxlabs.netlens/feature/mdns/di/MdnsModule.kt` | CREATE | @Module @Binds MdnsScanner |
| `feature/mdns/src/main/kotlin/com.ventouxlabs.netlens/feature/mdns/MdnsViewModel.kt` | CREATE | @HiltViewModel, startDiscovery(), stopDiscovery(), accumulate services |
| `feature/mdns/src/main/kotlin/com.ventouxlabs.netlens/feature/mdns/MdnsScreen.kt` | CREATE | LazyColumn of service cards (name, type, host:port), FAB to start/stop discovery, progress indicator while discovering |
| `app/src/main/kotlin/com.ventouxlabs.netlens/navigation/NetLensNavHost.kt` | UPDATE | Add composable route for mdns |

## Step-by-Step Tasks

### Task 1: Create feature module
- **ACTION**: Create build.gradle.kts, update settings + app build
- **VALIDATE**: `./gradlew :feature:mdns:compileDebugKotlin`

### Task 2: Create MdnsService model
- **ACTION**: `data class MdnsService(val name: String, val type: String, val host: String, val port: Int, val txtRecords: Map<String, String>)`
- **VALIDATE**: Compiles

### Task 3: Create MdnsScanner
- **ACTION**: Interface + impl. Inject `@ApplicationContext context`. Get `NsdManager` via `context.getSystemService()`. Use `callbackFlow`. Register `DiscoveryListener` for `"_services._dns-sd._udp"` with `NSD_PROTOCOL_DNS_SD`. On `onServiceFound`, resolve the service using `NsdManager.resolveService()` with `ResolveListener`. Build MdnsService from resolved `NsdServiceInfo` (name, serviceType, host.hostAddress, port, attributes map). Accumulate into list, emit on each new service. `awaitClose { nsdManager.stopServiceDiscovery(listener) }`.
- **VALIDATE**: Compiles (requires device for full test)

### Task 4: Create DI module
- **ACTION**: `MdnsModule` with `@Binds` for MdnsScanner
- **VALIDATE**: Compiles

### Task 5: Create MdnsViewModel
- **ACTION**: `@HiltViewModel`, `startDiscovery()` collects from scanner flow, `stopDiscovery()` cancels collection job. State tracks isDiscovering + service list.
- **VALIDATE**: Unit test with Turbine and fake scanner

### Task 6: Create MdnsScreen
- **ACTION**: TopAppBar "mDNS Browser". FAB toggles discovery start/stop. LinearProgressIndicator while discovering. LazyColumn of service cards: service name (bold), type (subtitle), host:port, expandable TXT records section.
- **VALIDATE**: Preview renders

### Task 7: Wire navigation
- **ACTION**: Add route to NavHost
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - `MdnsViewModel` — Turbine: discovery lifecycle, service accumulation
- **Integration tests for**:
  - Device test: discover services on local network (manual)

## Validation
