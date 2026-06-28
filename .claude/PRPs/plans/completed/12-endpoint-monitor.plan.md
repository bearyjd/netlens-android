# Plan: Endpoint Monitor

## Summary

Implement an endpoint uptime monitor that periodically checks HTTP endpoints via WorkManager, persists status history in Room, and sends notifications on state changes (up→down or down→up).

## User Story

As a user, I want to monitor the availability of important URLs and get notified when they go down or come back up, so that I can respond to outages quickly.

## Metadata

- **Complexity**: Large
- **Branch**: feat/endpoint-monitor
- **PR**: PR-12
- **Depends On**: scaffold
- **Estimated Files**: 15
- **New Modules**: feature/uptime (new)

## Patterns to Mirror

### WORKMANAGER
// SOURCE: widget/build.gradle.kts — already uses work-runtime dep
// Pattern: CoroutineWorker + PeriodicWorkRequest

### ROOM_ENTITY
// Standard entity + DAO pattern

### NOTIFICATION
// Standard NotificationManager + NotificationChannel pattern

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `feature/uptime/build.gradle.kts` | CREATE | netlens.android.feature, deps: :core:network, :core:data, work-runtime, Ktor client libs |
| `settings.gradle.kts` | UPDATE | Add `include(":feature:uptime")` |
| `app/build.gradle.kts` | UPDATE | Add implementation project dep |
| `core/data/src/main/kotlin/com.ventouxlabs.netlens/core/data/entity/MonitoredEndpointEntity.kt` | CREATE | @Entity: id (auto PK), name, url, intervalMinutes, lastStatus (UP/DOWN/UNKNOWN), lastCheckedAt, lastStatusChangeAt, notifyOnChange |
| `core/data/src/main/kotlin/com.ventouxlabs.netlens/core/data/entity/EndpointCheckEntity.kt` | CREATE | @Entity: id (auto PK), endpointId (FK), timestamp, statusCode, latencyMs, isUp, errorMessage |
| `core/data/src/main/kotlin/com.ventouxlabs.netlens/core/data/dao/MonitoredEndpointDao.kt` | CREATE | @Dao: getAll (Flow), getById, insert, update, delete |
| `core/data/src/main/kotlin/com.ventouxlabs.netlens/core/data/dao/EndpointCheckDao.kt` | CREATE | @Dao: getByEndpoint (Flow, ordered by timestamp DESC, limit 100), insert, deleteOlderThan |
| `core/data/src/main/kotlin/com.ventouxlabs.netlens/core/data/NetLensDatabase.kt` | UPDATE | Add both entities, bump version, add abstract DAOs |
| `core/data/src/main/kotlin/com.ventouxlabs.netlens/core/data/di/DataModule.kt` | UPDATE | @Provides both DAOs |
| `feature/uptime/src/main/kotlin/com.ventouxlabs.netlens/feature/uptime/worker/EndpointCheckWorker.kt` | CREATE | CoroutineWorker. Query all endpoints from DAO. For each: HTTP HEAD via Ktor, record status code + latency. If status changed from last, send notification. Insert EndpointCheckEntity. Update MonitoredEndpointEntity lastStatus/lastCheckedAt. |
| `feature/uptime/src/main/kotlin/com.ventouxlabs.netlens/feature/uptime/worker/EndpointMonitorScheduler.kt` | CREATE | Schedules PeriodicWorkRequest per endpoint interval. Uses unique work names per endpoint ID. |
| `feature/uptime/src/main/kotlin/com.ventouxlabs.netlens/feature/uptime/notification/UptimeNotificationHelper.kt` | CREATE | Creates NotificationChannel, builds status change notifications |
| `feature/uptime/src/main/kotlin/com.ventouxlabs.netlens/feature/uptime/model/UptimeUiState.kt` | CREATE | data class: endpoints list with last status, isLoading, showAddDialog, editingEndpoint |
| `feature/uptime/src/main/kotlin/com.ventouxlabs.netlens/feature/uptime/di/UptimeModule.kt` | CREATE | @Module providing scheduler |
| `feature/uptime/src/main/kotlin/com.ventouxlabs.netlens/feature/uptime/UptimeViewModel.kt` | CREATE | @HiltViewModel, CRUD endpoints, schedule/cancel workers, observe status |
| `feature/uptime/src/main/kotlin/com.ventouxlabs.netlens/feature/uptime/UptimeScreen.kt` | CREATE | LazyColumn of endpoint cards (name, URL, status indicator green/red/gray, last checked time, latency). FAB to add. AlertDialog for add/edit (name, URL, interval dropdown, notify toggle). Tap card → detail view with check history. |
| `app/src/main/AndroidManifest.xml` | UPDATE | Add POST_NOTIFICATIONS permission (API 33+) |
| `app/src/main/kotlin/com.ventouxlabs.netlens/navigation/NetLensNavHost.kt` | UPDATE | Add composable route for uptime |

## Step-by-Step Tasks

### Task 1: Create feature module
- **ACTION**: Create build.gradle.kts with work-runtime, Ktor client deps, :core:data, :core:network. Update settings + app build.
- **VALIDATE**: `./gradlew :feature:uptime:compileDebugKotlin`

### Task 2: Create Room entities + DAOs
- **ACTION**: `MonitoredEndpointEntity` and `EndpointCheckEntity` as specified. DAOs with Flow queries.
- **VALIDATE**: Compiles

### Task 3: Update NetLensDatabase + DataModule
- **ACTION**: Add both entities, bump version, add DAO getters + providers
- **VALIDATE**: Compiles

### Task 4: Create EndpointCheckWorker
- **ACTION**: `class EndpointCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)`. Get endpoint ID from `inputData`. Query endpoint from DAO. Execute HTTP HEAD with Ktor (timeout 10s). Record result. Compare with lastStatus. If changed and notifyOnChange, send notification. Return `Result.success()`.
- **VALIDATE**: Unit test with mock DAO + MockEngine

### Task 5: Create EndpointMonitorScheduler
- **ACTION**: `@Singleton class EndpointMonitorScheduler @Inject constructor(context: Context)`. `fun schedule(endpoint: MonitoredEndpointEntity)`: builds `PeriodicWorkRequestBuilder<EndpointCheckWorker>(interval, TimeUnit.MINUTES)` with input data containing endpoint ID, constraints (network required). Enqueues with `WorkManager.getInstance().enqueueUniquePeriodicWork("endpoint_${id}", REPLACE, request)`. `fun cancel(endpointId: Long)`: cancels unique work.
- **VALIDATE**: Compiles

### Task 6: Create UptimeNotificationHelper
- **ACTION**: Create notification channel "endpoint_monitor" on init. `fun notifyStatusChange(endpointName: String, isUp: Boolean)`: builds notification with appropriate icon/color (green check for UP, red X for DOWN), auto-cancel, pending intent to open app.
- **VALIDATE**: Compiles

### Task 7: Create DI module
- **ACTION**: `UptimeModule` providing scheduler + notification helper
- **VALIDATE**: Compiles

### Task 8: Create UptimeViewModel
- **ACTION**: `@HiltViewModel`, `fun addEndpoint(name, url, interval, notify)` → insert + schedule. `fun deleteEndpoint(id)` → delete + cancel worker. `fun toggleNotify(id)`. Observes all endpoints via Flow.
- **VALIDATE**: Unit test with Turbine

### Task 9: Create UptimeScreen
- **ACTION**: LazyColumn of endpoint cards: status dot (green=UP, red=DOWN, gray=UNKNOWN), name, URL truncated, "last checked: X ago", latency. Swipe-to-delete. FAB opens add dialog: name, URL, interval picker (5/15/30/60 min), notify toggle. Tap card for detail with check history list.
- **VALIDATE**: Preview renders

### Task 10: Update AndroidManifest
- **ACTION**: Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` for API 33+
- **VALIDATE**: Compiles

### Task 11: Wire navigation
- **ACTION**: Add route to NavHost
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - `EndpointCheckWorker` — mock DAO + Ktor MockEngine: UP/DOWN detection, notification trigger
  - `UptimeViewModel` — Turbine: CRUD, schedule/cancel
- **Integration tests for**:
  - DAOs — Room in-memory: insert endpoint, insert checks, query history

## Validation
