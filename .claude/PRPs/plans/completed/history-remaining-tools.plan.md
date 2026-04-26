# Plan: History for Remaining Tools

## Summary
Add history persistence and global History screen integration for the five tools that currently lack it: Traceroute, TLS Inspector, HTTP Tester, mDNS Browser, and Wake-on-LAN. Each tool will save results to Room, appear in the global History screen with search/filter, and support click-to-navigate with pre-populated input.

## User Story
As a network engineer, I want all tool results saved to history, so that I can review, search, and re-run any past operation from one place.

## Problem → Solution
Only 6 of 11 diagnostic tools save history → All 11 tools save history and appear in the global History screen.

## Metadata
- **Complexity**: Large
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: ~35

---

## UX Design

### Before
History screen shows entries for Ping, LAN Scan, Port Scan, DNS, WHOIS, IP Info only. Traceroute, TLS, HTTP Tester, mDNS, and WoL results are lost on screen exit.

### After
History screen shows entries for all 11 tools. Five new filter chips appear. Tapping any entry navigates to the tool with input pre-populated.

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| History filter chips | 7 chips (All + 6 tools) | 12 chips (All + 11 tools) | Scrollable LazyRow already handles overflow |
| Traceroute results | Lost on back press | Saved to history, clickable | Pre-fills host |
| TLS results | Lost on back press | Saved to history, clickable | Pre-fills hostname |
| HTTP Tester results | Lost on back press | Saved to history, clickable | Pre-fills URL |
| mDNS results | Lost on back press | Saved to history, clickable | Navigate only (no input) |
| WoL sends | Lost on back press | Saved to history, clickable | Pre-fills MAC/target |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `core/data/src/main/kotlin/.../repository/HistoryRepository.kt` | all | Central combine pattern |
| P0 | `core/data/src/main/kotlin/.../NetLensDatabase.kt` | all | Entity/DAO registration + migration pattern |
| P0 | `core/data/src/main/kotlin/.../di/DataModule.kt` | all | DAO provider pattern + migrations |
| P0 | `feature/history/src/main/kotlin/.../HistoryViewModel.kt` | 86–167 | mapToItems pattern |
| P0 | `feature/history/src/main/kotlin/.../model/ToolFilter.kt` | all | Enum extension pattern |
| P1 | `core/data/src/main/kotlin/.../dao/PingHistoryDao.kt` | all | DAO template (all 6 DAOs identical shape) |
| P1 | `core/data/src/main/kotlin/.../model/PingHistoryEntry.kt` | all | Entity template |
| P1 | `feature/ping/src/main/kotlin/.../PingViewModel.kt` | 118–131, 200–217 | saveToHistory pattern |
| P2 | `feature/history/src/main/kotlin/.../HistoryScreen.kt` | 282–290 | toolFilterIcon mapping |
| P2 | `app/src/main/kotlin/.../navigation/NetLensNavHost.kt` | all | Route registration with query arg |

---

## Patterns to Mirror

### ENTITY_PATTERN
```kotlin
// SOURCE: core/data/src/main/kotlin/.../model/PingHistoryEntry.kt
@Entity(tableName = "history_ping")
data class PingHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val host: String,
    // ... tool-specific fields
)
```

### DAO_PATTERN
```kotlin
// SOURCE: core/data/src/main/kotlin/.../dao/PingHistoryDao.kt
@Dao
interface PingHistoryDao {
    @Query("SELECT * FROM history_ping ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<PingHistoryEntry>>

    @Query("SELECT * FROM history_ping WHERE host LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    fun search(query: String, limit: Int = 50): Flow<List<PingHistoryEntry>>

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

### SAVE_TO_HISTORY_PATTERN
```kotlin
// SOURCE: feature/ping/src/main/kotlin/.../PingViewModel.kt:200-217
private suspend fun saveToHistory() {
    val state = _state.value
    // Guard: return early if no meaningful data
    if (state.summary == null) return
    pingHistoryDao.insert(
        PingHistoryEntry(
            host = state.host,
            // ... map state fields to entity fields
        ),
    )
}
```

### HISTORY_COMBINE_PATTERN
```kotlin
// SOURCE: core/data/src/main/kotlin/.../repository/HistoryRepository.kt:34-58
fun allRecent(limit: Int = 50): Flow<CombinedHistoryResults> {
    return combine(
        pingHistoryDao.getRecent(limit),
        lanScanHistoryDao.getRecent(limit),
        portScanHistoryDao.getRecent(limit),
        dnsHistoryDao.getRecent(limit),
        whoisHistoryDao.getRecent(limit),
    ) { pings, lanScans, portScans, dns, whois ->
        CombinedHistoryResults(pings = pings, lanScans = lanScans, ...)
    }.combine(ipInfoHistoryDao.getRecent(limit)) { results, ipInfo ->
        results.copy(ipInfoLookups = ipInfo)
    }
}
```

### MAP_TO_ITEMS_PATTERN
```kotlin
// SOURCE: feature/history/src/main/kotlin/.../HistoryViewModel.kt:89-99
results.pings.mapTo(items) { entry ->
    HistoryItem(
        id = entry.id,
        toolName = "Ping",
        primaryLabel = entry.host,
        secondarySummary = "avg %.1fms, %d/%d recv".format(entry.avgMs, entry.receivedCount, entry.sentCount),
        timestamp = entry.timestamp,
        toolFilter = ToolFilter.Ping,
        toolRoute = "ping",
    )
}
```

### MIGRATION_PATTERN
```kotlin
// SOURCE: core/data/src/main/kotlin/.../di/DataModule.kt:43-61
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `history_ping` (...)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_ping_timestamp` ON `history_ping` (`timestamp`)")
    }
}
```

---

## Files to Change

### core/data (per tool — 5x)
| File | Action | Justification |
|---|---|---|
| `core/data/src/main/kotlin/.../model/TracerouteHistoryEntry.kt` | CREATE | Room entity |
| `core/data/src/main/kotlin/.../model/TlsHistoryEntry.kt` | CREATE | Room entity |
| `core/data/src/main/kotlin/.../model/HttpTesterHistoryEntry.kt` | CREATE | Room entity |
| `core/data/src/main/kotlin/.../model/MdnsHistoryEntry.kt` | CREATE | Room entity |
| `core/data/src/main/kotlin/.../model/WolHistoryEntry.kt` | CREATE | Room entity |
| `core/data/src/main/kotlin/.../dao/TracerouteHistoryDao.kt` | CREATE | Room DAO |
| `core/data/src/main/kotlin/.../dao/TlsHistoryDao.kt` | CREATE | Room DAO |
| `core/data/src/main/kotlin/.../dao/HttpTesterHistoryDao.kt` | CREATE | Room DAO |
| `core/data/src/main/kotlin/.../dao/MdnsHistoryDao.kt` | CREATE | Room DAO |
| `core/data/src/main/kotlin/.../dao/WolHistoryDao.kt` | CREATE | Room DAO |

### core/data (shared)
| File | Action | Justification |
|---|---|---|
| `core/data/src/main/kotlin/.../NetLensDatabase.kt` | UPDATE | Add 5 entities + 5 DAO accessors, bump version to 8 |
| `core/data/src/main/kotlin/.../di/DataModule.kt` | UPDATE | Add MIGRATION_7_8 + 5 @Provides DAO methods |
| `core/data/src/main/kotlin/.../repository/HistoryRepository.kt` | UPDATE | Add 5 fields to CombinedHistoryResults, wire into allRecent/searchAll/clearAll/clearOlderThan |

### feature modules (per tool — 5x)
| File | Action | Justification |
|---|---|---|
| `feature/traceroute/src/main/kotlin/.../TracerouteViewModel.kt` | UPDATE | Inject DAO, add saveToHistory() |
| `feature/tls/src/main/kotlin/.../TlsViewModel.kt` | UPDATE | Inject DAO, add saveToHistory() |
| `feature/httptester/src/main/kotlin/.../HttpTesterViewModel.kt` | UPDATE | Inject DAO, add saveToHistory() |
| `feature/mdns/src/main/kotlin/.../MdnsViewModel.kt` | UPDATE | Inject DAO, add saveToHistory() |
| `feature/wol/src/main/kotlin/.../WolViewModel.kt` | UPDATE | Inject DAO, add saveToHistory() on send |

### feature/history
| File | Action | Justification |
|---|---|---|
| `feature/history/src/main/kotlin/.../model/ToolFilter.kt` | UPDATE | Add 5 enum values |
| `feature/history/src/main/kotlin/.../HistoryViewModel.kt` | UPDATE | Add 5 mapTo blocks in mapToItems() |
| `feature/history/src/main/kotlin/.../HistoryScreen.kt` | UPDATE | Add 5 icon mappings in toolFilterIcon() |
| `feature/history/src/main/res/values/strings.xml` | UPDATE | Add 5 filter label strings |

### Navigation
| File | Action | Justification |
|---|---|---|
| `app/src/main/kotlin/.../navigation/NetLensNavHost.kt` | UPDATE | Add query arg routes for traceroute, tls, httptester |
| `feature/traceroute/src/main/kotlin/.../TracerouteScreen.kt` | UPDATE | Add initialHost param |
| `feature/tls/src/main/kotlin/.../TlsScreen.kt` | UPDATE | Add initialHost param |
| `feature/httptester/src/main/kotlin/.../HttpTesterScreen.kt` | UPDATE | Add initialUrl param |

## NOT Building

- History detail view (showing old results) — separate PR
- Export/share history entries
- History retention settings UI
- Traceroute/TLS/HTTP Tester result caching beyond what's in the history entry

---

## Step-by-Step Tasks

### Task 1: Create 5 Room Entity Models
- **ACTION**: Create entity data classes in `core/data/src/main/kotlin/.../model/`
- **IMPLEMENT**:
  - `TracerouteHistoryEntry`: `host`, `hopCount`, `hopsJson` (JSON of hop list)
  - `TlsHistoryEntry`: `host`, `port` (default 443), `issuer`, `subject`, `expiresAt`, `protocol`, `isValid`
  - `HttpTesterHistoryEntry`: `url`, `method`, `statusCode`, `durationMs`, `responseSize`
  - `MdnsHistoryEntry`: `serviceCount`, `servicesJson` (JSON of discovered services)
  - `WolHistoryEntry`: `mac`, `label` (nullable), `broadcastIp`
- **MIRROR**: ENTITY_PATTERN — all have `id: Long = 0`, `timestamp: Long = System.currentTimeMillis()`
- **GOTCHA**: Table names must be `history_traceroute`, `history_tls`, `history_http`, `history_mdns`, `history_wol` — follow existing `history_*` convention
- **VALIDATE**: Files compile, no duplicate table names

### Task 2: Create 5 Room DAO Interfaces
- **ACTION**: Create DAO interfaces in `core/data/src/main/kotlin/.../dao/`
- **IMPLEMENT**: Each DAO has exactly 6 methods: `getRecent`, `search`, `insert`, `deleteById`, `deleteOlderThan`, `deleteAll`
- **MIRROR**: DAO_PATTERN — identical method signatures, only table name and search column differ
- **IMPORTS**: `androidx.room.*`, `kotlinx.coroutines.flow.Flow`
- **GOTCHA**: Search column must match the primary user-visible field (host, url, mac, etc.). For mDNS, search on `servicesJson` since there's no single identifier
- **VALIDATE**: `./gradlew :core:data:compileDebugKotlin`

### Task 3: Register in Room Database
- **ACTION**: Update `NetLensDatabase.kt` — add 5 entities to `@Database`, add 5 abstract DAO accessor methods, bump version to 8
- **IMPLEMENT**: Add to entities array, add `abstract fun tracerouteHistoryDao(): TracerouteHistoryDao` etc.
- **MIRROR**: Existing entity/DAO registration pattern
- **GOTCHA**: Version must bump from 7 to 8 or Room will crash at runtime
- **VALIDATE**: Compiles

### Task 4: Add Migration 7→8
- **ACTION**: Update `DataModule.kt` — add `MIGRATION_7_8` that creates 5 tables with indexes
- **IMPLEMENT**: Single migration with 5 `CREATE TABLE` + 5 `CREATE INDEX` statements. Add 5 `@Provides` methods for the new DAOs.
- **MIRROR**: MIGRATION_PATTERN — match the `CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS` pattern from migration 5→6
- **GOTCHA**: Column types must match entity fields exactly. Add migration to the `addMigrations()` call in `buildDatabase()`
- **VALIDATE**: `./gradlew :core:data:testDebugUnitTest`

### Task 5: Extend CombinedHistoryResults + HistoryRepository
- **ACTION**: Add 5 new fields to `CombinedHistoryResults`, wire 5 new DAOs into `HistoryRepository`
- **IMPLEMENT**: Inject 5 new DAOs into constructor. Chain additional `.combine()` calls in `allRecent()` and `searchAll()`. Add `deleteAll()` calls in `clearAll()` and `clearOlderThan()`
- **MIRROR**: HISTORY_COMBINE_PATTERN — `combine()` only takes 5 flows, chain extras with `.combine()`
- **GOTCHA**: `combine()` 5-arity limit means you need multiple chained `.combine()` calls. Currently there's already one chain for ipInfo; you'll need to add more
- **VALIDATE**: Compiles, existing history tests still pass

### Task 6: Add saveToHistory() to 5 ViewModels
- **ACTION**: Inject the appropriate DAO into each ViewModel constructor, add `saveToHistory()` call after successful operation
- **IMPLEMENT**: Follow the save-after-completion pattern. For Traceroute: save after trace completes. For TLS: save after inspection completes. For HTTP Tester: save after response received. For mDNS: save after discovery completes. For WoL: save after magic packet sent.
- **MIRROR**: SAVE_TO_HISTORY_PATTERN — guard against empty results, call DAO insert
- **GOTCHA**: WoL is fire-and-forget — save immediately on send since there's no "result" to wait for
- **VALIDATE**: Run each tool manually, verify entry appears in DB

### Task 7: Extend ToolFilter Enum + History ViewModel
- **ACTION**: Add 5 values to `ToolFilter`, add 5 `mapTo` blocks in `HistoryViewModel.mapToItems()`, add 5 string resources
- **IMPLEMENT**: `ToolFilter.Traceroute`, `.Tls`, `.HttpTester`, `.Mdns`, `.Wol` with string resources. Map each new `CombinedHistoryResults` field to `HistoryItem` with appropriate `toolName`, `primaryLabel`, `secondarySummary`, `toolRoute`
- **MIRROR**: MAP_TO_ITEMS_PATTERN
- **GOTCHA**: `toolRoute` must match the route string in `ToolDestination` enum exactly
- **VALIDATE**: Build, open History screen, verify new filter chips appear

### Task 8: Update HistoryScreen Icon Mapping
- **ACTION**: Add 5 cases to `toolFilterIcon()` in `HistoryScreen.kt`
- **IMPLEMENT**: Traceroute → `Icons.Default.Timeline`, TLS → `Icons.Default.Lock`, HTTP → `Icons.Default.Http`, mDNS → `Icons.Default.Wifi`, WoL → `Icons.Default.PowerSettingsNew`
- **GOTCHA**: Verify Material Icons availability — use existing imports as reference
- **VALIDATE**: Visual check on device

### Task 9: Wire Navigation Pre-population
- **ACTION**: Add optional query args to Traceroute, TLS, HTTP Tester routes in `NetLensNavHost.kt`. Add `initialHost`/`initialUrl` params to their Screen composables.
- **IMPLEMENT**: Same pattern as existing Ping/DNS/WHOIS route registrations with `?query={query}` and `navArgument`
- **MIRROR**: Existing query-arg route pattern in `NetLensNavHost.kt`
- **GOTCHA**: mDNS and WoL don't have a single text input to pre-fill — just navigate without query for those
- **VALIDATE**: Tap history entries, verify navigation and pre-population

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| Migration 7→8 runs clean | Empty DB at v7 | All 5 new tables created | No |
| Each DAO insert + getRecent | Insert entry | Entry appears in getRecent flow | No |
| Each DAO search | Insert entry with "test" host | Appears in search("test") | No |
| HistoryRepository combines 11 sources | All DAOs emit | CombinedHistoryResults has all 11 lists | No |
| mapToItems includes new tools | Combined results with all types | HistoryItems for all 11 tools | No |
| clearAll deletes from all 11 tables | Entries in all tables | All tables empty after clearAll | No |

### Edge Cases Checklist
- [ ] Empty results from new tools (guard in saveToHistory)
- [ ] Very long URLs in HTTP Tester history (text overflow in UI)
- [ ] mDNS with 0 services discovered (skip save)
- [ ] WoL with no label (nullable, display MAC only)
- [ ] Migration from v7 with existing data (non-destructive)

---

## Validation Commands

### Static Analysis
```bash
./gradlew assembleDebug
```
EXPECT: Zero errors

### Unit Tests
```bash
./gradlew :core:data:testDebugUnitTest :feature:history:testDebugUnitTest
```
EXPECT: All tests pass

### Full Test Suite
```bash
./gradlew testDebugUnitTest
```
EXPECT: No regressions

### Manual Validation
- [ ] Run each of the 5 new tools, verify entry appears in History
- [ ] Filter by each new tool type
- [ ] Search for a known entry
- [ ] Tap each new tool's history entry, verify navigation + pre-population
- [ ] Clear all history, verify all entries removed
- [ ] Fresh install (migration from scratch) works

---

## Acceptance Criteria
- [ ] All 5 tools save to history after successful operation
- [ ] All 5 tools appear in global History screen with correct icons
- [ ] Filter chips work for all 5 new tool types
- [ ] Search finds entries from all 5 new tools
- [ ] Tapping entries navigates to the correct tool
- [ ] Pre-population works for Traceroute, TLS, HTTP Tester
- [ ] Room migration 7→8 runs cleanly on upgrade
- [ ] All validation commands pass

## Completion Checklist
- [ ] Code follows discovered patterns (entity, DAO, save, combine, map)
- [ ] Error handling matches codebase style (guard clauses in saveToHistory)
- [ ] Tests follow test patterns
- [ ] No hardcoded values (string resources for filter labels)
- [ ] CI passes

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `combine()` chaining with 11 flows is verbose | High | Low | Extract helper function if needed |
| Room migration breaks on existing installs | Low | High | Test migration with real v7 DB |
| Material Icons missing for some tool types | Low | Low | Fallback to generic icons |

## Notes
- WoL is unique: it's a fire-and-forget action with no "results" screen, so history serves as a send log rather than a results viewer.
- mDNS discovery is continuous/ongoing, so save a snapshot when the user leaves the screen or explicitly stops.
- Consider chunking this into 2 PRs if the diff is too large: one for core/data infrastructure (entities, DAOs, migration, repository) and one for feature module integration.
