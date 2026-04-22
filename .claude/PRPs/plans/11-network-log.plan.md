# Plan: Network Change Log

## Summary

Implement a persistent network change logger that records connectivity events (connected, disconnected, IP changes, VPN state) using ConnectivityManager callbacks and stores them in Room for chronological viewing with filters.

## User Story

As a user, I want a log of all network changes on my device, so that I can troubleshoot intermittent connectivity issues and track VPN state transitions.

## Metadata

- **Complexity**: Medium
- **Branch**: feat/network-log
- **PR**: PR-11
- **Depends On**: scaffold
- **Estimated Files**: 12
- **New Modules**: feature/netlog (new)

## Patterns to Mirror

### NETWORK_CALLBACK
// SOURCE: core/network/src/main/kotlin/us/beary/netlens/core/network/ConnectivityManagerNetworkMonitor.kt
- ConnectivityManager.registerDefaultNetworkCallback pattern

### ROOM_ENTITY
// Standard entity + DAO pattern

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `feature/netlog/build.gradle.kts` | CREATE | netlens.android.feature, namespace us.beary.netlens.feature.netlog, deps: :core:network, :core:data |
| `settings.gradle.kts` | UPDATE | Add `include(":feature:netlog")` |
| `app/build.gradle.kts` | UPDATE | Add implementation project dep |
| `core/data/src/main/kotlin/us/beary/netlens/core/data/entity/NetworkChangeEventEntity.kt` | CREATE | @Entity: id (auto PK), timestamp, eventType (CONNECTED/DISCONNECTED/IP_CHANGED/VPN_ON/VPN_OFF), oldIp, newIp, networkType (WIFI/CELLULAR/ETHERNET/VPN), isVpn |
| `core/data/src/main/kotlin/us/beary/netlens/core/data/dao/NetworkChangeEventDao.kt` | CREATE | @Dao: getAll (Flow, ordered by timestamp DESC), getByType, insert, deleteOlderThan, deleteAll |
| `core/data/src/main/kotlin/us/beary/netlens/core/data/NetLensDatabase.kt` | UPDATE | Add entity, bump version, add abstract DAO |
| `core/data/src/main/kotlin/us/beary/netlens/core/data/di/DataModule.kt` | UPDATE | @Provides DAO |
| `feature/netlog/src/main/kotlin/us/beary/netlens/feature/netlog/service/NetworkLogService.kt` | CREATE | Singleton service started from Application.onCreate(). Registers ConnectivityManager.NetworkCallback. On onAvailable/onLost/onCapabilitiesChanged: determine event type, extract IP from LinkProperties, detect VPN, persist to Room. Runs in CoroutineScope(SupervisorJob + Dispatchers.IO). |
| `feature/netlog/src/main/kotlin/us/beary/netlens/feature/netlog/di/NetLogModule.kt` | CREATE | @Module providing NetworkLogService |
| `feature/netlog/src/main/kotlin/us/beary/netlens/feature/netlog/model/NetworkLogUiState.kt` | CREATE | data class: events list, selectedFilter (ALL/CONNECTED/DISCONNECTED/IP_CHANGED/VPN), isLoading |
| `feature/netlog/src/main/kotlin/us/beary/netlens/feature/netlog/NetworkLogViewModel.kt` | CREATE | @HiltViewModel, observes events from DAO, filter by type, clear log |
| `feature/netlog/src/main/kotlin/us/beary/netlens/feature/netlog/NetworkLogScreen.kt` | CREATE | Filter chips row (All, Connected, Disconnected, IP Changed, VPN). LazyColumn of event cards: timestamp, event type icon/color, old/new IP, network type badge. Clear log button in top bar. |
| `app/src/main/kotlin/us/beary/netlens/NetLensApplication.kt` | UPDATE | Start NetworkLogService in onCreate |
| `app/src/main/kotlin/us/beary/netlens/navigation/NetLensNavHost.kt` | UPDATE | Add composable route for netlog |

## Step-by-Step Tasks

### Task 1: Create feature module
- **ACTION**: Create build.gradle.kts, update settings + app build
- **VALIDATE**: `./gradlew :feature:netlog:compileDebugKotlin`

### Task 2: Create Room entity + DAO
- **ACTION**: `NetworkChangeEventEntity` with `@PrimaryKey(autoGenerate = true) val id: Long = 0, val timestamp: Long, val eventType: String, val oldIp: String?, val newIp: String?, val networkType: String, val isVpn: Boolean`. DAO with `@Query("SELECT * FROM network_change_events ORDER BY timestamp DESC") fun getAll(): Flow<List<...>>`, `@Query("SELECT * FROM ... WHERE eventType = :type ORDER BY timestamp DESC") fun getByType(type: String): Flow<List<...>>`, insert, `deleteOlderThan(timestamp: Long)`, deleteAll.
- **VALIDATE**: Compiles

### Task 3: Update NetLensDatabase + DataModule
- **ACTION**: Add entity, bump version, add DAO getter + provider
- **VALIDATE**: Compiles

### Task 4: Create NetworkLogService
- **ACTION**: `@Singleton class NetworkLogService @Inject constructor(context: Context, dao: NetworkChangeEventDao)`. In `start()`: register `ConnectivityManager.registerDefaultNetworkCallback`. Track previous IP. On `onAvailable`: insert CONNECTED event with new IP. On `onLost`: insert DISCONNECTED. On `onLinkPropertiesChanged`: compare old/new IP, insert IP_CHANGED if different. On `onCapabilitiesChanged`: check VPN state, insert VPN_ON/VPN_OFF on transition. Auto-prune events older than 30 days.
- **VALIDATE**: Unit test with mock ConnectivityManager

### Task 5: Create DI module
- **ACTION**: `NetLogModule` providing NetworkLogService
- **VALIDATE**: Compiles

### Task 6: Start service from Application
- **ACTION**: In `NetLensApplication.onCreate()`, inject and call `networkLogService.start()` (lazy init to avoid blocking startup)
- **VALIDATE**: Compiles

### Task 7: Create NetworkLogViewModel
- **ACTION**: `@HiltViewModel`, `fun setFilter(type: String?)`. Switches between DAO queries. Exposes events as StateFlow.
- **VALIDATE**: Unit test with Turbine

### Task 8: Create NetworkLogScreen
- **ACTION**: Filter chips: All, Connected, Disconnected, IP Changed, VPN. LazyColumn of events: relative timestamp ("2 min ago"), event type with icon (green dot=connected, red=disconnected, blue=IP changed, shield=VPN), old→new IP, network type badge. "Clear log" action in overflow menu.
- **VALIDATE**: Preview renders

### Task 9: Wire navigation
- **ACTION**: Add route to NavHost
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - NetworkLogService — event detection logic with mock callbacks
  - NetworkLogViewModel — Turbine: filter switching, event list
- **Integration tests for**:
  - NetworkChangeEventDao — Room in-memory: insert, query by type, delete

## Validation
