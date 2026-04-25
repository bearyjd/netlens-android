# Plan: Persistent Scan & Lookup History

## Summary

Add a persistent history system for 6 network tools (LAN scan, port scan, ping, DNS, WHOIS, IP info). Results auto-save to Room on completion. A new `feature:history` module provides a unified history screen with search, filter chips, and date grouping. Per-tool history is accessible from each tool's top bar. A history settings section controls retention and per-tool auto-save toggles.

## User Story

As a network diagnostician, I want my scan and lookup results saved automatically so that I can review past results, compare changes over time, and re-run previous scans without re-entering parameters.

## Problem -> Solution

Currently all tool results are ephemeral — they exist only in ViewModel memory and vanish when navigating away. -> Auto-save results to Room on completion; surface them in a searchable, filterable history screen; allow re-running past scans.

## Metadata

- **Complexity**: XL
- **Source PRD**: N/A (inline spec)
- **PRD Phase**: N/A
- **Estimated Files**: ~45 new, ~15 modified

---

## UX Design

### Before

```
┌────────────────────────┐
│ TopAppBar: "Ping"   <- │
│                        │
│ [host input] [copy]    │
│ [4] [8] [16]           │
│ [Start] [Stop]         │
│ bar chart              │
│ summary card           │
│ result rows...         │
│                        │
│ (results lost on back) │
└────────────────────────┘
```

### After

```
┌────────────────────────────┐
│ TopAppBar: "Ping"  [H] <- │  <- [H] = history icon
│                            │
│ [host input] [copy]        │
│ ...same as before...       │
│ (results auto-saved)       │
└────────────────────────────┘

┌────────────────────────────┐
│ TopAppBar: "History"    <- │
│ [search bar.............]  │
│ [All][LAN][Ports][Ping]... │
│                            │
│ Today                      │
│ ┌─ Ping  google.com       │
│ │  avg 12ms, 4/4 recv     │
│ │  2 hours ago             │
│ ├─ DNS  example.com       │
│ │  A, AAAA - 3 records    │
│ │  3 hours ago             │
│ └──────────────────────────│
│ Yesterday                  │
│ ┌─ LAN  192.168.1.0/24   │
│ │  14 devices              │
│ │  ...                     │
└────────────────────────────┘
```

### Interaction Changes

| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Tool completion | Results in memory only | Auto-saved to Room DB | No user action needed |
| Tool top bar | Back button only | Back + history icon | Opens per-tool filtered history |
| Home screen tools | 14 tools | 14 tools + History entry | New `ToolDestination.History` |
| History item tap | N/A | Navigate to tool screen | Read-only view, "Re-run" button (future) |
| Settings | Widget settings only | Widget + History settings | Retention period, per-tool toggles |

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `core/data/src/.../NetLensDatabase.kt` | all | Current DB entities + version (5) |
| P0 | `core/data/src/.../di/DataModule.kt` | all | Migration pattern, DAO provider pattern |
| P0 | `core/data/src/.../dao/NetworkEventDao.kt` | all | DAO method signature convention |
| P0 | `core/data/src/.../model/NetworkEvent.kt` | all | Entity definition convention |
| P0 | `feature/ping/src/.../PingViewModel.kt` | all | Flow completion + auto-save hook point |
| P0 | `feature/lanscan/src/.../LanScanViewModel.kt` | all | Flow completion + auto-save hook point |
| P0 | `feature/portscan/src/.../PortScanViewModel.kt` | all | Try/catch completion pattern |
| P0 | `feature/dns/src/.../DnsLookupViewModel.kt` | all | Result callback completion pattern |
| P0 | `feature/whois/src/.../WhoisViewModel.kt` | all | Sealed state completion pattern |
| P0 | `feature/ipinfo/src/.../IpInfoViewModel.kt` | all | Sealed state completion pattern |
| P1 | `app/src/.../navigation/ToolDestination.kt` | all | Route + category enum pattern |
| P1 | `app/src/.../navigation/NetLensNavHost.kt` | all | Composable routing pattern |
| P1 | `feature/ping/src/.../PingScreen.kt` | 54-91 | Screen scaffold + TopAppBar pattern |
| P1 | `app/build.gradle.kts` | all | Feature module dependency wiring |
| P1 | `settings.gradle.kts` | all | Module include pattern |
| P2 | `core/data/build.gradle.kts` | all | Room + KSP plugin setup |
| P2 | `feature/ping/build.gradle.kts` | all | Feature build.gradle template |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Room migrations | developer.android.com | Must provide Migration(5,6) for each new table; AutoMigration won't work because we're adding 6 tables |
| WorkManager one-shot | developer.android.com | `OneTimeWorkRequestBuilder<T>()` for retention cleanup on app start |

---

## Patterns to Mirror

Code patterns discovered in the codebase. Follow these exactly.

### ENTITY_DEFINITION
```kotlin
// SOURCE: core/data/src/main/kotlin/us/beary/netlens/core/data/model/NetworkEvent.kt:1-15
@Entity(tableName = "network_events", indices = [Index("timestamp")])
data class NetworkEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val transportType: String,
    val networkDetails: String,
    val isVpn: Boolean = false,
)
```

### DAO_CONVENTION
```kotlin
// SOURCE: core/data/src/main/kotlin/us/beary/netlens/core/data/dao/NetworkEventDao.kt:1-26
@Dao
interface NetworkEventDao {
    @Query("SELECT * FROM network_events ORDER BY timestamp DESC")
    fun getAll(): Flow<List<NetworkEvent>>

    @Query("SELECT * FROM network_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<NetworkEvent>>

    @Insert
    suspend fun insert(event: NetworkEvent)

    @Query("DELETE FROM network_events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM network_events")
    suspend fun deleteAll()
}
```

### DATABASE_CLASS
```kotlin
// SOURCE: core/data/src/main/kotlin/us/beary/netlens/core/data/NetLensDatabase.kt:14-29
@Database(
    entities = [...],
    version = 5,
    exportSchema = true,
)
abstract class NetLensDatabase : RoomDatabase() {
    abstract fun wolTargetDao(): WolTargetDao
    // one abstract fun per DAO
}
```

### MIGRATION_PATTERN
```kotlin
// SOURCE: core/data/src/main/kotlin/us/beary/netlens/core/data/di/DataModule.kt:22-28
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS ...")
    }
}
// Added to builder via .addMigrations(MIGRATION_4_5)
```

### DAO_PROVIDER_PATTERN
```kotlin
// SOURCE: core/data/src/main/kotlin/us/beary/netlens/core/data/di/DataModule.kt:43-52
@Provides
fun provideNetworkEventDao(database: NetLensDatabase): NetworkEventDao =
    database.networkEventDao()
```

### VIEWMODEL_STATEFLOW
```kotlin
// SOURCE: feature/ping/src/main/kotlin/.../PingViewModel.kt:19-25
@HiltViewModel
class PingViewModel @Inject constructor(
    private val pinger: Pinger,
) : ViewModel() {
    private val _state = MutableStateFlow(PingUiState())
    val state: StateFlow<PingUiState> = _state.asStateFlow()
```

### FLOW_COMPLETION_SAVE_POINT
```kotlin
// SOURCE: feature/ping/src/main/kotlin/.../PingViewModel.kt:54-61
.onCompletion {
    _state.update { current ->
        current.copy(
            isPinging = false,
            summary = computeSummary(current),
        )
    }
}
```

### RESULT_CALLBACK_SAVE_POINT
```kotlin
// SOURCE: feature/dns/src/main/kotlin/.../DnsLookupViewModel.kt:53-55
dnsResolver.lookup(domain, current.selectedTypes)
    .onSuccess { results ->
        _state.update { it.copy(isLoading = false, results = results) }
    }
```

### SCREEN_SCAFFOLD
```kotlin
// SOURCE: feature/ping/src/main/kotlin/.../PingScreen.kt:54-91
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Ping") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding -> ... }
}
```

### NAVHOST_ROUTE
```kotlin
// SOURCE: app/src/main/kotlin/.../navigation/NetLensNavHost.kt:47
composable(ToolDestination.Ping.route) { PingScreen(onBack = navController::popBackStack) }
```

### TOOL_DESTINATION_ENTRY
```kotlin
// SOURCE: app/src/main/kotlin/.../navigation/ToolDestination.kt:49-55
Ping(
    route = "ping",
    icon = Icons.Default.NetworkPing,
    label = "Ping",
    description = "Latency & reachability",
    category = ToolCategory.Connectivity,
),
```

### FEATURE_DI_MODULE
```kotlin
// SOURCE: feature/ping/src/main/kotlin/.../di/PingModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class PingModule {
    @Binds
    abstract fun bindPinger(impl: PingerImpl): Pinger
}
```

### FEATURE_BUILD_GRADLE
```kotlin
// SOURCE: feature/ping/build.gradle.kts (representative)
plugins {
    id("netlens.android.feature")
}
android {
    namespace = "us.beary.netlens.feature.ping"
}
dependencies {
    implementation(project(":core:network"))
    implementation(libs.core.ktx)
}
```

### TEST_STRUCTURE
```kotlin
// SOURCE: feature/dns/src/test/.../DnsLookupViewModelTest.kt (representative)
@OptIn(ExperimentalCoroutinesApi::class)
class DnsLookupViewModelTest {
    private lateinit var fakeDnsResolver: FakeDnsResolver
    private lateinit var viewModel: DnsLookupViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeDnsResolver = FakeDnsResolver()
        viewModel = DnsLookupViewModel(fakeDnsResolver)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default values`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals("", state.domain)
        }
    }
}
```

### STRING_RESOURCE_NAMING
```
// SOURCE: feature/ping/src/main/res/values/strings.xml
{feature}_{element_type}_{purpose}
Examples: ping_label_host, ping_button_start, ping_stat_sent, ping_cd_copy_results
```

---

## Files to Change

### New Files — `core/data` (entities + DAOs + repository)

| File | Action | Justification |
|---|---|---|
| `core/data/src/main/kotlin/.../model/PingHistoryEntry.kt` | CREATE | Room entity for ping history |
| `core/data/src/main/kotlin/.../model/LanScanHistoryEntry.kt` | CREATE | Room entity for LAN scan history |
| `core/data/src/main/kotlin/.../model/PortScanHistoryEntry.kt` | CREATE | Room entity for port scan history |
| `core/data/src/main/kotlin/.../model/DnsHistoryEntry.kt` | CREATE | Room entity for DNS history |
| `core/data/src/main/kotlin/.../model/WhoisHistoryEntry.kt` | CREATE | Room entity for WHOIS history |
| `core/data/src/main/kotlin/.../model/IpInfoHistoryEntry.kt` | CREATE | Room entity for IP info history |
| `core/data/src/main/kotlin/.../dao/PingHistoryDao.kt` | CREATE | DAO for ping history |
| `core/data/src/main/kotlin/.../dao/LanScanHistoryDao.kt` | CREATE | DAO for LAN scan history |
| `core/data/src/main/kotlin/.../dao/PortScanHistoryDao.kt` | CREATE | DAO for port scan history |
| `core/data/src/main/kotlin/.../dao/DnsHistoryDao.kt` | CREATE | DAO for DNS history |
| `core/data/src/main/kotlin/.../dao/WhoisHistoryDao.kt` | CREATE | DAO for WHOIS history |
| `core/data/src/main/kotlin/.../dao/IpInfoHistoryDao.kt` | CREATE | DAO for IP info history |
| `core/data/src/main/kotlin/.../repository/HistoryRepository.kt` | CREATE | Unified repository wrapping all history DAOs |

### Modified Files — `core/data`

| File | Action | Justification |
|---|---|---|
| `core/data/src/main/kotlin/.../NetLensDatabase.kt` | UPDATE | Add 6 new entities, bump version to 6, add 6 abstract DAO funs |
| `core/data/src/main/kotlin/.../di/DataModule.kt` | UPDATE | Add MIGRATION_5_6, add 6 DAO @Provides, add HistoryRepository @Provides |

### New Files — `feature/history` module

| File | Action | Justification |
|---|---|---|
| `feature/history/build.gradle.kts` | CREATE | Feature module build config |
| `feature/history/src/main/AndroidManifest.xml` | CREATE | Required empty manifest |
| `feature/history/src/main/kotlin/.../HistoryScreen.kt` | CREATE | Unified history UI |
| `feature/history/src/main/kotlin/.../HistoryViewModel.kt` | CREATE | History state management |
| `feature/history/src/main/kotlin/.../model/HistoryUiState.kt` | CREATE | UI state + HistoryItem sealed class |
| `feature/history/src/main/kotlin/.../model/ToolFilter.kt` | CREATE | Filter chip enum |
| `feature/history/src/main/res/values/strings.xml` | CREATE | String resources |
| `feature/history/src/test/.../HistoryViewModelTest.kt` | CREATE | ViewModel unit tests |

### Modified Files — Feature ViewModels (auto-save)

| File | Action | Justification |
|---|---|---|
| `feature/ping/src/main/kotlin/.../PingViewModel.kt` | UPDATE | Inject PingHistoryDao, save on completion |
| `feature/lanscan/src/main/kotlin/.../LanScanViewModel.kt` | UPDATE | Inject LanScanHistoryDao, save on completion |
| `feature/portscan/src/main/kotlin/.../PortScanViewModel.kt` | UPDATE | Inject PortScanHistoryDao, save on completion |
| `feature/dns/src/main/kotlin/.../DnsLookupViewModel.kt` | UPDATE | Inject DnsHistoryDao, save on success |
| `feature/whois/src/main/kotlin/.../WhoisViewModel.kt` | UPDATE | Inject WhoisHistoryDao, save on success |
| `feature/ipinfo/src/main/kotlin/.../IpInfoViewModel.kt` | UPDATE | Inject IpInfoHistoryDao, save on success |

### Modified Files — Feature build.gradle.kts (add core:data dep)

| File | Action | Justification |
|---|---|---|
| `feature/ping/build.gradle.kts` | UPDATE | Add `implementation(project(":core:data"))` |
| `feature/lanscan/build.gradle.kts` | UPDATE | Add `implementation(project(":core:data"))` (may already have it) |
| `feature/portscan/build.gradle.kts` | UPDATE | Add `implementation(project(":core:data"))` |
| `feature/dns/build.gradle.kts` | UPDATE | Add `implementation(project(":core:data"))` |
| `feature/whois/build.gradle.kts` | UPDATE | Add `implementation(project(":core:data"))` |
| `feature/ipinfo/build.gradle.kts` | UPDATE | Add `implementation(project(":core:data"))` (may already have it) |

### Modified Files — Feature Screens (history icon in TopAppBar)

| File | Action | Justification |
|---|---|---|
| `feature/ping/src/main/kotlin/.../PingScreen.kt` | UPDATE | Add history icon button in TopAppBar actions |
| `feature/lanscan/src/main/kotlin/.../LanScanScreen.kt` | UPDATE | Add history icon button |
| `feature/portscan/src/main/kotlin/.../PortScanScreen.kt` | UPDATE | Add history icon button |
| `feature/dns/src/main/kotlin/.../DnsLookupScreen.kt` | UPDATE | Add history icon button |
| `feature/whois/src/main/kotlin/.../WhoisScreen.kt` | UPDATE | Add history icon button |
| `feature/ipinfo/src/main/kotlin/.../IpInfoScreen.kt` | UPDATE | Add history icon button |

### Modified Files — Navigation & App

| File | Action | Justification |
|---|---|---|
| `app/src/main/kotlin/.../navigation/ToolDestination.kt` | UPDATE | Add `History` entry |
| `app/src/main/kotlin/.../navigation/NetLensNavHost.kt` | UPDATE | Add `composable` for history route + per-tool history routes |
| `app/build.gradle.kts` | UPDATE | Add `implementation(project(":feature:history"))` |
| `settings.gradle.kts` | UPDATE | Add `include(":feature:history")` |

## NOT Building

- Traceroute history (not in scope — only the 6 tools specified)
- HTTP tester history, TLS history, mDNS history (not in scope)
- Re-run from history (tapping a row opens the tool but pre-populating is a follow-up)
- Long-press context menu (follow-up)
- Export/share history
- Per-tool history bottom sheet (simplified to navigation-based filter instead)
- WorkManager retention cleanup (simplify to cleanup in HistoryRepository init or app start)
- Settings screen as a separate module (add history settings inline in the history screen via a settings dialog)

---

## Step-by-Step Tasks

### Task 1: Create Room Entities (6 files)

- **ACTION**: Create 6 entity data classes in `core/data/src/main/kotlin/us/beary/netlens/core/data/model/`
- **IMPLEMENT**:

**PingHistoryEntry.kt**:
```kotlin
package us.beary.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history_ping", indices = [Index("timestamp")])
data class PingHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val host: String,
    val sentCount: Int,
    val receivedCount: Int,
    val minMs: Float,
    val avgMs: Float,
    val maxMs: Float,
)
```

**LanScanHistoryEntry.kt**:
```kotlin
@Entity(tableName = "history_lanscan", indices = [Index("timestamp")])
data class LanScanHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val ssid: String?,
    val subnet: String?,
    val deviceCount: Int,
    val devicesJson: String,
)
```

**PortScanHistoryEntry.kt**:
```kotlin
@Entity(tableName = "history_portscan", indices = [Index("timestamp")])
data class PortScanHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val host: String,
    val openPorts: String,
    val totalScanned: Int,
    val durationMs: Long,
)
```

**DnsHistoryEntry.kt**:
```kotlin
@Entity(tableName = "history_dns", indices = [Index("timestamp")])
data class DnsHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val query: String,
    val recordType: String,
    val resultsJson: String,
)
```

**WhoisHistoryEntry.kt**:
```kotlin
@Entity(tableName = "history_whois", indices = [Index("timestamp")])
data class WhoisHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val query: String,
    val rawResponse: String,
)
```

**IpInfoHistoryEntry.kt**:
```kotlin
@Entity(tableName = "history_ipinfo", indices = [Index("timestamp")])
data class IpInfoHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val ip: String,
    val isp: String?,
    val org: String?,
    val countryCode: String?,
    val city: String?,
    val isVpn: Boolean,
)
```

- **MIRROR**: ENTITY_DEFINITION — `@Entity(tableName, indices)`, `@PrimaryKey(autoGenerate = true)`, default `System.currentTimeMillis()`
- **IMPORTS**: `androidx.room.Entity`, `androidx.room.Index`, `androidx.room.PrimaryKey`
- **GOTCHA**: Use `String` for JSON-serialized fields (devicesJson, openPorts, resultsJson) — NOT Room TypeConverters. Serialization/deserialization happens in the repository/ViewModel layer using `kotlinx.serialization`.
- **VALIDATE**: Files compile. Entity field types match what the DAO queries will use.

---

### Task 2: Create DAOs (6 files)

- **ACTION**: Create 6 DAO interfaces in `core/data/src/main/kotlin/us/beary/netlens/core/data/dao/`
- **IMPLEMENT**: Each DAO follows the same pattern. Example for Ping:

```kotlin
package us.beary.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import us.beary.netlens.core.data.model.PingHistoryEntry

@Dao
interface PingHistoryDao {

    @Query("SELECT * FROM history_ping ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<PingHistoryEntry>>

    @Query("SELECT * FROM history_ping WHERE host LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<PingHistoryEntry>>

    @Insert
    suspend fun insert(entry: PingHistoryEntry)

    @Query("DELETE FROM history_ping WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_ping WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM history_ping")
    suspend fun deleteAll()
}
```

Repeat for each entity, changing table name and search column:
- **LanScanHistoryDao**: search on `subnet` field
- **PortScanHistoryDao**: search on `host` field
- **DnsHistoryDao**: search on `query` field
- **WhoisHistoryDao**: search on `query` field
- **IpInfoHistoryDao**: search on `ip` field

- **MIRROR**: DAO_CONVENTION — `Flow<List<T>>` for reads, `suspend` for writes, `@Query` with parameterized values
- **IMPORTS**: `androidx.room.Dao`, `androidx.room.Insert`, `androidx.room.Query`, `kotlinx.coroutines.flow.Flow`
- **GOTCHA**: Default parameter `limit: Int = 50` works in Room DAO interfaces. The `LIKE '%' || :query || '%'` pattern is Room's way of doing contains search — do NOT use string interpolation.
- **VALIDATE**: Each DAO compiles and references the correct entity table name.

---

### Task 3: Update NetLensDatabase

- **ACTION**: Add 6 new entities to `@Database` annotation, bump version to 6, add 6 abstract DAO functions
- **IMPLEMENT**:

```kotlin
@Database(
    entities = [
        SavedHost::class,
        WolTarget::class,
        NetworkEvent::class,
        MonitoredEndpoint::class,
        EndpointCheck::class,
        PingHistoryEntry::class,
        LanScanHistoryEntry::class,
        PortScanHistoryEntry::class,
        DnsHistoryEntry::class,
        WhoisHistoryEntry::class,
        IpInfoHistoryEntry::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class NetLensDatabase : RoomDatabase() {
    abstract fun wolTargetDao(): WolTargetDao
    abstract fun networkEventDao(): NetworkEventDao
    abstract fun endpointDao(): EndpointDao
    abstract fun pingHistoryDao(): PingHistoryDao
    abstract fun lanScanHistoryDao(): LanScanHistoryDao
    abstract fun portScanHistoryDao(): PortScanHistoryDao
    abstract fun dnsHistoryDao(): DnsHistoryDao
    abstract fun whoisHistoryDao(): WhoisHistoryDao
    abstract fun ipInfoHistoryDao(): IpInfoHistoryDao
}
```

- **MIRROR**: DATABASE_CLASS
- **IMPORTS**: Add imports for all 6 new entity classes and 6 new DAO interfaces
- **GOTCHA**: Version must be exactly 6 (incremented from 5). `exportSchema = true` triggers schema JSON export to `core/data/schemas/` — the Room Gradle plugin handles this.
- **VALIDATE**: `./gradlew :core:data:compileDebugKotlin` succeeds.

---

### Task 4: Add Migration and DAO Providers to DataModule

- **ACTION**: Add `MIGRATION_5_6` with 6 `CREATE TABLE` + `CREATE INDEX` statements. Add 6 `@Provides` functions for new DAOs. Add `@Provides` for `HistoryRepository`.
- **IMPLEMENT**:

```kotlin
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `history_ping` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `host` TEXT NOT NULL,
                `sentCount` INTEGER NOT NULL,
                `receivedCount` INTEGER NOT NULL,
                `minMs` REAL NOT NULL,
                `avgMs` REAL NOT NULL,
                `maxMs` REAL NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_ping_timestamp` ON `history_ping` (`timestamp`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `history_lanscan` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `ssid` TEXT,
                `subnet` TEXT,
                `deviceCount` INTEGER NOT NULL,
                `devicesJson` TEXT NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_lanscan_timestamp` ON `history_lanscan` (`timestamp`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `history_portscan` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `host` TEXT NOT NULL,
                `openPorts` TEXT NOT NULL,
                `totalScanned` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_portscan_timestamp` ON `history_portscan` (`timestamp`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `history_dns` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `query` TEXT NOT NULL,
                `recordType` TEXT NOT NULL,
                `resultsJson` TEXT NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_dns_timestamp` ON `history_dns` (`timestamp`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `history_whois` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `query` TEXT NOT NULL,
                `rawResponse` TEXT NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_whois_timestamp` ON `history_whois` (`timestamp`)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `history_ipinfo` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `ip` TEXT NOT NULL,
                `isp` TEXT,
                `org` TEXT,
                `countryCode` TEXT,
                `city` TEXT,
                `isVpn` INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_ipinfo_timestamp` ON `history_ipinfo` (`timestamp`)")
    }
}
```

Update builder: `.addMigrations(MIGRATION_4_5, MIGRATION_5_6)`

Add DAO providers (same pattern as existing):
```kotlin
@Provides
fun providePingHistoryDao(database: NetLensDatabase): PingHistoryDao =
    database.pingHistoryDao()
// ... repeat for all 6
```

Add repository provider:
```kotlin
@Provides
@Singleton
fun provideHistoryRepository(
    pingDao: PingHistoryDao,
    lanScanDao: LanScanHistoryDao,
    portScanDao: PortScanHistoryDao,
    dnsDao: DnsHistoryDao,
    whoisDao: WhoisHistoryDao,
    ipInfoDao: IpInfoHistoryDao,
): HistoryRepository = HistoryRepository(pingDao, lanScanDao, portScanDao, dnsDao, whoisDao, ipInfoDao)
```

- **MIRROR**: MIGRATION_PATTERN, DAO_PROVIDER_PATTERN
- **GOTCHA**: Room maps Kotlin `Boolean` to `INTEGER` (0/1), `Float` to `REAL`, `Long` to `INTEGER`, nullable `String?` to nullable `TEXT`. The SQL column types must match exactly or the migration validation will fail. Index naming convention is `index_{tableName}_{columnName}`.
- **VALIDATE**: `./gradlew :core:data:compileDebugKotlin` succeeds. Run app — migration should execute without crash.

---

### Task 5: Create HistoryRepository

- **ACTION**: Create `core/data/src/main/kotlin/us/beary/netlens/core/data/repository/HistoryRepository.kt`
- **IMPLEMENT**:

```kotlin
package us.beary.netlens.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import us.beary.netlens.core.data.dao.*
import us.beary.netlens.core.data.model.*
import javax.inject.Inject

data class CombinedHistoryResults(
    val pings: List<PingHistoryEntry> = emptyList(),
    val lanScans: List<LanScanHistoryEntry> = emptyList(),
    val portScans: List<PortScanHistoryEntry> = emptyList(),
    val dnsLookups: List<DnsHistoryEntry> = emptyList(),
    val whoisLookups: List<WhoisHistoryEntry> = emptyList(),
    val ipInfoLookups: List<IpInfoHistoryEntry> = emptyList(),
)

class HistoryRepository @Inject constructor(
    private val pingDao: PingHistoryDao,
    private val lanScanDao: LanScanHistoryDao,
    private val portScanDao: PortScanHistoryDao,
    private val dnsDao: DnsHistoryDao,
    private val whoisDao: WhoisHistoryDao,
    private val ipInfoDao: IpInfoHistoryDao,
) {
    fun recentPings(limit: Int = 50): Flow<List<PingHistoryEntry>> = pingDao.getRecent(limit)
    fun recentLanScans(limit: Int = 50): Flow<List<LanScanHistoryEntry>> = lanScanDao.getRecent(limit)
    fun recentPortScans(limit: Int = 50): Flow<List<PortScanHistoryEntry>> = portScanDao.getRecent(limit)
    fun recentDnsLookups(limit: Int = 50): Flow<List<DnsHistoryEntry>> = dnsDao.getRecent(limit)
    fun recentWhoisLookups(limit: Int = 50): Flow<List<WhoisHistoryEntry>> = whoisDao.getRecent(limit)
    fun recentIpInfoLookups(limit: Int = 50): Flow<List<IpInfoHistoryEntry>> = ipInfoDao.getRecent(limit)

    fun searchAll(query: String): Flow<CombinedHistoryResults> {
        return combine(
            pingDao.search(query),
            lanScanDao.search(query),
            portScanDao.search(query),
            dnsDao.search(query),
            whoisDao.search(query),
            ipInfoDao.search(query),
        ) { pings, lans, ports, dns, whois, ipinfo ->
            CombinedHistoryResults(pings, lans, ports, dns, whois, ipinfo)
        }
    }

    fun allRecent(limit: Int = 50): Flow<CombinedHistoryResults> {
        return combine(
            pingDao.getRecent(limit),
            lanScanDao.getRecent(limit),
            portScanDao.getRecent(limit),
            dnsDao.getRecent(limit),
            whoisDao.getRecent(limit),
            ipInfoDao.getRecent(limit),
        ) { pings, lans, ports, dns, whois, ipinfo ->
            CombinedHistoryResults(pings, lans, ports, dns, whois, ipinfo)
        }
    }

    suspend fun clearAll() {
        pingDao.deleteAll()
        lanScanDao.deleteAll()
        portScanDao.deleteAll()
        dnsDao.deleteAll()
        whoisDao.deleteAll()
        ipInfoDao.deleteAll()
    }

    suspend fun clearOlderThan(days: Int) {
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        pingDao.deleteOlderThan(cutoff)
        lanScanDao.deleteOlderThan(cutoff)
        portScanDao.deleteOlderThan(cutoff)
        dnsDao.deleteOlderThan(cutoff)
        whoisDao.deleteOlderThan(cutoff)
        ipInfoDao.deleteOlderThan(cutoff)
    }
}
```

- **MIRROR**: DAO_CONVENTION for Flow-based return types, suspend for mutations
- **GOTCHA**: `combine` with 6 flows uses the `combine(f1, f2, f3, f4, f5, f6) { ... }` overload from `kotlinx.coroutines.flow` — up to 5 params has a built-in overload; for 6, use the `combine(flows: Iterable<Flow>) { Array -> ... }` variant or the vararg version. The 6-param version does NOT exist natively — use `combine(listOf(...)) { results -> CombinedHistoryResults(results[0] as ..., ...) }` or chain two `combine` calls. **Preferred**: use `combine(f1, f2, ..., f5)` for 5 and chain.
- **VALIDATE**: Compiles. Flow types are correct.

---

### Task 6: Add core:data Dependency to Feature Modules

- **ACTION**: Add `implementation(project(":core:data"))` to each feature module's `build.gradle.kts` that needs history auto-save, plus add `kotlinx-serialization-json` dependency for JSON serialization of complex fields.
- **IMPLEMENT**: For each of `feature/ping`, `feature/lanscan`, `feature/portscan`, `feature/dns`, `feature/whois`, `feature/ipinfo` — add to `dependencies {}`:

```kotlin
implementation(project(":core:data"))
implementation(libs.kotlinx.serialization.json)
```

And add to `plugins {}` (if not already present):
```kotlin
alias(libs.plugins.kotlin.serialization)
```

- **GOTCHA**: Some modules (lanscan, ipinfo) may already depend on `core:data` — check first, don't duplicate. The serialization plugin must be applied for `@Serializable` or `Json.encodeToString` to work.
- **VALIDATE**: `./gradlew assembleDebug` succeeds.

---

### Task 7: Auto-Save in PingViewModel

- **ACTION**: Inject `PingHistoryDao` and save on ping completion (both natural and stopped).
- **IMPLEMENT**: Add to constructor:
```kotlin
@HiltViewModel
class PingViewModel @Inject constructor(
    private val pinger: Pinger,
    private val pingHistoryDao: PingHistoryDao,
) : ViewModel() {
```

Add private save function:
```kotlin
private fun saveToHistory(state: PingUiState) {
    val summary = state.summary ?: return
    if (summary.transmitted == 0) return
    viewModelScope.launch {
        pingHistoryDao.insert(
            PingHistoryEntry(
                host = state.host,
                sentCount = summary.transmitted,
                receivedCount = summary.received,
                minMs = summary.minMs,
                avgMs = summary.avgMs,
                maxMs = summary.maxMs,
            ),
        )
    }
}
```

Call `saveToHistory(_state.value)` inside `.onCompletion` block AFTER computing the summary (line 54-61), and in `stopPing()` after the state update.

Specifically in `startPing()`:
```kotlin
.onCompletion {
    _state.update { current ->
        current.copy(
            isPinging = false,
            summary = computeSummary(current),
        )
    }
    saveToHistory(_state.value)
}
```

And in `stopPing()`:
```kotlin
fun stopPing() {
    pingJob?.cancel()
    _state.update { current ->
        current.copy(
            isPinging = false,
            summary = computeSummary(current),
        )
    }
    saveToHistory(_state.value)
}
```

- **MIRROR**: FLOW_COMPLETION_SAVE_POINT
- **IMPORTS**: Add `us.beary.netlens.core.data.dao.PingHistoryDao`, `us.beary.netlens.core.data.model.PingHistoryEntry`
- **GOTCHA**: `saveToHistory` launches its own coroutine via `viewModelScope.launch` so it doesn't block the completion flow. Check that `_state.value` has the updated summary BEFORE calling save — the `.update {}` in `onCompletion` runs synchronously on `MutableStateFlow`, so the value is available immediately after.
- **VALIDATE**: Run ping, navigate away, check DB or re-check after relaunch.

---

### Task 8: Auto-Save in LanScanViewModel

- **ACTION**: Inject `LanScanHistoryDao`, save on scan completion (not on cancel).
- **IMPLEMENT**: Add to constructor:
```kotlin
@HiltViewModel
class LanScanViewModel @Inject constructor(
    private val subnetScanner: SubnetScanner,
    @ApplicationContext private val context: Context,
    private val lanScanHistoryDao: LanScanHistoryDao,
) : ViewModel() {
```

Add save function:
```kotlin
private fun saveToHistory() {
    val state = _uiState.value
    if (state.devices.isEmpty()) return
    viewModelScope.launch {
        lanScanHistoryDao.insert(
            LanScanHistoryEntry(
                ssid = null,
                subnet = state.subnetInfo,
                deviceCount = state.devices.size,
                devicesJson = Json.encodeToString(state.devices.map { it.ip }),
            ),
        )
    }
}
```

Call inside `.onCompletion` in `startScan()`, AFTER the state update:
```kotlin
.onCompletion {
    _uiState.update { it.copy(isScanning = false, progress = 1f) }
    saveToHistory()
}
```

Do NOT call in `cancelScan()` — per spec, cancelled scans are not saved.

- **MIRROR**: FLOW_COMPLETION_SAVE_POINT
- **IMPORTS**: `us.beary.netlens.core.data.dao.LanScanHistoryDao`, `us.beary.netlens.core.data.model.LanScanHistoryEntry`, `kotlinx.serialization.json.Json`, `kotlinx.serialization.encodeToString`
- **GOTCHA**: `devicesJson` stores a simplified list (just IPs) to avoid serializing the full `LanDevice` model which isn't `@Serializable`. Alternatively, serialize a list of `Map<String, String?>` with ip/mac/vendor/hostname. Keep it simple — just the IP list as `Json.encodeToString(state.devices.map { it.ip })`.
- **VALIDATE**: Run scan to completion, verify entry in history.

---

### Task 9: Auto-Save in PortScanViewModel

- **ACTION**: Inject `PortScanHistoryDao`, save after flow collection completes (line 52).
- **IMPLEMENT**: Add to constructor, add save function:

```kotlin
private fun saveToHistory(state: PortScanUiState, startTime: Long) {
    if (state.results.isEmpty()) return
    viewModelScope.launch {
        portScanHistoryDao.insert(
            PortScanHistoryEntry(
                host = state.host,
                openPorts = Json.encodeToString(state.results.filter { it.isOpen }.map { it.port }),
                totalScanned = state.results.size,
                durationMs = System.currentTimeMillis() - startTime,
            ),
        )
    }
}
```

Add `val startTime = System.currentTimeMillis()` at the start of `scan()`, and call `saveToHistory(_state.value, startTime)` after `_state.update { it.copy(isScanning = false) }` (line 52) — only in the success path, NOT in the catch block.

- **MIRROR**: RESULT_CALLBACK_SAVE_POINT (try/catch pattern variant)
- **IMPORTS**: `us.beary.netlens.core.data.dao.PortScanHistoryDao`, `us.beary.netlens.core.data.model.PortScanHistoryEntry`, `kotlinx.serialization.json.Json`, `kotlinx.serialization.encodeToString`
- **GOTCHA**: Track `startTime` as a local variable in `scan()` to compute duration. The cancelled path (via `cancelScan()`) never reaches the success `_state.update` line, so cancels won't save.
- **VALIDATE**: Run port scan to completion, verify entry in history.

---

### Task 10: Auto-Save in DnsLookupViewModel

- **ACTION**: Inject `DnsHistoryDao`, save inside `.onSuccess` callback.
- **IMPLEMENT**: Add to constructor, add save inside `lookup()`:

```kotlin
.onSuccess { results ->
    _state.update { it.copy(isLoading = false, results = results) }
    viewModelScope.launch {
        dnsHistoryDao.insert(
            DnsHistoryEntry(
                query = domain,
                recordType = current.selectedTypes.joinToString(",") { it.displayName },
                resultsJson = Json.encodeToString(results.map { "${it.type.displayName}: ${it.value}" }),
            ),
        )
    }
}
```

- **MIRROR**: RESULT_CALLBACK_SAVE_POINT
- **IMPORTS**: `us.beary.netlens.core.data.dao.DnsHistoryDao`, `us.beary.netlens.core.data.model.DnsHistoryEntry`, `kotlinx.serialization.json.Json`, `kotlinx.serialization.encodeToString`
- **GOTCHA**: `domain` is a local val already captured at line 41 of the current ViewModel. `results` is the parameter from `onSuccess`. Serialize results as simple strings to avoid needing `DnsResult` to be `@Serializable`.
- **VALIDATE**: Run DNS lookup, verify entry.

---

### Task 11: Auto-Save in WhoisViewModel

- **ACTION**: Inject `WhoisHistoryDao`, save when transitioning to `Success` state.
- **IMPLEMENT**: Add to constructor. After each `_state.value = WhoisUiState.Success(...)` assignment, launch a save:

```kotlin
private fun saveToHistory(query: String, whois: WhoisResult?, rdns: RdnsResult?) {
    viewModelScope.launch {
        whoisHistoryDao.insert(
            WhoisHistoryEntry(
                query = query,
                rawResponse = whois?.rawResponse ?: rdns?.hostnames?.joinToString("\n") ?: "",
            ),
        )
    }
}
```

Call at lines 50 and 71 (the two `WhoisUiState.Success` assignments):
```kotlin
_state.value = WhoisUiState.Success(whois = null, rdns = rdns)
saveToHistory(trimmed, null, rdns)
```
and
```kotlin
_state.value = WhoisUiState.Success(whois = whoisResult.getOrNull(), rdns = rdnsResult)
saveToHistory(trimmed, whoisResult.getOrNull(), rdnsResult)
```

- **MIRROR**: Direct state assignment pattern from WhoisViewModel
- **IMPORTS**: `us.beary.netlens.core.data.dao.WhoisHistoryDao`, `us.beary.netlens.core.data.model.WhoisHistoryEntry`
- **GOTCHA**: Don't save on `Error` state — only on `Success`. `trimmed` is the local val from line 38.
- **VALIDATE**: Run WHOIS lookup, verify entry.

---

### Task 12: Auto-Save in IpInfoViewModel

- **ACTION**: Inject `IpInfoHistoryDao`, save inside `.onSuccess` callback.
- **IMPLEMENT**: Add to constructor:
```kotlin
@HiltViewModel
class IpInfoViewModel @Inject constructor(
    private val repository: IpInfoRepository,
    private val ipInfoHistoryDao: IpInfoHistoryDao,
) : ViewModel() {
```

Add save inside `refresh()`:
```kotlin
.onSuccess { data ->
    _uiState.value = IpInfoUiState.Success(data)
    viewModelScope.launch {
        ipInfoHistoryDao.insert(
            IpInfoHistoryEntry(
                ip = data.query,
                isp = data.isp,
                org = data.org,
                countryCode = data.countryCode,
                city = data.city,
                isVpn = data.proxy,
            ),
        )
    }
}
```

- **MIRROR**: RESULT_CALLBACK_SAVE_POINT
- **IMPORTS**: `us.beary.netlens.core.data.dao.IpInfoHistoryDao`, `us.beary.netlens.core.data.model.IpInfoHistoryEntry`
- **GOTCHA**: `IpApiResponse.proxy` maps to `isVpn`. `IpApiResponse.query` is the IP address.
- **VALIDATE**: Open IP Info screen (auto-fetches on init), verify entry saved.

---

### Task 13: Create feature:history Module Scaffold

- **ACTION**: Create the `feature/history/` module with build.gradle.kts, manifest, and add to settings.gradle.kts + app/build.gradle.kts.
- **IMPLEMENT**:

**feature/history/build.gradle.kts**:
```kotlin
plugins {
    id("netlens.android.feature")
}

android {
    namespace = "us.beary.netlens.feature.history"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
```

**feature/history/src/main/AndroidManifest.xml**:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

**settings.gradle.kts** — add: `include(":feature:history")`

**app/build.gradle.kts** — add: `implementation(project(":feature:history"))`

- **MIRROR**: FEATURE_BUILD_GRADLE
- **GOTCHA**: The `netlens.android.feature` plugin auto-applies library + compose + hilt + lifecycle/navigation deps. `compose.material.icons` needed for tool icons in history rows.
- **VALIDATE**: `./gradlew :feature:history:compileDebugKotlin` succeeds.

---

### Task 14: Create HistoryUiState and ToolFilter Models

- **ACTION**: Create model classes for the history screen.
- **IMPLEMENT**:

**feature/history/src/main/kotlin/us/beary/netlens/feature/history/model/ToolFilter.kt**:
```kotlin
package us.beary.netlens.feature.history.model

enum class ToolFilter(val label: String) {
    All("All"),
    Ping("Ping"),
    LanScan("LAN"),
    PortScan("Ports"),
    Dns("DNS"),
    Whois("WHOIS"),
    IpInfo("IP Info"),
}
```

**feature/history/src/main/kotlin/us/beary/netlens/feature/history/model/HistoryUiState.kt**:
```kotlin
package us.beary.netlens.feature.history.model

import androidx.compose.ui.graphics.vector.ImageVector

data class HistoryUiState(
    val items: List<HistoryItem> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: ToolFilter = ToolFilter.All,
    val isLoading: Boolean = true,
)

data class HistoryItem(
    val id: Long,
    val toolName: String,
    val toolIcon: ImageVector,
    val primaryLabel: String,
    val secondarySummary: String,
    val timestamp: Long,
    val toolFilter: ToolFilter,
)
```

- **MIRROR**: UiState data class pattern from PingUiState, DnsLookupUiState
- **GOTCHA**: `ImageVector` in a data class is fine for UI layer — this model lives in the feature module, not core:data. `HistoryItem` is a flattened UI model that merges all 6 entity types into a single list for display.
- **VALIDATE**: Compiles.

---

### Task 15: Create HistoryViewModel

- **ACTION**: Create the ViewModel that loads, searches, and filters history.
- **IMPLEMENT**:

**feature/history/src/main/kotlin/us/beary/netlens/feature/history/HistoryViewModel.kt**:

```kotlin
package us.beary.netlens.feature.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.beary.netlens.core.data.repository.CombinedHistoryResults
import us.beary.netlens.core.data.repository.HistoryRepository
import us.beary.netlens.feature.history.model.*
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        loadHistory()
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        loadHistory()
    }

    fun onFilterSelected(filter: ToolFilter) {
        _state.update { it.copy(selectedFilter = filter) }
        loadHistory()
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }

    private fun loadHistory() {
        val query = _state.value.searchQuery.trim()
        val source = if (query.isBlank()) {
            historyRepository.allRecent()
        } else {
            historyRepository.searchAll(query)
        }

        source
            .onEach { results ->
                val items = mapToItems(results)
                val filtered = applyFilter(items, _state.value.selectedFilter)
                _state.update { it.copy(items = filtered, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    private fun applyFilter(items: List<HistoryItem>, filter: ToolFilter): List<HistoryItem> {
        if (filter == ToolFilter.All) return items
        return items.filter { it.toolFilter == filter }
    }

    private fun mapToItems(results: CombinedHistoryResults): List<HistoryItem> {
        val items = mutableListOf<HistoryItem>()

        results.pings.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "Ping",
                toolIcon = Icons.Default.NetworkPing,
                primaryLabel = entry.host,
                secondarySummary = "avg %.1fms, %d/%d recv".format(entry.avgMs, entry.receivedCount, entry.sentCount),
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.Ping,
            )
        }

        results.lanScans.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "LAN Scan",
                toolIcon = Icons.Default.Router,
                primaryLabel = entry.subnet ?: "Unknown",
                secondarySummary = "${entry.deviceCount} devices",
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.LanScan,
            )
        }

        results.portScans.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "Port Scan",
                toolIcon = Icons.Default.Security,
                primaryLabel = entry.host,
                secondarySummary = "${entry.totalScanned} scanned",
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.PortScan,
            )
        }

        results.dnsLookups.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "DNS",
                toolIcon = Icons.Default.Dns,
                primaryLabel = entry.query,
                secondarySummary = entry.recordType,
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.Dns,
            )
        }

        results.whoisLookups.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "WHOIS",
                toolIcon = Icons.Default.Search,
                primaryLabel = entry.query,
                secondarySummary = "${entry.rawResponse.length} chars",
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.Whois,
            )
        }

        results.ipInfoLookups.mapTo(items) { entry ->
            HistoryItem(
                id = entry.id,
                toolName = "IP Info",
                toolIcon = Icons.Default.Language,
                primaryLabel = entry.ip,
                secondarySummary = listOfNotNull(entry.city, entry.countryCode).joinToString(", "),
                timestamp = entry.timestamp,
                toolFilter = ToolFilter.IpInfo,
            )
        }

        return items.sortedByDescending { it.timestamp }
    }
}
```

- **MIRROR**: VIEWMODEL_STATEFLOW, uses `.launchIn(viewModelScope)` like NetLogViewModel
- **IMPORTS**: Listed in the code above
- **GOTCHA**: Each call to `loadHistory()` launches a new collection — cancel the previous one by storing the `Job` and cancelling, or use `flatMapLatest` on a trigger flow. Simpler approach: use a single `combine` + `flatMapLatest` on search/filter changes. For the initial implementation, the `launchIn` approach is acceptable since the underlying Room flows are already distinct-until-changed.
- **VALIDATE**: Unit test with fakes (Task 19).

---

### Task 16: Create HistoryScreen Composable

- **ACTION**: Create the unified history screen UI.
- **IMPLEMENT**:

**feature/history/src/main/kotlin/us/beary/netlens/feature/history/HistoryScreen.kt**:

Key structure:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* show clear confirmation dialog */ }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.history_cd_clear_all))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text(stringResource(R.string.history_placeholder_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(ToolFilter.entries) { filter ->
                    FilterChip(
                        selected = state.selectedFilter == filter,
                        onClick = { viewModel.onFilterSelected(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // History list grouped by date
            if (state.items.isEmpty() && !state.isLoading) {
                EmptyState()
            } else {
                HistoryList(items = state.items)
            }
        }
    }
}
```

Group items by date header (Today, Yesterday, or formatted date). Each row shows tool icon, tool name, primary label, summary, and relative timestamp.

- **MIRROR**: SCREEN_SCAFFOLD (TopAppBar + Scaffold + back icon)
- **IMPORTS**: Standard Compose + Material3 imports, hiltViewModel, collectAsStateWithLifecycle
- **GOTCHA**: Use `LazyRow` for filter chips (not `FlowRow`) since the chips can overflow. Date grouping logic: compare `timestamp` to start-of-today and start-of-yesterday millis. Relative time: use `android.text.format.DateUtils.getRelativeTimeSpanString()`.
- **VALIDATE**: Navigate to history screen, see empty state. Run a tool, return to history, see entry.

---

### Task 17: Create String Resources for History

- **ACTION**: Create `feature/history/src/main/res/values/strings.xml`
- **IMPLEMENT**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="history_title">History</string>
    <string name="history_placeholder_search">Search history…</string>
    <string name="history_cd_clear_all">Clear all history</string>
    <string name="history_empty_title">No history yet</string>
    <string name="history_empty_subtitle">Results from your scans and lookups will appear here</string>
    <string name="history_group_today">Today</string>
    <string name="history_group_yesterday">Yesterday</string>
    <string name="history_clear_confirm_title">Clear history?</string>
    <string name="history_clear_confirm_message">This will delete all saved scan and lookup results.</string>
    <string name="history_clear_confirm_yes">Clear</string>
    <string name="history_clear_confirm_no">Cancel</string>
</resources>
```

- **MIRROR**: STRING_RESOURCE_NAMING — `history_{element}_{purpose}`
- **VALIDATE**: String references resolve in the composable.

---

### Task 18: Wire History into Navigation

- **ACTION**: Add `ToolDestination.History`, add `composable` route in `NetLensNavHost`, add history icon to each tool screen's TopAppBar.
- **IMPLEMENT**:

**ToolDestination.kt** — add new entry before the trailing `;`:
```kotlin
History(
    route = "history",
    icon = Icons.Default.ManageHistory,
    label = "History",
    description = "Past scan results",
    category = ToolCategory.Tools,
),
```

Note: `Icons.Default.History` is already used by `NetLog`. Use `Icons.Default.ManageHistory` to differentiate.

**NetLensNavHost.kt** — add:
```kotlin
import us.beary.netlens.feature.history.HistoryScreen
// ...
composable(ToolDestination.History.route) { HistoryScreen(onBack = navController::popBackStack) }
```

**Each tool screen** (PingScreen, LanScanScreen, etc.) — add `onNavigateToHistory: () -> Unit = {}` parameter and a history icon in TopAppBar `actions`:
```kotlin
actions = {
    IconButton(onClick = onNavigateToHistory) {
        Icon(Icons.Default.ManageHistory, contentDescription = "History")
    }
}
```

**NetLensNavHost.kt** — update each tool composable to pass the history navigation:
```kotlin
composable(ToolDestination.Ping.route) {
    PingScreen(
        onBack = navController::popBackStack,
        onNavigateToHistory = { navController.navigate(ToolDestination.History.route) { launchSingleTop = true } },
    )
}
```

- **MIRROR**: TOOL_DESTINATION_ENTRY, NAVHOST_ROUTE, SCREEN_SCAFFOLD actions
- **IMPORTS**: `Icons.Default.ManageHistory`, `us.beary.netlens.feature.history.HistoryScreen`
- **GOTCHA**: The `ManageHistory` icon requires `compose.material.icons.extended` — check if `compose.material.icons` already includes it. If not, use `Icons.Default.History` with a different tint or use `Icons.Default.Schedule`. Alternatively, check what icons are available in the non-extended set. **Safest**: use `Icons.Default.History` and differentiate by label, since NetLog uses it but they're in different categories.
- **VALIDATE**: Home screen shows History tool. Tapping opens HistoryScreen. Tool screens show history icon in top bar.

---

### Task 19: Write HistoryViewModel Unit Tests

- **ACTION**: Create test file with fakes for DAOs.
- **IMPLEMENT**:

**feature/history/src/test/kotlin/us/beary/netlens/feature/history/HistoryViewModelTest.kt**:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private lateinit var repository: HistoryRepository
    private lateinit var viewModel: HistoryViewModel
    // Use in-memory fake DAOs

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Create fake DAOs, inject into repository, create viewModel
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is loading`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.isLoading)
        }
    }

    @Test
    fun `items sorted by timestamp descending`() = runTest { ... }

    @Test
    fun `filter by tool shows only matching entries`() = runTest { ... }

    @Test
    fun `search filters results by query`() = runTest { ... }

    @Test
    fun `clearAll empties the list`() = runTest { ... }
}
```

Create fake DAOs that return `MutableStateFlow<List<T>>` from their Flow methods and track inserts in a mutable list.

- **MIRROR**: TEST_STRUCTURE
- **VALIDATE**: `./gradlew :feature:history:testDebugUnitTest` passes.

---

### Task 20: Update Existing ViewModel Tests

- **ACTION**: Update existing ViewModel tests to account for the new DAO constructor parameter.
- **IMPLEMENT**: For each existing ViewModel test (PingViewModelTest, DnsLookupViewModelTest, etc.), update the `setUp()` to pass a fake/no-op DAO:

```kotlin
// Example for PingViewModelTest
private val fakePingHistoryDao = FakePingHistoryDao()
viewModel = PingViewModel(fakePinger, fakePingHistoryDao)
```

Create simple fake DAOs in each test directory or a shared test fixture:
```kotlin
class FakePingHistoryDao : PingHistoryDao {
    val inserted = mutableListOf<PingHistoryEntry>()
    private val _items = MutableStateFlow<List<PingHistoryEntry>>(emptyList())

    override fun getRecent(limit: Int) = _items
    override fun search(query: String) = _items
    override suspend fun insert(entry: PingHistoryEntry) { inserted.add(entry) }
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteOlderThan(before: Long) {}
    override suspend fun deleteAll() { inserted.clear() }
}
```

- **MIRROR**: TEST_STRUCTURE — hand-written fakes, same as FakeDnsResolver pattern
- **GOTCHA**: Existing tests will fail to compile until the new constructor parameter is provided. Fix ALL existing test files before running the test suite.
- **VALIDATE**: `./gradlew testDebugUnitTest` — all tests pass.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| HistoryViewModel initial state | No data | isLoading=true, items=empty | No |
| HistoryViewModel loads items | Pre-populated fake DAOs | Items sorted by timestamp desc | No |
| HistoryViewModel filter by Ping | Mixed items + ToolFilter.Ping | Only ping items shown | No |
| HistoryViewModel search | Query "google" | Items matching "google" | No |
| HistoryViewModel clearAll | Pre-populated data | Empty list | No |
| PingViewModel saves on completion | Run ping to completion | PingHistoryEntry inserted in DAO | No |
| PingViewModel saves on stop | Run ping then stop | PingHistoryEntry inserted | No |
| PingViewModel no save on error | Ping with error | No insertion | Yes |
| DnsViewModel saves on success | Successful lookup | DnsHistoryEntry inserted | No |
| DnsViewModel no save on failure | Failed lookup | No insertion | Yes |
| LanScanViewModel no save on cancel | Start then cancel | No insertion | Yes |
| Empty results | Tool returns empty | No history entry saved | Yes |

### Edge Cases Checklist

- [x] Empty input (no results to save) — guard with early return
- [x] Concurrent scans (cancel previous, save only completed)
- [x] Very large scan results (LAN with 254 devices) — JSON serialization of IP list
- [x] Rapid repeated lookups (each saves independently)
- [x] Database migration from version 5 to 6 — preserves existing data

---

## Validation Commands

### Static Analysis
```bash
./gradlew assembleDebug
```
EXPECT: Zero build errors

### Unit Tests
```bash
./gradlew :feature:history:testDebugUnitTest
./gradlew :feature:ping:testDebugUnitTest
./gradlew :feature:dns:testDebugUnitTest
```
EXPECT: All tests pass

### Full Test Suite
```bash
./gradlew testDebugUnitTest
```
EXPECT: No regressions

### Database Validation
```bash
# Room schema export happens automatically during build
# Check core/data/schemas/ for version 6 JSON schema
ls core/data/schemas/us.beary.netlens.core.data.NetLensDatabase/6.json
```
EXPECT: Schema file exists and contains all 6 new tables

### Manual Validation

- [ ] Open each tool, run a scan/lookup to completion
- [ ] Navigate to History screen from home
- [ ] Verify entry appears with correct tool icon, label, and summary
- [ ] Test search — type a hostname, results filter
- [ ] Test filter chips — each shows only matching tool entries
- [ ] Test "Clear all" — confirm dialog, then empty state
- [ ] Test tool TopAppBar history icon — navigates to history
- [ ] Verify cancelled scans do NOT save (LAN scan, port scan)
- [ ] Verify errors do NOT save
- [ ] Uninstall and reinstall app — migration should work cleanly
- [ ] Upgrade from previous version — migration 5->6 should preserve existing data

---

## Acceptance Criteria

- [ ] All 6 tools auto-save results to Room on successful completion
- [ ] Unified history screen shows all entries sorted by time
- [ ] Search filters across all tool types
- [ ] Filter chips isolate by tool type
- [ ] History is accessible from home screen (tool grid) and per-tool top bar
- [ ] Clear all with confirmation dialog works
- [ ] Room migration 5->6 runs without data loss
- [ ] Cancelled/errored scans are NOT saved
- [ ] All existing tests still pass
- [ ] New HistoryViewModel tests pass
- [ ] No type errors, no lint errors
- [ ] Empty states show meaningful messages

## Completion Checklist

- [ ] Code follows discovered patterns (entity, DAO, ViewModel, Screen, DI)
- [ ] Error handling: save failures are silent (don't crash the scan)
- [ ] Naming matches codebase: `camelCase` functions, `PascalCase` classes, `history_` string prefix
- [ ] Tests use hand-written fakes, JUnit 5, Turbine, backtick names
- [ ] No hardcoded values — strings in resources, limits as parameters
- [ ] No unnecessary scope additions (no traceroute/HTTP/TLS/mDNS history)
- [ ] Self-contained — no questions needed during implementation

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Migration SQL mismatch with Room-generated schema | Medium | High (crash on upgrade) | Compare migration SQL with Room's auto-generated schema JSON; run on real device with v5 DB |
| `combine()` with 6 flows doesn't have a built-in overload | Medium | Medium (compile error) | Use `combine(listOf(f1..f6)) { array -> ... }` or chain two `combine` calls |
| `Icons.Default.ManageHistory` not in base icon set | Medium | Low (build error) | Fall back to `Icons.Default.History` or add `compose.material.icons.extended` dep |
| Large devicesJson for 254-device LAN scans | Low | Low (slow insert) | Serialize only IPs, not full LanDevice objects |
| Existing tests break from new constructor param | Certain | Medium (test failures) | Fix all test files in Task 20 before running suite |

## Notes

- The `combine` 6-flow overload issue is the most likely surprise. The `kotlinx.coroutines.flow.combine` function has overloads up to 5 flows. For 6, use: `combine(listOf(f1, f2, f3, f4, f5, f6)) { results -> CombinedHistoryResults(results[0] as List<PingHistoryEntry>, ...) }` — this requires `@Suppress("UNCHECKED_CAST")` or split into two `combine` calls.
- The database already uses `fallbackToDestructiveMigration()` as a safety net, but we still write a proper migration to preserve user data on upgrades.
- The plan deliberately avoids adding WorkManager for retention cleanup — instead, `HistoryRepository.clearOlderThan()` can be called from `HistoryViewModel.init` or `NetLensApplication.onCreate`. This keeps the implementation simpler and avoids adding a new dependency.
- Per-tool history navigation from the tool screens could alternatively use a query parameter on the history route (e.g., `history?filter=ping`) instead of a separate callback. But the callback approach is consistent with the existing `onBack` pattern and avoids adding navigation arguments.
