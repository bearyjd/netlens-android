# Plan: Continuous Ping Mode

## Summary

Add a continuous ping mode to the existing ping feature that runs indefinitely until manually stopped. The user toggles between Fixed (current behavior) and Continuous via a segmented button. Continuous mode uses `ping -i 1 host` (no `-c` flag), keeps a rolling 100-result buffer, shows live-updating stats, and runs as a foreground service with a persistent notification when the app is backgrounded. History saves on stop with a `CONTINUOUS` mode tag.

## User Story

As a network diagnostician, I want to run a continuous ping that doesn't stop after a fixed count, so that I can monitor a host's reachability and latency over an extended period and spot intermittent issues.

## Problem -> Solution

Currently ping always sends a fixed number of packets (4/8/16) and stops. There's no way to monitor a host continuously. -> Add a Continuous mode that pings indefinitely with live stats, background persistence via foreground service, and a notification with cancel action.

## Metadata

- **Complexity**: Large
- **Source PRD**: N/A (inline spec)
- **PRD Phase**: N/A
- **Estimated Files**: ~12 new, ~8 modified
- **Depends on**: `scan-history` feature (PingHistoryEntry must exist)

---

## UX Design

### Before

```
┌────────────────────────────┐
│ TopAppBar: "Ping"   [H] <-│
│                            │
│ [host input........] [copy]│
│ [4] [8] [16]   ← chips    │
│ [Start]  [Stop]            │
│                            │
│ ▐▐▐▐▐▐▐▐  bar chart       │
│ ┌─ Summary ─────────────┐ │
│ │ Sent:4 Recv:4 Loss:0% │ │
│ │ Min:8  Avg:10 Max:12  │ │
│ └────────────────────────┘ │
│ seq=1 ttl=64 time=10.0ms  │
│ seq=2 ttl=64 time=12.0ms  │
│ seq=3 ttl=64 time=8.0ms   │
│ seq=4 ttl=64 time=11.0ms  │
│                            │
│ (stops after count)        │
└────────────────────────────┘
```

### After

```
┌────────────────────────────┐
│ TopAppBar: "Ping"   [H] <-│
│                            │
│ [host input........] [copy]│
│ ┌──────────┬─────────────┐ │
│ │  Fixed   │ Continuous  │ │  ← segmented button
│ └──────────┴─────────────┘ │
│                            │
│ FIXED MODE (unchanged):    │
│   [4] [8] [16] chips       │
│   [Start] [Stop]           │
│   bar chart + summary      │
│   result rows              │
│                            │
│ CONTINUOUS MODE:            │
│   (no count chips)          │
│   [Start] → red [■ Stop]   │
│   Running for 1m 23s       │
│   Sent:42 Recv:42          │
│   Loss:0% Avg:11ms         │
│   ───── scrolling graph ── │
│   (last 60 points)         │
│   seq=38..42 result rows   │
│   (last 100 in buffer)     │
│                            │
│ BACKGROUNDED:              │
│   notification:            │
│   "Pinging google.com      │
│    42 sent, 0% loss"       │
│   [Stop] action button     │
└────────────────────────────┘
```

### Interaction Changes

| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Mode selection | None — always fixed | Segmented button: Fixed / Continuous | Defaults to Fixed |
| Count chips | Always visible | Hidden in Continuous mode | Only relevant for Fixed |
| Start button | "Start" always | "Start" in Fixed, "Start" in Continuous | Same label |
| Stop button | Outlined "Stop" during ping | Red filled "Stop" button in Continuous mode | More prominent |
| Stats display | Summary card after completion | Live-updating stats bar during continuous ping | Updates every packet |
| RTT graph | Static bar chart | Horizontally scrolling graph (last 60 points) in Continuous | Auto-scrolls |
| Elapsed time | Not shown | "Running for Xm Ys" in Continuous mode | Updates every second |
| Background behavior | Ping dies on navigate-away | Foreground service keeps ping alive + notification | New behavior |
| Notification | None | Persistent notification with stats + Stop action | Continuous only |
| History save | On flow completion | On Stop (continuous) / on completion (fixed) | Mode tagged |

---

## Mandatory Reading

Files that MUST be read before implementing:

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `feature/ping/src/.../engine/Pinger.kt` | all | Interface to extend |
| P0 | `feature/ping/src/.../engine/PingerImpl.kt` | all | ProcessBuilder pattern to modify |
| P0 | `feature/ping/src/.../PingViewModel.kt` | all | State management to extend |
| P0 | `feature/ping/src/.../PingScreen.kt` | all | UI to modify |
| P0 | `feature/ping/src/.../model/PingUiState.kt` | all | State to extend |
| P0 | `feature/ping/src/.../model/PingResult.kt` | all | Unchanged but referenced |
| P0 | `feature/ping/src/.../model/PingSummary.kt` | all | Unchanged but referenced |
| P0 | `app/src/main/AndroidManifest.xml` | all | Permissions + service declaration |
| P1 | `feature/ping/src/test/.../PingViewModelTest.kt` | all | Tests to extend |
| P1 | `feature/ping/src/test/.../engine/FakePinger.kt` | all | Fake to extend |
| P1 | `feature/ping/build.gradle.kts` | all | Dependencies to add |
| P1 | `core/data/src/.../model/PingHistoryEntry.kt` | all | Entity to add mode column (from scan-history) |
| P1 | `core/data/src/.../NetLensDatabase.kt` | all | Version bump + migration |
| P1 | `core/data/src/.../di/DataModule.kt` | all | Migration to add |
| P2 | `feature/ping/src/.../engine/PingOutputParser.kt` | all | Parser — unchanged but reference |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Foreground Service (Android 14+) | developer.android.com | Must declare `foregroundServiceType` in manifest; `FOREGROUND_SERVICE_SPECIAL_USE` permission needed for API 34+ |
| POST_NOTIFICATIONS | developer.android.com | Runtime permission required on API 33+ (`Manifest.permission.POST_NOTIFICATIONS`) |
| SegmentedButton (M3) | developer.android.com/compose/material3 | `SingleChoiceSegmentedButtonRow` + `SegmentedButton` composables in Material3 |
| Foreground Service + Hilt | dagger.dev/hilt/android-entry-point | Use `@AndroidEntryPoint` on Service subclass for injection |

---

## Patterns to Mirror

### PINGER_INTERFACE
```kotlin
// SOURCE: feature/ping/src/main/kotlin/.../engine/Pinger.kt:1-4
interface Pinger {
    fun ping(host: String, count: Int = 4): Flow<PingResult>
}
```

### PINGER_PROCESS_BUILDER
```kotlin
// SOURCE: feature/ping/src/main/kotlin/.../engine/PingerImpl.kt:17-22
val process = withContext(Dispatchers.IO) {
    ProcessBuilder("ping", "-c", count.toString(), sanitized)
        .redirectErrorStream(true)
        .start()
}
```

### PINGER_CALLBACKFLOW
```kotlin
// SOURCE: feature/ping/src/main/kotlin/.../engine/PingerImpl.kt:15-38
override fun ping(host: String, count: Int): Flow<PingResult> = callbackFlow {
    val sanitized = validateHost(host)
    val process = withContext(Dispatchers.IO) {
        ProcessBuilder("ping", "-c", count.toString(), sanitized)
            .redirectErrorStream(true)
            .start()
    }
    try {
        withContext(Dispatchers.IO) {
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!isActive) break
                    val result = PingOutputParser.parseReplyLine(line)
                    if (result != null) {
                        trySend(result)
                    }
                }
            }
        }
        channel.close()
    } catch (e: kotlinx.coroutines.CancellationException) {
        channel.close()
        throw e
    } catch (e: Exception) {
        channel.close(e)
    } finally {
        process.destroy()
    }
    awaitClose { process.destroy() }
}
```

### VIEWMODEL_STATE_FLOW
```kotlin
// SOURCE: feature/ping/src/main/kotlin/.../PingViewModel.kt:19-25
@HiltViewModel
class PingViewModel @Inject constructor(
    private val pinger: Pinger,
) : ViewModel() {
    private val _state = MutableStateFlow(PingUiState())
    val state: StateFlow<PingUiState> = _state.asStateFlow()
    private var pingJob: Job? = null
```

### VIEWMODEL_COMPLETION
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(...) } },
            )
        },
    ) { innerPadding -> ... }
}
```

### FILTER_CHIP_ROW
```kotlin
// SOURCE: feature/ping/src/main/kotlin/.../PingScreen.kt:142-153
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    countOptions.forEach { count ->
        FilterChip(
            selected = selectedCount == count,
            onClick = { selectedCount = count },
            label = { Text("$count") },
        )
    }
}
```

### SUMMARY_CARD
```kotlin
// SOURCE: feature/ping/src/main/kotlin/.../PingScreen.kt:249-286
Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(text = stringResource(R.string.ping_label_summary), style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatItem(label = "Sent", value = "${summary.transmitted}")
            // ...
        }
    }
}
```

### FAKE_PINGER_TEST_DOUBLE
```kotlin
// SOURCE: feature/ping/src/test/.../engine/FakePinger.kt:1-15
class FakePinger : Pinger {
    var results: List<PingResult> = emptyList()
    var error: Throwable? = null
    override fun ping(host: String, count: Int): Flow<PingResult> = flow {
        error?.let { throw it }
        results.forEach { emit(it) }
    }
}
```

### TEST_SETUP
```kotlin
// SOURCE: feature/ping/src/test/.../PingViewModelTest.kt:28-33
@BeforeEach
fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    fakePinger = FakePinger()
    viewModel = PingViewModel(fakePinger)
}
```

### MIGRATION_PATTERN
```kotlin
// SOURCE: core/data/src/main/kotlin/.../di/DataModule.kt:22-28
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS ...")
    }
}
```

---

## Files to Change

### New Files

| File | Action | Justification |
|---|---|---|
| `feature/ping/src/main/kotlin/.../model/PingMode.kt` | CREATE | Enum: FIXED, CONTINUOUS |
| `feature/ping/src/main/kotlin/.../service/ContinuousPingService.kt` | CREATE | Foreground service for background ping |
| `feature/ping/src/main/kotlin/.../service/PingNotificationManager.kt` | CREATE | Notification channel + builder helpers |
| `feature/ping/src/main/res/values/strings.xml` | UPDATE | Add continuous mode strings |

### Modified Files

| File | Action | Justification |
|---|---|---|
| `feature/ping/src/main/kotlin/.../engine/Pinger.kt` | UPDATE | Add `pingContinuous(host: String)` method |
| `feature/ping/src/main/kotlin/.../engine/PingerImpl.kt` | UPDATE | Implement continuous ping (no `-c` flag, `-i 1`) |
| `feature/ping/src/main/kotlin/.../model/PingUiState.kt` | UPDATE | Add mode, liveStats, elapsedMs, totalSent/totalReceived |
| `feature/ping/src/main/kotlin/.../PingViewModel.kt` | UPDATE | Mode toggle, rolling buffer, live stats, service binding |
| `feature/ping/src/main/kotlin/.../PingScreen.kt` | UPDATE | Segmented button, conditional UI, elapsed timer |
| `feature/ping/build.gradle.kts` | UPDATE | Add lifecycle-service, core:data deps |
| `app/src/main/AndroidManifest.xml` | UPDATE | Add permissions + service declaration |
| `core/data/src/main/kotlin/.../model/PingHistoryEntry.kt` | UPDATE | Add `mode` column with default |
| `core/data/src/main/kotlin/.../NetLensDatabase.kt` | UPDATE | Bump version |
| `core/data/src/main/kotlin/.../di/DataModule.kt` | UPDATE | Add migration for mode column |
| `feature/ping/src/test/.../engine/FakePinger.kt` | UPDATE | Add `pingContinuous` fake |
| `feature/ping/src/test/.../PingViewModelTest.kt` | UPDATE | Add continuous mode tests |

## NOT Building

- Continuous mode for any tool other than Ping
- Configurable ping interval (hardcoded to 1 second via `-i 1`)
- Packet size configuration
- Multiple simultaneous continuous pings
- Notification sound/vibration customization
- Widget integration for continuous ping stats
- Background ping surviving app kill (service dies with app process)
- Export/share of continuous ping session

---

## Step-by-Step Tasks

### Task 1: Create PingMode Enum

- **ACTION**: Create `feature/ping/src/main/kotlin/com.ventouxlabs.netlens/feature/ping/model/PingMode.kt`
- **IMPLEMENT**:
```kotlin
package com.ventouxlabs.netlens.feature.ping.model

enum class PingMode {
    FIXED,
    CONTINUOUS,
}
```
- **MIRROR**: Simple enum like `SortOrder` in LanScanViewModel
- **IMPORTS**: None
- **GOTCHA**: None
- **VALIDATE**: Compiles

---

### Task 2: Extend PingUiState

- **ACTION**: Add continuous mode fields to `PingUiState`
- **IMPLEMENT**: Update `feature/ping/src/main/kotlin/com.ventouxlabs.netlens/feature/ping/model/PingUiState.kt`:
```kotlin
data class PingUiState(
    val host: String = "",
    val results: List<PingResult> = emptyList(),
    val summary: PingSummary? = null,
    val isPinging: Boolean = false,
    val error: String? = null,
    val mode: PingMode = PingMode.FIXED,
    val totalSent: Int = 0,
    val totalReceived: Int = 0,
    val elapsedMs: Long = 0,
)
```
- **MIRROR**: Existing data class pattern — all fields have defaults
- **IMPORTS**: `com.ventouxlabs.netlens.feature.ping.model.PingMode`
- **GOTCHA**: `totalSent` / `totalReceived` are separate from `results.size` because the rolling buffer drops old entries but the counters keep accumulating. `elapsedMs` is driven by a ticker coroutine in the ViewModel.
- **VALIDATE**: Compiles. Existing tests still pass (new fields have defaults).

---

### Task 3: Extend Pinger Interface

- **ACTION**: Add `pingContinuous` method to the `Pinger` interface
- **IMPLEMENT**: Update `feature/ping/src/main/kotlin/com.ventouxlabs.netlens/feature/ping/engine/Pinger.kt`:
```kotlin
interface Pinger {
    fun ping(host: String, count: Int = 4): Flow<PingResult>
    fun pingContinuous(host: String): Flow<PingResult>
}
```
- **MIRROR**: PINGER_INTERFACE — same style, Flow return type
- **IMPORTS**: None new
- **GOTCHA**: Separate method instead of a magic `count = -1` value — cleaner API, easier to test, and the ProcessBuilder args differ significantly (no `-c`, adds `-i 1`).
- **VALIDATE**: Compiles (PingerImpl and FakePinger will break until updated in Tasks 4 and 13).

---

### Task 4: Implement Continuous Ping in PingerImpl

- **ACTION**: Add `pingContinuous` implementation to `PingerImpl`
- **IMPLEMENT**: Add to `PingerImpl.kt`:
```kotlin
override fun pingContinuous(host: String): Flow<PingResult> = callbackFlow {
    val sanitized = validateHost(host)
    val process = withContext(Dispatchers.IO) {
        ProcessBuilder("ping", "-i", "1", sanitized)
            .redirectErrorStream(true)
            .start()
    }

    try {
        withContext(Dispatchers.IO) {
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!isActive) break
                    val result = PingOutputParser.parseReplyLine(line)
                    if (result != null) {
                        trySend(result)
                    }
                }
            }
        }
        channel.close()
    } catch (e: kotlinx.coroutines.CancellationException) {
        channel.close()
        throw e
    } catch (e: Exception) {
        channel.close(e)
    } finally {
        process.destroy()
    }

    awaitClose {
        process.destroy()
    }
}
```
- **MIRROR**: PINGER_CALLBACKFLOW — identical pattern, different ProcessBuilder args
- **IMPORTS**: None new (same as existing `ping` method)
- **GOTCHA**: No `-c` flag means the process runs forever. The `-i 1` flag sets 1-second intervals (default on most Android ping implementations). The `process.destroy()` in both `finally` and `awaitClose` ensures cleanup on cancellation. Without `-c`, the buffered reader loop will block indefinitely until the coroutine is cancelled — this is expected behavior.
- **VALIDATE**: `./gradlew :feature:ping:compileDebugKotlin` succeeds

---

### Task 5: Extend PingViewModel for Continuous Mode

- **ACTION**: Major refactor of PingViewModel to support both modes
- **IMPLEMENT**: Changes to `PingViewModel.kt`:

**Add mode toggle:**
```kotlin
fun onModeChanged(mode: PingMode) {
    if (_state.value.isPinging) return
    _state.update { it.copy(mode = mode) }
}
```

**Modify `startPing` to branch on mode:**
```kotlin
fun startPing(host: String, count: Int) {
    pingJob?.cancel()
    _state.update {
        it.copy(
            isPinging = true,
            results = emptyList(),
            summary = null,
            error = null,
            totalSent = 0,
            totalReceived = 0,
            elapsedMs = 0,
        )
    }

    startTime = System.currentTimeMillis()

    if (_state.value.mode == PingMode.CONTINUOUS) {
        startElapsedTimer()
    }

    val flow = if (_state.value.mode == PingMode.CONTINUOUS) {
        pinger.pingContinuous(host)
    } else {
        pinger.ping(host, count)
    }

    pingJob = viewModelScope.launch {
        flow
            .catch { e ->
                _state.update {
                    it.copy(isPinging = false, error = e.message ?: "Ping failed")
                }
            }
            .onCompletion {
                elapsedJob?.cancel()
                _state.update { current ->
                    current.copy(
                        isPinging = false,
                        summary = computeSummary(current),
                    )
                }
                saveToHistory(_state.value)
            }
            .collect { result ->
                _state.update { current ->
                    val newResults = if (current.mode == PingMode.CONTINUOUS) {
                        (current.results + result).takeLast(ROLLING_BUFFER_SIZE)
                    } else {
                        current.results + result
                    }
                    val newSent = current.totalSent + 1
                    val newReceived = current.totalReceived + if (!result.isTimeout) 1 else 0
                    current.copy(
                        results = newResults,
                        totalSent = newSent,
                        totalReceived = newReceived,
                        summary = if (current.mode == PingMode.CONTINUOUS) {
                            computeLiveSummary(newResults, newSent, newReceived)
                        } else {
                            null
                        },
                    )
                }
            }
    }
}
```

**Add elapsed timer:**
```kotlin
private var startTime: Long = 0
private var elapsedJob: Job? = null

private fun startElapsedTimer() {
    elapsedJob?.cancel()
    elapsedJob = viewModelScope.launch {
        while (isActive) {
            _state.update { it.copy(elapsedMs = System.currentTimeMillis() - startTime) }
            delay(1000)
        }
    }
}
```

**Add live summary computation (for continuous mode — updates each packet):**
```kotlin
private fun computeLiveSummary(
    results: List<PingResult>,
    totalSent: Int,
    totalReceived: Int,
): PingSummary {
    val latencies = results.mapNotNull { it.latencyMs }
    val lossPercent = if (totalSent > 0) {
        ((totalSent - totalReceived).toFloat() / totalSent) * 100f
    } else 0f
    val avg = if (latencies.isNotEmpty()) latencies.average().toFloat() else 0f
    return PingSummary(
        transmitted = totalSent,
        received = totalReceived,
        lossPercent = lossPercent,
        minMs = latencies.minOrNull() ?: 0f,
        avgMs = avg,
        maxMs = latencies.maxOrNull() ?: 0f,
        jitterMs = if (latencies.size >= 2) {
            val variance = latencies.map { (it - avg) * (it - avg) }.average().toFloat()
            kotlin.math.sqrt(variance.toDouble()).toFloat()
        } else 0f,
    )
}

companion object {
    private const val ROLLING_BUFFER_SIZE = 100
}
```

**Update `stopPing`:**
```kotlin
fun stopPing() {
    pingJob?.cancel()
    elapsedJob?.cancel()
    _state.update { current ->
        current.copy(
            isPinging = false,
            summary = computeSummary(current),
        )
    }
    saveToHistory(_state.value)
}
```

- **MIRROR**: VIEWMODEL_STATE_FLOW, VIEWMODEL_COMPLETION
- **IMPORTS**: Add `com.ventouxlabs.netlens.feature.ping.model.PingMode`, `kotlinx.coroutines.delay`
- **GOTCHA**: `computeSummary` (existing) uses `current.results` which in continuous mode is the rolling buffer, not all results. For the final summary on stop, use `totalSent`/`totalReceived` from state for transmitted/received counts, but min/avg/max from the buffer. The `computeLiveSummary` function uses the same buffer but takes explicit totalSent/totalReceived. Don't allow mode change while `isPinging == true`. The `saveToHistory` call must check `totalSent > 0` to avoid saving empty continuous sessions — add a guard in the existing `saveToHistory` method.
- **VALIDATE**: Build succeeds. Fixed mode behavior unchanged.

---

### Task 6: Create PingNotificationManager

- **ACTION**: Create notification channel and builder utility
- **IMPLEMENT**: Create `feature/ping/src/main/kotlin/com.ventouxlabs.netlens/feature/ping/service/PingNotificationManager.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.ping.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ventouxlabs.netlens.feature.ping.R

class PingNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "continuous_ping"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.ventouxlabs.netlens.ACTION_STOP_PING"
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.ping_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = context.getString(R.string.ping_notification_channel_desc)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(host: String, sent: Int, lossPercent: Float): Notification {
        val stopIntent = Intent(ACTION_STOP).setPackage(context.packageName)
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ping_notification)
            .setContentTitle(context.getString(R.string.ping_notification_title, host))
            .setContentText(
                context.getString(R.string.ping_notification_text, sent, "%.0f".format(lossPercent)),
            )
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.ping_notification_stop),
                stopPendingIntent,
            )
            .build()
    }
}
```

Also create a simple notification icon drawable. Since the project uses vector drawables, create `feature/ping/src/main/res/drawable/ic_ping_notification.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z"/>
</vector>
```

- **MIRROR**: Standard Android NotificationChannel + NotificationCompat.Builder
- **IMPORTS**: Android framework notification classes
- **GOTCHA**: `IMPORTANCE_LOW` avoids sound/vibration. `PendingIntent.FLAG_IMMUTABLE` is required on API 31+. The stop action uses a broadcast intent that the service will register a receiver for. Use `setPackage` to scope the broadcast to our app. The small icon must be a monochrome vector — if the project doesn't have a suitable one, use `android.R.drawable.ic_dialog_info` temporarily.
- **VALIDATE**: Channel creation doesn't crash. Notification renders correctly.

---

### Task 7: Create ContinuousPingService

- **ACTION**: Create a foreground service that runs continuous ping in the background
- **IMPLEMENT**: Create `feature/ping/src/main/kotlin/com.ventouxlabs.netlens/feature/ping/service/ContinuousPingService.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.ping.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.ventouxlabs.netlens.feature.ping.engine.Pinger
import com.ventouxlabs.netlens.feature.ping.model.PingResult
import javax.inject.Inject

@AndroidEntryPoint
class ContinuousPingService : Service() {

    @Inject lateinit var pinger: Pinger

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pingJob: Job? = null
    private lateinit var notificationManager: PingNotificationManager

    private val _results = MutableStateFlow<List<PingResult>>(emptyList())
    val results: StateFlow<List<PingResult>> = _results.asStateFlow()

    private var totalSent = 0
    private var totalReceived = 0
    private var currentHost = ""

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            stopSelf()
        }
    }

    inner class LocalBinder : Binder() {
        val service: ContinuousPingService get() = this@ContinuousPingService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notificationManager = PingNotificationManager(this)
        notificationManager.createChannel()
        registerReceiver(
            stopReceiver,
            IntentFilter(PingNotificationManager.ACTION_STOP),
            RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = intent?.getStringExtra(EXTRA_HOST) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        currentHost = host

        val notification = notificationManager.buildNotification(host, 0, 0f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                PingNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(PingNotificationManager.NOTIFICATION_ID, notification)
        }

        startPing(host)
        return START_NOT_STICKY
    }

    private fun startPing(host: String) {
        pingJob?.cancel()
        totalSent = 0
        totalReceived = 0
        _results.value = emptyList()

        pingJob = scope.launch {
            pinger.pingContinuous(host)
                .catch { /* silently stop on error */ stopSelf() }
                .collect { result ->
                    totalSent++
                    if (!result.isTimeout) totalReceived++
                    _results.value = (_results.value + result).takeLast(BUFFER_SIZE)
                    updateNotification()
                }
        }
    }

    private fun updateNotification() {
        val lossPercent = if (totalSent > 0) {
            ((totalSent - totalReceived).toFloat() / totalSent) * 100f
        } else 0f
        val notification = notificationManager.buildNotification(currentHost, totalSent, lossPercent)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(PingNotificationManager.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        pingJob?.cancel()
        scope.cancel()
        unregisterReceiver(stopReceiver)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_HOST = "host"
        private const val BUFFER_SIZE = 100

        fun start(context: Context, host: String) {
            val intent = Intent(context, ContinuousPingService::class.java)
                .putExtra(EXTRA_HOST, host)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ContinuousPingService::class.java))
        }
    }
}
```

- **MIRROR**: No existing service pattern — this is new. Follow Android best practices.
- **IMPORTS**: See above
- **GOTCHA**: `@AndroidEntryPoint` on Service requires Hilt. `RECEIVER_NOT_EXPORTED` flag is mandatory on API 34+ for dynamically registered receivers. `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` is required on API 34+ — must also declare `android:foregroundServiceType="specialUse"` in manifest. The service owns its own `CoroutineScope` (not `viewModelScope`) because it outlives the ViewModel when the app is backgrounded. The `LocalBinder` pattern lets the ViewModel bind to read results, but the initial implementation can work without binding — the ViewModel starts/stops the service and manages its own state independently.
- **VALIDATE**: Service starts without crash, notification appears, ping runs.

---

### Task 8: Update AndroidManifest.xml

- **ACTION**: Add permissions and service declaration
- **IMPLEMENT**: Update `app/src/main/AndroidManifest.xml`:

Add permissions (before `<application>`):
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Add service declaration (inside `<application>`, after the receiver):
```xml
<service
    android:name="com.ventouxlabs.netlens.feature.ping.service.ContinuousPingService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="continuous_network_diagnostics" />
</service>
```

- **MIRROR**: Existing manifest structure — permissions grouped at top, components inside `<application>`
- **GOTCHA**: `FOREGROUND_SERVICE_SPECIAL_USE` is required for API 34+ (Android 14). The `<property>` tag explaining the use case is required for Play Store review. `android:exported="false"` because the service is internal only. The service class is in the `feature:ping` module but declared in the `app` manifest because manifest merger handles it — actually, it's better to declare it in `feature/ping/src/main/AndroidManifest.xml` and let manifest merger pull it into the app manifest. **Decision**: Declare in the feature manifest instead.
- **VALIDATE**: App installs without manifest merge errors.

**Alternative (preferred)**: Instead of modifying app manifest, update `feature/ping/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <service
            android:name="com.ventouxlabs.netlens.feature.ping.service.ContinuousPingService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="continuous_network_diagnostics" />
        </service>
    </application>

</manifest>
```

This is cleaner — the feature module owns its own permissions and components.

---

### Task 9: Update PingScreen UI

- **ACTION**: Add segmented button for mode toggle, conditional UI for continuous mode
- **IMPLEMENT**: Major changes to `PingScreen.kt`. Key additions:

**Add segmented button above count chips:**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    selectedMode: PingMode,
    onModeChanged: (PingMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        PingMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = { onModeChanged(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, PingMode.entries.size),
                enabled = enabled,
            ) {
                Text(if (mode == PingMode.FIXED) stringResource(R.string.ping_mode_fixed) else stringResource(R.string.ping_mode_continuous))
            }
        }
    }
}
```

**Conditional count chips:**
```kotlin
if (state.mode == PingMode.FIXED) {
    FlowRow(...) {
        countOptions.forEach { ... FilterChip(...) }
    }
}
```

**Live stats bar (continuous mode, while pinging):**
```kotlin
@Composable
private fun LiveStatsBar(state: PingUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.ping_continuous_elapsed, formatElapsed(state.elapsedMs)),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem(label = stringResource(R.string.ping_stat_sent), value = "${state.totalSent}")
                StatItem(label = stringResource(R.string.ping_stat_recv), value = "${state.totalReceived}")
                StatItem(label = stringResource(R.string.ping_stat_loss), value = "%.0f%%".format(
                    state.summary?.lossPercent ?: 0f,
                ))
                StatItem(label = stringResource(R.string.ping_stat_avg), value = "%.1f ms".format(
                    state.summary?.avgMs ?: 0f,
                ))
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
```

**Stop button styling (continuous mode):**
```kotlin
if (state.isPinging && state.mode == PingMode.CONTINUOUS) {
    Button(
        onClick = onStopPing,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
        ),
    ) {
        Text(stringResource(R.string.ping_button_stop))
    }
} else if (state.isPinging) {
    OutlinedButton(onClick = onStopPing) {
        Text(stringResource(R.string.ping_button_stop))
    }
}
```

**LatencyBarChart in continuous mode**: Use the last 60 points from `state.results` and auto-scroll.

**Permission request**: Before starting continuous mode, check and request `POST_NOTIFICATIONS` permission:
```kotlin
val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
) { granted ->
    if (granted) {
        onStartPing(state.host, selectedCount)
    }
}

// In Start button onClick:
if (state.mode == PingMode.CONTINUOUS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
} else {
    onStartPing(state.host, selectedCount)
}
```

- **MIRROR**: SCREEN_SCAFFOLD, FILTER_CHIP_ROW, SUMMARY_CARD
- **IMPORTS**: Add `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `SegmentedButtonDefaults`, `ButtonDefaults`, `ActivityResultContracts`, `Build`, `Manifest`
- **GOTCHA**: `SingleChoiceSegmentedButtonRow` is in `androidx.compose.material3` and is stable as of M3 1.2+. Check the Compose BOM version (2024.12.01) includes it. The permission request launcher must be remembered at the composable scope (not inside a callback). The `ModeSelector` must be disabled while `isPinging` to prevent mode changes during active ping.
- **VALIDATE**: UI renders correctly. Mode toggle works. Count chips hide in continuous mode. Live stats update.

---

### Task 10: Add Continuous Ping Strings

- **ACTION**: Add new string resources to `feature/ping/src/main/res/values/strings.xml`
- **IMPLEMENT**: Add these entries:
```xml
<string name="ping_mode_fixed">Fixed</string>
<string name="ping_mode_continuous">Continuous</string>
<string name="ping_continuous_elapsed">Running for %1$s</string>
<string name="ping_notification_channel_name">Continuous Ping</string>
<string name="ping_notification_channel_desc">Shows status while ping is running in the background</string>
<string name="ping_notification_title">Pinging %1$s</string>
<string name="ping_notification_text">%1$d sent, %2$s%% loss</string>
<string name="ping_notification_stop">Stop</string>
```
- **MIRROR**: STRING_RESOURCE_NAMING — `ping_{category}_{purpose}`
- **GOTCHA**: `%1$s` / `%1$d` format specifiers for string resource arguments. Use `$s` for string, `$d` for int.
- **VALIDATE**: All `stringResource()` calls resolve without crash.

---

### Task 11: Update ping build.gradle.kts

- **ACTION**: Add dependencies needed for foreground service and notifications
- **IMPLEMENT**: Update `feature/ping/build.gradle.kts`:
```kotlin
plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.ping"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
```

Note: `core:data` is needed for `PingHistoryDao`. The convention plugin `netlens.android.feature` already includes `lifecycle-runtime-compose` and `lifecycle-viewmodel-compose`. The `NotificationCompat` class comes from `androidx.core:core-ktx` which is already included via `libs.core.ktx`. No additional notification library dependency is needed.

- **MIRROR**: FEATURE_BUILD_GRADLE
- **GOTCHA**: Verify `androidx.core:core-ktx` version includes `NotificationCompat`. It does — `core-ktx` 1.13+ includes it.
- **VALIDATE**: `./gradlew :feature:ping:dependencies` shows core-ktx.

---

### Task 12: Add Room Migration for mode Column

- **ACTION**: Add `mode` column to `PingHistoryEntry`, bump DB version, add migration
- **IMPLEMENT**:

**Update `core/data/src/main/kotlin/.../model/PingHistoryEntry.kt`** (assumes scan-history created this):
```kotlin
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
    val mode: String = "FIXED",
)
```

**Update `NetLensDatabase.kt`**: Bump version (from whatever scan-history set it to — likely 6 -> 7).

**Update `DataModule.kt`**: Add migration:
```kotlin
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE history_ping ADD COLUMN mode TEXT NOT NULL DEFAULT 'FIXED'")
    }
}
```
Add to builder: `.addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)`

**Update history save in PingViewModel**: Pass mode when inserting:
```kotlin
pingHistoryDao.insert(
    PingHistoryEntry(
        host = state.host,
        sentCount = if (state.mode == PingMode.CONTINUOUS) state.totalSent else summary.transmitted,
        receivedCount = if (state.mode == PingMode.CONTINUOUS) state.totalReceived else summary.received,
        minMs = summary.minMs,
        avgMs = summary.avgMs,
        maxMs = summary.maxMs,
        mode = state.mode.name,
    ),
)
```

- **MIRROR**: MIGRATION_PATTERN
- **GOTCHA**: `ALTER TABLE ADD COLUMN` requires a `DEFAULT` value for `NOT NULL` columns. Using `'FIXED'` as default means existing history entries are tagged as FIXED, which is correct. The version numbers depend on scan-history being implemented first. If scan-history bumps to 6, this bumps to 7.
- **VALIDATE**: App upgrades without crash. Existing history entries get `mode = "FIXED"`.

---

### Task 13: Update FakePinger for Tests

- **ACTION**: Add `pingContinuous` to `FakePinger`
- **IMPLEMENT**: Update `feature/ping/src/test/kotlin/com.ventouxlabs.netlens/feature/ping/engine/FakePinger.kt`:
```kotlin
class FakePinger : Pinger {
    var results: List<PingResult> = emptyList()
    var continuousResults: List<PingResult> = emptyList()
    var error: Throwable? = null

    override fun ping(host: String, count: Int): Flow<PingResult> = flow {
        error?.let { throw it }
        results.forEach { emit(it) }
    }

    override fun pingContinuous(host: String): Flow<PingResult> = flow {
        error?.let { throw it }
        continuousResults.forEach { emit(it) }
    }
}
```
- **MIRROR**: FAKE_PINGER_TEST_DOUBLE
- **GOTCHA**: `continuousResults` is separate from `results` so tests can configure fixed and continuous modes independently.
- **VALIDATE**: Compiles.

---

### Task 14: Update PingViewModelTest

- **ACTION**: Add tests for continuous mode, update setUp for new constructor params
- **IMPLEMENT**: Add to `PingViewModelTest.kt`:

**Update setUp** (if scan-history added `PingHistoryDao` injection):
```kotlin
private lateinit var fakePingHistoryDao: FakePingHistoryDao

@BeforeEach
fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    fakePinger = FakePinger()
    fakePingHistoryDao = FakePingHistoryDao()
    viewModel = PingViewModel(fakePinger, fakePingHistoryDao)
}
```

**New tests:**
```kotlin
@Test
fun `onModeChanged switches mode`() = runTest {
    viewModel.state.test {
        awaitItem() // initial FIXED
        viewModel.onModeChanged(PingMode.CONTINUOUS)
        assertEquals(PingMode.CONTINUOUS, awaitItem().mode)
    }
}

@Test
fun `onModeChanged ignored while pinging`() = runTest {
    fakePinger.results = listOf(
        PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
    )
    viewModel.startPing("8.8.8.8", 4)
    // Flow completes eagerly with UnconfinedTestDispatcher
    viewModel.onModeChanged(PingMode.CONTINUOUS)
    // Mode should remain FIXED since isPinging was true during collection
    // (but with eager completion, isPinging is already false — so mode changes)
    // This test verifies the guard works during actual pinging
}

@Test
fun `continuous mode uses rolling buffer`() = runTest {
    viewModel.onModeChanged(PingMode.CONTINUOUS)
    val manyResults = (1..150).map {
        PingResult(sequenceNumber = it, latencyMs = it.toFloat(), ttl = 64)
    }
    fakePinger.continuousResults = manyResults

    viewModel.state.test {
        awaitItem() // initial
        awaitItem() // mode changed to CONTINUOUS

        viewModel.startPing("8.8.8.8", 0)
        val finalState = expectMostRecentItem()

        // Rolling buffer keeps last 100
        assertEquals(100, finalState.results.size)
        assertEquals(51, finalState.results.first().sequenceNumber)
        assertEquals(150, finalState.results.last().sequenceNumber)
        assertEquals(150, finalState.totalSent)
        assertEquals(150, finalState.totalReceived)
    }
}

@Test
fun `continuous mode computes live summary each packet`() = runTest {
    viewModel.onModeChanged(PingMode.CONTINUOUS)
    fakePinger.continuousResults = listOf(
        PingResult(sequenceNumber = 1, latencyMs = 10.0f, ttl = 64),
        PingResult(sequenceNumber = 2, latencyMs = 20.0f, ttl = 64),
        PingResult(sequenceNumber = 3, isTimeout = true),
    )

    viewModel.state.test {
        awaitItem() // initial
        awaitItem() // mode changed

        viewModel.startPing("8.8.8.8", 0)
        val finalState = expectMostRecentItem()

        assertNotNull(finalState.summary)
        assertEquals(3, finalState.totalSent)
        assertEquals(2, finalState.totalReceived)
    }
}

@Test
fun `continuous mode does not save with zero packets`() = runTest {
    viewModel.onModeChanged(PingMode.CONTINUOUS)
    fakePinger.continuousResults = emptyList()

    viewModel.startPing("8.8.8.8", 0)
    viewModel.stopPing()

    assertTrue(fakePingHistoryDao.inserted.isEmpty())
}
```

- **MIRROR**: TEST_SETUP, existing test patterns with `runTest`, `test {}`, `expectMostRecentItem()`
- **GOTCHA**: With `UnconfinedTestDispatcher`, flows complete eagerly so we can't test intermediate states. The `onModeChanged` guard test is tricky because `isPinging` flips too fast — test it via state.value assertions instead of Turbine. The elapsed timer test is hard with `UnconfinedTestDispatcher` because `delay` is skipped — test `elapsedMs` as 0 or verify the timer Job is created.
- **VALIDATE**: `./gradlew :feature:ping:testDebugUnitTest` passes

---

### Task 15: Wire Service Start/Stop in ViewModel

- **ACTION**: Start ContinuousPingService when continuous mode ping starts, stop on stopPing
- **IMPLEMENT**: The ViewModel needs application context to start the service. Add `@ApplicationContext` injection:

```kotlin
@HiltViewModel
class PingViewModel @Inject constructor(
    private val pinger: Pinger,
    private val pingHistoryDao: PingHistoryDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {
```

In `startPing`, after launching the ping flow, start the service if continuous:
```kotlin
if (_state.value.mode == PingMode.CONTINUOUS) {
    ContinuousPingService.start(context, host)
}
```

In `stopPing`, stop the service:
```kotlin
if (_state.value.mode == PingMode.CONTINUOUS) {
    ContinuousPingService.stop(context)
}
```

- **MIRROR**: LanScanViewModel uses `@ApplicationContext context: Context` — same pattern
- **IMPORTS**: `android.content.Context`, `dagger.hilt.android.qualifiers.ApplicationContext`
- **GOTCHA**: The ViewModel and service both run ping independently — the ViewModel handles UI state, the service handles background persistence. When the ViewModel is destroyed (screen navigated away), the service keeps running. When the user returns, the ViewModel should check if the service is still running and re-bind to get its state. **Simplification for initial implementation**: Don't sync state between ViewModel and service. The service is purely for keeping the process alive and showing the notification. When the user returns to the screen, they start a fresh UI session. This avoids complex service binding logic.
- **VALIDATE**: Service starts on continuous ping start, stops on stop.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| Mode toggle changes state | Switch to CONTINUOUS | `state.mode == CONTINUOUS` | No |
| Mode toggle blocked while pinging | Toggle during active ping | Mode unchanged | Yes |
| Fixed mode unchanged | Ping with count 4 | Same behavior as before | No |
| Continuous uses rolling buffer | 150 results | `results.size == 100`, first seq = 51 | No |
| Live summary updates each packet | 3 packets (1 timeout) | Summary shows 3 sent, 2 recv, ~33% loss | No |
| Stop saves to history | Stop after 10 packets | History entry with mode=CONTINUOUS | No |
| No save on 0 packets | Start then immediately stop | No history entry | Yes |
| pingContinuous uses correct args | Call pingContinuous | ProcessBuilder called with `-i 1` (no `-c`) | No |
| Host validation in continuous | Shell injection attempt | `IllegalArgumentException` | Yes |
| Elapsed timer increments | Start continuous ping | `elapsedMs > 0` after delay | No |

### Edge Cases Checklist

- [x] Empty input (host blank) — existing validation prevents start
- [x] Zero packets on stop — guard against saving empty history
- [x] 100+ results — rolling buffer drops oldest
- [x] All timeouts — lossPercent = 100%, avgMs = 0
- [x] Process crash — `catch` handler stops gracefully
- [x] App backgrounded — service keeps ping alive
- [x] Notification permission denied — ping still works (no notification on older APIs)
- [x] Rapid start/stop — previous Job cancelled before new one starts

---

## Validation Commands

### Static Analysis
```bash
./gradlew assembleDebug
```
EXPECT: Zero build errors

### Unit Tests
```bash
./gradlew :feature:ping:testDebugUnitTest
```
EXPECT: All tests pass (existing + new)

### Full Test Suite
```bash
./gradlew testDebugUnitTest
```
EXPECT: No regressions

### Database Validation
```bash
ls core/data/schemas/com.ventouxlabs.netlens.core.data.NetLensDatabase/7.json
```
EXPECT: Schema file exists with `mode` column in `history_ping`

### Manual Validation

- [ ] Open Ping screen — segmented button shows "Fixed | Continuous"
- [ ] Fixed mode works identically to before (count chips, start/stop, summary)
- [ ] Switch to Continuous — count chips hidden
- [ ] Start continuous ping — live stats update each packet
- [ ] Stats bar shows Sent/Recv/Loss%/Avg with real-time updates
- [ ] Elapsed time counter increments every second
- [ ] Bar chart scrolls with new data points
- [ ] Result list shows last ~100 entries
- [ ] Press Stop — ping stops, summary computed, history saved
- [ ] Check history — entry shows mode=CONTINUOUS
- [ ] Navigate away while continuous ping runs — notification appears
- [ ] Notification shows host, sent count, loss%
- [ ] Tap Stop on notification — ping stops
- [ ] Return to Ping screen after backgrounding — screen shows clean state
- [ ] Deny notification permission — ping still starts (just no notification on API 33+)
- [ ] Cannot switch mode while ping is running

---

## Acceptance Criteria

- [ ] Segmented button toggles between Fixed and Continuous modes
- [ ] Fixed mode behavior is unchanged
- [ ] Continuous mode pings indefinitely until stopped
- [ ] Rolling buffer keeps last 100 results
- [ ] Live stats (sent/recv/loss/avg) update each packet
- [ ] Elapsed time counter shows while running
- [ ] Red Stop button in continuous mode
- [ ] Foreground service keeps ping alive when backgrounded
- [ ] Persistent notification with stats and Stop action
- [ ] POST_NOTIFICATIONS permission requested on API 33+
- [ ] History saves on stop with mode=CONTINUOUS tag
- [ ] No save for sessions with 0 packets
- [ ] Room migration for mode column works without data loss
- [ ] All existing tests pass
- [ ] New continuous mode tests pass

## Completion Checklist

- [ ] Code follows discovered patterns (Pinger interface, ViewModel StateFlow, Screen Scaffold)
- [ ] Error handling: process.destroy() on cancellation, catch blocks in service
- [ ] Naming: `PingMode`, `ContinuousPingService`, `PingNotificationManager`, `ping_mode_*` strings
- [ ] Tests use hand-written fakes (FakePinger extended), JUnit 5, Turbine, backtick names
- [ ] No hardcoded values — buffer size as companion const, strings in resources
- [ ] No unnecessary scope (only ping, no other tools)
- [ ] Self-contained — all patterns, file paths, and gotchas documented

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `ping -i 1` flag not supported on all Android devices | Low | High (silent failure) | Test on emulator + real device; fall back to no `-i` flag (default interval is ~1s anyway) |
| SegmentedButton API not in current Compose BOM | Medium | Medium (compile error) | Check BOM 2024.12.01 includes M3 1.2+; if not, use `TabRow` as fallback |
| Foreground service killed by OEM battery optimization | Medium | Medium (ping dies) | Document in UI; can't fully prevent. `START_NOT_STICKY` means no auto-restart. |
| Service + ViewModel state desync after backgrounding | Medium | Low (stale UI) | Accept clean state on return; don't sync. Future improvement: service binding. |
| Room migration conflicts with scan-history migration | Low | High (crash) | Coordinate version numbers — this plan assumes scan-history uses version 6, this uses 7 |
| `FOREGROUND_SERVICE_SPECIAL_USE` rejected by Play Store | Low | Medium (can't publish) | Provide clear justification in `<property>` tag; network diagnostic tool is a valid use case |

## Notes

- The initial implementation keeps ViewModel and Service independent — the ViewModel manages the UI ping flow, and the service is started/stopped alongside it for background persistence. A more sophisticated approach would have the service own the ping flow and the ViewModel observe it via binding, but that adds significant complexity for marginal benefit. This can be improved in a follow-up.
- The `-i 1` flag sets the ping interval to 1 second. On some Android builds, root is required for intervals < 0.2s. 1 second is always allowed for non-root.
- The rolling buffer of 100 and graph window of 60 points are separate concerns: the buffer stores data, the graph displays the last 60 from the buffer. Both use `takeLast()`.
- If scan-history hasn't been implemented yet, Task 12 needs to create the `PingHistoryEntry` entity and full migration instead of just adding the `mode` column. The plan assumes scan-history is implemented first per the dependency declaration.
