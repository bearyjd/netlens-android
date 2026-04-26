# Plan: History Detail View (Show Old Results)

## Summary
Add a detail view when tapping history entries that shows the saved results from a past run, with an option to re-run. Currently tapping a history entry navigates directly to the tool and starts a new run. This plan adds an intermediate detail screen that displays the historical data, letting users review past results without re-running.

## User Story
As a network engineer, I want to view the results of a past scan without re-running it, so that I can compare results over time and avoid unnecessary network traffic.

## Problem → Solution
Tapping a history entry always triggers a re-run → Tapping shows saved results first, with a "Re-run" button for when you want fresh data.

## Metadata
- **Complexity**: Large
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: ~20

---

## UX Design

### Before
```
History Screen          Tool Screen
┌──────────────┐       ┌──────────────┐
│ Ping 8.8.8.8 │──tap──│ [8.8.8.8]    │
│ avg 29ms     │       │ [Start] ← auto│
│              │       │              │
│ LAN Scan     │       │ scanning...  │
│ 192.168.1.0  │       │              │
└──────────────┘       └──────────────┘
```

### After
```
History Screen      Detail Sheet          Tool Screen
┌──────────────┐   ┌──────────────┐      ┌──────────────┐
│ Ping 8.8.8.8 │─┐ │ Ping: 8.8.8.8│      │              │
│ avg 29ms     │ │ │ 36 min ago   │      │              │
│              │ │ │──────────────│      │              │
│ LAN Scan     │ ├─│ avg: 29.1ms  │──Re──│ [8.8.8.8]   │
│ 192.168.1.0  │ │ │ min: 22ms    │ run  │ scanning...  │
└──────────────┘ │ │ max: 35ms    │      │              │
                 │ │ sent: 4 recv:4│      └──────────────┘
                 │ │              │
                 │ │ [Re-run ▶]  │
                 │ └──────────────┘
                 │
                 │ ┌──────────────┐
                 └─│ LAN Scan     │
                   │ 192.168.1.0  │
                   │──────────────│
                   │ 21 devices:  │
                   │ 192.168.1.1  │
                   │ 192.168.1.5  │
                   │ ...          │
                   │ [Re-scan ▶] │
                   └──────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Tap history entry | Navigates to tool, starts re-run | Opens bottom sheet with saved results | Major UX change |
| Re-run | Automatic on tap | Explicit button in detail sheet | User opts in |
| Back from detail | N/A (was on tool screen) | Dismisses sheet, stays on History | Less disruptive |
| Long-press entry | Nothing | Could delete entry (future) | Out of scope |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `feature/history/src/main/kotlin/.../HistoryScreen.kt` | all | Current click handler and list rendering |
| P0 | `feature/history/src/main/kotlin/.../HistoryViewModel.kt` | 86–167 | mapToItems — shows what data is available per tool |
| P0 | `feature/history/src/main/kotlin/.../model/HistoryUiState.kt` | all | HistoryItem data class |
| P0 | `core/data/src/main/kotlin/.../dao/PingHistoryDao.kt` | all | Need to add getById |
| P0 | `core/data/src/main/kotlin/.../model/PingHistoryEntry.kt` | all | What fields are stored |
| P1 | `feature/lanscan/src/main/kotlin/.../HostDetailSheet.kt` | all | Existing bottom sheet pattern |
| P1 | `core/data/src/main/kotlin/.../model/LanScanHistoryEntry.kt` | all | devicesJson field for result display |
| P1 | `core/data/src/main/kotlin/.../model/PortScanHistoryEntry.kt` | all | openPorts JSON field |
| P1 | `core/data/src/main/kotlin/.../model/DnsHistoryEntry.kt` | all | resultsJson field |
| P2 | `core/data/src/main/kotlin/.../repository/HistoryRepository.kt` | all | May need getById methods |

---

## Patterns to Mirror

### BOTTOM_SHEET_PATTERN
```kotlin
// SOURCE: feature/lanscan/src/main/kotlin/.../LanScanScreen.kt (hostDetail sheet)
// ModalBottomSheet pattern used for device detail in LAN Scan
if (hostDetail != null) {
    ModalBottomSheet(onDismissRequest = viewModel::dismissDetail) {
        HostDetailContent(...)
    }
}
```

### DAO_GETBYID_PATTERN (new — does not exist yet)
```kotlin
// Pattern to add to each DAO:
@Query("SELECT * FROM history_ping WHERE id = :id")
suspend fun getById(id: Long): PingHistoryEntry?
```

### HISTORY_ITEM_PATTERN
```kotlin
// SOURCE: feature/history/src/main/kotlin/.../model/HistoryUiState.kt
data class HistoryItem(
    val id: Long,
    val toolName: String,
    val primaryLabel: String,
    val secondarySummary: String,
    val timestamp: Long,
    val toolFilter: ToolFilter,
    val toolRoute: String,
)
```

---

## Files to Change

### core/data — DAO updates
| File | Action | Justification |
|---|---|---|
| `core/data/src/main/kotlin/.../dao/PingHistoryDao.kt` | UPDATE | Add `getById(id: Long)` |
| `core/data/src/main/kotlin/.../dao/LanScanHistoryDao.kt` | UPDATE | Add `getById(id: Long)` |
| `core/data/src/main/kotlin/.../dao/PortScanHistoryDao.kt` | UPDATE | Add `getById(id: Long)` |
| `core/data/src/main/kotlin/.../dao/DnsHistoryDao.kt` | UPDATE | Add `getById(id: Long)` |
| `core/data/src/main/kotlin/.../dao/WhoisHistoryDao.kt` | UPDATE | Add `getById(id: Long)` |
| `core/data/src/main/kotlin/.../dao/IpInfoHistoryDao.kt` | UPDATE | Add `getById(id: Long)` |

### core/data — repository
| File | Action | Justification |
|---|---|---|
| `core/data/src/main/kotlin/.../repository/HistoryRepository.kt` | UPDATE | Add `getEntry(toolFilter, id)` dispatcher method |

### feature/history — detail view
| File | Action | Justification |
|---|---|---|
| `feature/history/src/main/kotlin/.../HistoryDetailSheet.kt` | CREATE | Bottom sheet composable for showing saved results |
| `feature/history/src/main/kotlin/.../model/HistoryDetailState.kt` | CREATE | Sealed interface for detail loading/loaded/error states |
| `feature/history/src/main/kotlin/.../HistoryViewModel.kt` | UPDATE | Add selectEntry/dismissDetail methods, detail state flow |
| `feature/history/src/main/kotlin/.../HistoryScreen.kt` | UPDATE | Show bottom sheet on tap instead of navigating, add re-run button |
| `feature/history/src/main/kotlin/.../model/HistoryUiState.kt` | UPDATE | Add selectedDetail field |

### feature/history — result renderers
| File | Action | Justification |
|---|---|---|
| `feature/history/src/main/kotlin/.../detail/PingDetailContent.kt` | CREATE | Render saved ping stats |
| `feature/history/src/main/kotlin/.../detail/LanScanDetailContent.kt` | CREATE | Render saved device list |
| `feature/history/src/main/kotlin/.../detail/PortScanDetailContent.kt` | CREATE | Render saved open ports |
| `feature/history/src/main/kotlin/.../detail/DnsDetailContent.kt` | CREATE | Render saved DNS records |
| `feature/history/src/main/kotlin/.../detail/WhoisDetailContent.kt` | CREATE | Render saved WHOIS text |
| `feature/history/src/main/kotlin/.../detail/IpInfoDetailContent.kt` | CREATE | Render saved IP info |

### Build config
| File | Action | Justification |
|---|---|---|
| `feature/history/build.gradle.kts` | UPDATE | Add `kotlinx.serialization` dependency for JSON parsing |

## NOT Building

- Edit/annotate history entries
- Compare two history entries side-by-side
- Export history to file
- History for tools that don't yet save history (separate PR: history-remaining-tools)
- Delete individual entries from detail view (future enhancement)

---

## Step-by-Step Tasks

### Task 1: Add getById to All 6 Existing DAOs
- **ACTION**: Add `@Query("SELECT * FROM <table> WHERE id = :id") suspend fun getById(id: Long): <Entity>?` to each DAO
- **IMPLEMENT**: One-line addition per DAO. Return nullable since the entry may have been deleted.
- **MIRROR**: Existing DAO query patterns
- **GOTCHA**: `DnsHistoryDao` and `WhoisHistoryDao` use backtick-escaped `query` column — getById uses `id`, no escaping needed
- **VALIDATE**: `./gradlew :core:data:compileDebugKotlin`

### Task 2: Add Repository Dispatcher Method
- **ACTION**: Add method to `HistoryRepository` that routes a `(ToolFilter, Long)` to the correct DAO's `getById`
- **IMPLEMENT**:
  ```kotlin
  suspend fun getEntry(filter: ToolFilter, id: Long): HistoryDetailData? {
      return when (filter) {
          ToolFilter.Ping -> pingHistoryDao.getById(id)?.let { HistoryDetailData.Ping(it) }
          ToolFilter.LanScan -> lanScanHistoryDao.getById(id)?.let { HistoryDetailData.LanScan(it) }
          // ... etc
          ToolFilter.All -> null
      }
  }
  ```
- **IMPORTS**: New sealed interface `HistoryDetailData` in repository file or separate model
- **GOTCHA**: `ToolFilter.All` has no single DAO — return null
- **VALIDATE**: Compiles

### Task 3: Create HistoryDetailState Model
- **ACTION**: Create sealed interface for detail view states
- **IMPLEMENT**:
  ```kotlin
  sealed interface HistoryDetailState {
      data object Loading : HistoryDetailState
      data class Loaded(val item: HistoryItem, val data: HistoryDetailData) : HistoryDetailState
      data class Error(val message: String) : HistoryDetailState
  }
  ```
- **MIRROR**: Sealed state pattern used throughout the app
- **VALIDATE**: Compiles

### Task 4: Create HistoryDetailData Sealed Interface
- **ACTION**: Create sealed interface wrapping each entry type
- **IMPLEMENT**:
  ```kotlin
  sealed interface HistoryDetailData {
      data class Ping(val entry: PingHistoryEntry) : HistoryDetailData
      data class LanScan(val entry: LanScanHistoryEntry) : HistoryDetailData
      data class PortScan(val entry: PortScanHistoryEntry) : HistoryDetailData
      data class Dns(val entry: DnsHistoryEntry) : HistoryDetailData
      data class Whois(val entry: WhoisHistoryEntry) : HistoryDetailData
      data class IpInfo(val entry: IpInfoHistoryEntry) : HistoryDetailData
  }
  ```
- **GOTCHA**: feature:history module needs to depend on core:data models — verify build.gradle.kts
- **VALIDATE**: Compiles

### Task 5: Update HistoryViewModel
- **ACTION**: Add `selectedDetail: StateFlow<HistoryDetailState?>`, `selectEntry(item: HistoryItem)`, and `dismissDetail()` methods
- **IMPLEMENT**: `selectEntry` sets state to `Loading`, launches coroutine to call `historyRepository.getEntry(item.toolFilter, item.id)`, then sets `Loaded` or `Error`
- **MIRROR**: LanScanViewModel's `selectDevice`/`dismissDetail` pattern
- **GOTCHA**: The entry may be deleted between tap and fetch — handle null from getById gracefully
- **VALIDATE**: Compiles

### Task 6: Create Per-Tool Detail Content Composables
- **ACTION**: Create 6 `@Composable` functions, one per tool type, in `feature/history/src/main/kotlin/.../detail/`
- **IMPLEMENT**:
  - **PingDetailContent**: Show host, sent/recv counts, min/avg/max ms
  - **LanScanDetailContent**: Show subnet, device count, parse `devicesJson` to list IPs
  - **PortScanDetailContent**: Show host, total scanned, parse `openPorts` JSON, show port list with service names
  - **DnsDetailContent**: Show query, record type, parse `resultsJson` to list records
  - **WhoisDetailContent**: Show query, scrollable raw response text
  - **IpInfoDetailContent**: Show IP, ISP, org, city, country, VPN status
- **IMPORTS**: `kotlinx.serialization.json.Json` for parsing JSON fields
- **GOTCHA**: JSON fields may be empty or `"[]"` — handle gracefully
- **VALIDATE**: Visual check in preview

### Task 7: Create HistoryDetailSheet
- **ACTION**: Create `HistoryDetailSheet.kt` — a `ModalBottomSheet` that renders the detail state
- **IMPLEMENT**: Header with tool icon + name + timestamp + primary label. Body dispatches to the appropriate detail content composable via `when (data) { is HistoryDetailData.Ping -> PingDetailContent(...) ... }`. Footer with "Re-run" `FilledTonalButton`.
- **MIRROR**: BOTTOM_SHEET_PATTERN from LanScan's HostDetailSheet
- **GOTCHA**: Sheet should be scrollable for long content (WHOIS raw response)
- **VALIDATE**: Visual check

### Task 8: Update HistoryScreen to Show Detail Sheet
- **ACTION**: Change click handler from navigating to tool to calling `viewModel.selectEntry(item)`. Show `HistoryDetailSheet` when `selectedDetail != null`. Add "Re-run" callback that dismisses sheet and navigates to tool with pre-populated input.
- **IMPLEMENT**: Replace `onNavigateToTool` click in `HistoryList` with `onItemClick = { viewModel.selectEntry(it) }`. Keep `onNavigateToTool` for the Re-run button inside the sheet.
- **GOTCHA**: Don't remove `onNavigateToTool` param — it's still used by the Re-run button
- **VALIDATE**: Full flow: tap entry → sheet opens → see results → tap Re-run → navigates to tool

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `getById` returns entry | Insert ping entry, query by id | Returns the entry | No |
| `getById` returns null for missing id | Query non-existent id | Returns null | Yes |
| `selectEntry` emits Loading then Loaded | Call selectEntry with valid item | State transitions: null → Loading → Loaded | No |
| `selectEntry` emits Error for deleted entry | Call selectEntry after entry deleted | State: Loading → Error | Yes |
| `dismissDetail` clears state | Call after selectEntry | State becomes null | No |
| JSON parsing handles empty arrays | `devicesJson = "[]"` | Renders "0 devices" | Yes |
| JSON parsing handles malformed JSON | `openPorts = "invalid"` | Graceful fallback, no crash | Yes |

### Edge Cases Checklist
- [ ] Entry deleted between list display and tap (getById returns null)
- [ ] Very long WHOIS response (scrollable in sheet)
- [ ] Empty device list in LAN Scan history
- [ ] DNS entry with multiple record types
- [ ] Rapid tap (debounce or ignore if already loading)

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
- [ ] Tap Ping history entry → bottom sheet shows saved avg/min/max ms
- [ ] Tap LAN Scan entry → bottom sheet shows device IP list
- [ ] Tap Port Scan entry → bottom sheet shows open ports
- [ ] Tap DNS entry → bottom sheet shows DNS records
- [ ] Tap WHOIS entry → bottom sheet shows raw response (scrollable)
- [ ] Tap IP Info entry → bottom sheet shows geo + ISP info
- [ ] Tap "Re-run" button → navigates to tool with input pre-populated
- [ ] Dismiss sheet by swiping down or tapping outside
- [ ] Back button from sheet returns to History list

---

## Acceptance Criteria
- [ ] Tapping any history entry opens a detail bottom sheet
- [ ] Detail sheet shows all saved data from the historical run
- [ ] "Re-run" button navigates to the tool with pre-populated input
- [ ] JSON-stored data (devices, ports, DNS records) is parsed and displayed correctly
- [ ] Loading and error states handled gracefully
- [ ] All validation commands pass

## Completion Checklist
- [ ] Code follows discovered patterns (bottom sheet, sealed state, DAO)
- [ ] Error handling matches codebase style
- [ ] Tests follow test patterns (Turbine for StateFlow, fakes over mocks)
- [ ] No hardcoded values
- [ ] Sheet is accessible (content descriptions, keyboard nav)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| JSON parsing failures on malformed historical data | Medium | Medium | Use `try/catch` with fallback display |
| Bottom sheet too tall on small screens | Medium | Low | Make content scrollable, cap initial height |
| feature:history module gaining too many core:data model dependencies | Low | Medium | Import only the entity classes needed |
| Breaking the existing click-to-navigate flow | Low | High | Keep onNavigateToTool as Re-run fallback |

## Notes
- This PR depends on the existing 6 tools having history. For the 5 tools added by `history-remaining-tools.plan.md`, the detail renderers should be added in that PR or a follow-up.
- The detail renderers are intentionally simple: display the stored data as-is. No re-fetching, no enrichment. The "Re-run" button is for when users want fresh data.
- Consider whether the detail sheet should replace the current direct-navigation behavior entirely, or offer both (tap = detail, long-press = direct re-run). Starting with tap = detail + Re-run button is the safer UX.
