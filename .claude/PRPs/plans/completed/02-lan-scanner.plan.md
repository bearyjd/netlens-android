# Plan: LAN Scanner + OUI

## Summary

Implement a LAN scanner that discovers devices on the local subnet using ARP table parsing and ICMP reachability checks, enriched with OUI vendor lookups. Results display in a live-updating list with scan progress and persist to Room for offline viewing.

## User Story

As a user, I want to scan my local network to discover connected devices with their IP, MAC address, hostname, and vendor name, so that I can audit what is on my network.

## Metadata

- **Complexity**: Large
- **Branch**: feat/lan-scanner
- **PR**: PR-02
- **Depends On**: scaffold
- **Estimated Files**: 16
- **New Modules**: none (feature/lanscan, core/network, core/data, core/oui already exist)

## Patterns to Mirror

### FEATURE_MODULE
// SOURCE: feature/lanscan/build.gradle.kts — already depends on :core:network, :core:data, :core:oui

### ROOM_ENTITY
// SOURCE: core/data/src/main/kotlin/com.ventoux.netlens/core/data/NetLensDatabase.kt
- Add entity to @Database annotation, bump version, add abstract DAO getter

### OUI_LOOKUP
// SOURCE: core/oui/src/main/kotlin/com.ventoux.netlens/core/oui/OuiLookup.kt
- Inject OuiLookup, call lookup(mac) to get vendor string

### CALLBACKFLOW
// SOURCE: core/network/src/main/kotlin/com.ventoux.netlens/core/network/ConnectivityManagerNetworkMonitor.kt
- callbackFlow + awaitClose pattern for streaming results

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `core/network/src/main/kotlin/com.ventoux.netlens/core/network/SubnetUtils.kt` | CREATE | Utility to derive gateway IP, subnet mask, IP range from LinkProperties/ConnectivityManager |
| `core/network/src/main/kotlin/com.ventoux.netlens/core/network/ArpReader.kt` | CREATE | Parse /proc/net/arp into Map<String(ip), String(mac)> |
| `core/data/src/main/kotlin/com.ventoux.netlens/core/data/entity/LanDeviceEntity.kt` | CREATE | @Entity with ip (PK), mac, vendor, hostname, isReachable, lastSeenAt |
| `core/data/src/main/kotlin/com.ventoux.netlens/core/data/dao/LanDeviceDao.kt` | CREATE | @Dao with insertAll, getAll (Flow), deleteAll |
| `core/data/src/main/kotlin/com.ventoux.netlens/core/data/NetLensDatabase.kt` | UPDATE | Add LanDeviceEntity to entities, add abstract lanDeviceDao(), bump version to 2 |
| `core/data/src/main/kotlin/com.ventoux.netlens/core/data/di/DataModule.kt` | UPDATE | Add @Provides for LanDeviceDao |
| `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/model/LanDevice.kt` | CREATE | Domain data class: ip, mac, vendor, hostname, isReachable |
| `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/model/LanScanUiState.kt` | CREATE | data class: devices list, isScanning, progress (0f-1f), error |
| `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/engine/LanScanner.kt` | CREATE | Interface with `fun scan(): Flow<List<LanDevice>>` |
| `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/engine/LanScannerImpl.kt` | CREATE | Semaphore(50), iterate IP range, InetAddress.isReachable(200), read ARP after, OUI lookup, emit progressive results |
| `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/data/LanScanRepository.kt` | CREATE | Interface: scan() Flow, getLastScanResults() Flow |
| `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/data/LanScanRepositoryImpl.kt` | CREATE | Coordinates LanScanner + LanDeviceDao, persists results |
| `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/di/LanScanModule.kt` | CREATE | @Module binding scanner + repository |
| `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/LanScanViewModel.kt` | CREATE | @HiltViewModel, StateFlow<LanScanUiState>, startScan(), cancelScan() |
| `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/LanScanScreen.kt` | CREATE | LazyColumn of device cards, FAB to start scan, LinearProgressIndicator, device count |
| `app/src/main/kotlin/com.ventoux.netlens/navigation/NetLensNavHost.kt` | UPDATE | Replace PlaceholderScreen for LanScan route with LanScanScreen() |

## Step-by-Step Tasks

### Task 1: Create SubnetUtils
- **ACTION**: Create utility class with `@Inject constructor(@ApplicationContext context: Context)`. Method `getSubnetInfo(): SubnetInfo` returning data class with `gatewayIp`, `subnetMask`, `ipRange: List<String>`. Derive from `ConnectivityManager.getLinkProperties()` → `LinkAddress`.
- **VALIDATE**: Unit test with mock LinkProperties

### Task 2: Create ArpReader
- **ACTION**: Object with `fun readArpTable(): Map<String, String>`. Read `/proc/net/arp`, skip header, parse columns: IP (0), HW type (1), Flags (2), HW address (3). Filter `00:00:00:00:00:00`. Return map of ip→mac.
- **VALIDATE**: Unit test with sample arp file content

### Task 3: Create Room entity + DAO
- **ACTION**: `LanDeviceEntity` with `@PrimaryKey val ip: String, val mac: String, val vendor: String?, val hostname: String?, val isReachable: Boolean, val lastSeenAt: Long`. `LanDeviceDao` with `@Insert(onConflict = REPLACE) suspend fun insertAll(devices: List<LanDeviceEntity>)`, `@Query("SELECT * FROM lan_devices ORDER BY ip") fun getAll(): Flow<List<LanDeviceEntity>>`, `@Query("DELETE FROM lan_devices") suspend fun deleteAll()`.
- **VALIDATE**: Compiles

### Task 4: Update NetLensDatabase
- **ACTION**: Add `LanDeviceEntity::class` to `entities` array. Bump version to 2. Add `abstract fun lanDeviceDao(): LanDeviceDao`.
- **VALIDATE**: Compiles (fallbackToDestructiveMigration handles version bump)

### Task 5: Update DataModule
- **ACTION**: Add `@Provides fun provideLanDeviceDao(db: NetLensDatabase): LanDeviceDao = db.lanDeviceDao()`
- **VALIDATE**: Compiles

### Task 6: Create LanDevice domain model
- **ACTION**: `data class LanDevice(val ip: String, val mac: String, val vendor: String?, val hostname: String?, val isReachable: Boolean)`
- **VALIDATE**: Compiles

### Task 7: Create LanScanner engine
- **ACTION**: Interface `LanScanner { fun scan(): Flow<List<LanDevice>> }`. Impl injects `SubnetUtils`, `ArpReader`, `OuiLookup`. Uses `Semaphore(50)`, `coroutineScope` with async per IP, `InetAddress.getByName(ip).isReachable(200)`. After sweep, read ARP table. For each reachable IP, lookup vendor via OUI. Emit progressive results via `channelFlow`.
- **VALIDATE**: Unit test with fakes

### Task 8: Create LanScanRepository
- **ACTION**: Interface with `fun scan(): Flow<List<LanDevice>>` and `fun getLastScanResults(): Flow<List<LanDevice>>`. Impl persists scan results to Room, maps entities to domain models.
- **VALIDATE**: Compiles

### Task 9: Create DI module
- **ACTION**: `LanScanModule` with `@Binds` for `LanScanner` and `LanScanRepository`
- **VALIDATE**: Compiles

### Task 10: Create LanScanViewModel
- **ACTION**: `@HiltViewModel`, `_uiState = MutableStateFlow(LanScanUiState())`. `startScan()` collects from repository scan flow, updates state with devices + progress. `cancelScan()` cancels scan job. Init loads last scan results from Room.
- **VALIDATE**: Unit test with Turbine

### Task 11: Create LanScanScreen
- **ACTION**: `@Composable LanScanScreen(viewModel = hiltViewModel())`. TopAppBar "LAN Scan". LinearProgressIndicator when scanning. LazyColumn of device cards showing IP, MAC, vendor icon, hostname. FAB with scan icon. Device count chip. Pull-to-refresh.
- **VALIDATE**: Preview renders

### Task 12: Wire navigation
- **ACTION**: Update NetLensNavHost — import LanScanScreen, replace PlaceholderScreen for LanScan route
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - `ArpReader` — parsing various /proc/net/arp formats
  - `SubnetUtils` — IP range calculation from different subnet masks
  - `LanScannerImpl` — mock InetAddress reachability, verify semaphore concurrency
  - `LanScanViewModel` — Turbine: idle → scanning → results, cancel behavior
- **Integration tests for**:
  - `LanDeviceDao` — Room in-memory DB: insertAll, getAll flow, deleteAll

## Validation
