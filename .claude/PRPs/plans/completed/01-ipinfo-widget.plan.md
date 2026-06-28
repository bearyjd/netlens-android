# Plan: IP Info + Widget

## Summary

Implement the IP Info feature screen that fetches public IP information from ip-api.com, displays it with pull-to-refresh, and powers a Glance home screen widget showing public IP, ISP, and VPN status. The widget uses WorkManager for background refresh and DataStore for state persistence.

## User Story

As a user, I want to see my public IP address, ISP, location, and VPN status at a glance, so that I can quickly verify my network identity both in-app and from my home screen widget.

## Metadata

- **Complexity**: Large
- **Branch**: feat/ipinfo-widget
- **PR**: PR-01
- **Depends On**: scaffold
- **Estimated Files**: 14
- **New Modules**: none (feature/ipinfo and widget already exist)

## Patterns to Mirror

### FEATURE_MODULE
// SOURCE: feature/ipinfo/build.gradle.kts
- Already has `id("netlens.android.feature")`, kotlin-serialization plugin, Ktor deps

### HILT_VIEWMODEL
// Pattern from kotlin rules: @HiltViewModel + @Inject constructor + MutableStateFlow + asStateFlow

### REPOSITORY
// Pattern: interface + impl with @Inject, suspend fun returning Result<T>

### DI_MODULE
// SOURCE: core/network/src/main/kotlin/com.ventouxlabs.netlens/core/network/di/NetworkModule.kt
- @Module @InstallIn(SingletonComponent::class) + @Binds @Singleton

### NAVIGATION
// SOURCE: app/src/main/kotlin/com.ventouxlabs.netlens/navigation/NetLensNavHost.kt
- Replace PlaceholderScreen for TopLevelDestination.IpInfo with IpInfoScreen()

### WIDGET
// SOURCE: widget/src/main/kotlin/com.ventouxlabs.netlens/widget/NetLensWidget.kt
- Extend provideGlance to read from DataStore via GlanceStateDefinition
- SOURCE: widget/build.gradle.kts — already depends on :feature:ipinfo, work-runtime, datastore-preferences

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `feature/ipinfo/src/main/kotlin/com.ventouxlabs.netlens/feature/ipinfo/model/IpApiResponse.kt` | CREATE | @Serializable data class with fields: query, isp, org, as_, country, regionName, city, lat, lon, proxy, hosting |
| `feature/ipinfo/src/main/kotlin/com.ventouxlabs.netlens/feature/ipinfo/model/IpInfoUiState.kt` | CREATE | Sealed interface: Loading, Success(data: IpApiResponse), Error(message: String) |
| `feature/ipinfo/src/main/kotlin/com.ventouxlabs.netlens/feature/ipinfo/data/IpInfoRepository.kt` | CREATE | Interface with `suspend fun fetchIpInfo(): Result<IpApiResponse>` |
| `feature/ipinfo/src/main/kotlin/com.ventouxlabs.netlens/feature/ipinfo/data/IpInfoRepositoryImpl.kt` | CREATE | Ktor HttpClient GET to `http://ip-api.com/json/?fields=query,isp,org,as,country,regionName,city,lat,lon,proxy,hosting` |
| `feature/ipinfo/src/main/kotlin/com.ventouxlabs.netlens/feature/ipinfo/di/IpInfoModule.kt` | CREATE | @Module @InstallIn(SingletonComponent) binding repo + providing Ktor HttpClient |
| `feature/ipinfo/src/main/kotlin/com.ventouxlabs.netlens/feature/ipinfo/IpInfoViewModel.kt` | CREATE | @HiltViewModel, StateFlow<IpInfoUiState>, refresh(), init fetches |
| `feature/ipinfo/src/main/kotlin/com.ventouxlabs.netlens/feature/ipinfo/IpInfoScreen.kt` | CREATE | @Composable with pull-to-refresh, displays all fields, copy-IP button |
| `widget/src/main/kotlin/com.ventouxlabs.netlens/widget/IpWidgetState.kt` | CREATE | Data class: ip, isp, isVpn — serialized to/from DataStore |
| `widget/src/main/kotlin/com.ventouxlabs.netlens/widget/IpWidgetStateDefinition.kt` | CREATE | GlanceStateDefinition<IpWidgetState> backed by DataStore<Preferences> |
| `widget/src/main/kotlin/com.ventouxlabs.netlens/widget/IpWidgetRefreshWorker.kt` | CREATE | CoroutineWorker fetching ip-api, writing to DataStore, updating widget |
| `widget/src/main/kotlin/com.ventouxlabs.netlens/widget/IpWidgetRefreshAction.kt` | CREATE | ActionCallback that enqueues OneTimeWorkRequest |
| `widget/src/main/kotlin/com.ventouxlabs.netlens/widget/NetLensWidget.kt` | UPDATE | Read IpWidgetState, display IP + ISP + VPN dot (green/yellow), tap-to-copy via actionRunCallback, tap-refresh |
| `widget/src/main/kotlin/com.ventouxlabs.netlens/widget/NetLensWidgetReceiver.kt` | UPDATE | Override onEnabled to enqueue initial refresh worker |
| `app/src/main/kotlin/com.ventouxlabs.netlens/navigation/NetLensNavHost.kt` | UPDATE | Replace PlaceholderScreen for IpInfo route with IpInfoScreen() |

## Step-by-Step Tasks

### Task 1: Create IpApiResponse model
- **ACTION**: Create `feature/ipinfo/src/main/kotlin/com.ventouxlabs.netlens/feature/ipinfo/model/IpApiResponse.kt` with `@Serializable data class IpApiResponse(val query: String, val isp: String, val org: String, @SerialName("as") val asNumber: String, val country: String, val regionName: String, val city: String, val lat: Double, val lon: Double, val proxy: Boolean, val hosting: Boolean)`
- **VALIDATE**: Compiles with `./gradlew :feature:ipinfo:compileDebugKotlin`

### Task 2: Create IpInfoUiState
- **ACTION**: Create sealed interface `IpInfoUiState` with `Loading`, `Success(val data: IpApiResponse)`, `Error(val message: String)`
- **VALIDATE**: Compiles

### Task 3: Create IpInfoRepository interface + impl
- **ACTION**: Create interface with `suspend fun fetchIpInfo(): Result<IpApiResponse>`. Impl uses Ktor `HttpClient` with `ContentNegotiation` + `Json`. GET request to ip-api.com endpoint. Wrap in `runCatching`, never catch `CancellationException`.
- **VALIDATE**: Unit test with Ktor MockEngine

### Task 4: Create DI module
- **ACTION**: Create `IpInfoModule` with `@Binds` for repository, `@Provides` for HttpClient (configure CIO engine, ContentNegotiation with Json { ignoreUnknownKeys = true }, 10s timeout)
- **VALIDATE**: Compiles

### Task 5: Create IpInfoViewModel
- **ACTION**: `@HiltViewModel class IpInfoViewModel @Inject constructor(private val repository: IpInfoRepository)`. Private `_uiState = MutableStateFlow<IpInfoUiState>(IpInfoUiState.Loading)`. Public `uiState = _uiState.asStateFlow()`. `fun refresh()` launches in viewModelScope, sets Loading, calls repo, maps to Success/Error. `init { refresh() }`.
- **VALIDATE**: Unit test with fake repository + Turbine

### Task 6: Create IpInfoScreen
- **ACTION**: Composable function `IpInfoScreen(viewModel: IpInfoViewModel = hiltViewModel())`. Collect uiState. Use `Modifier.pullRefresh` (material3). Show loading indicator, error with retry, or card with all IP info fields. Copy-to-clipboard button for IP using `ClipboardManager`.
- **VALIDATE**: Preview renders

### Task 7: Wire navigation
- **ACTION**: In `NetLensNavHost.kt`, import `IpInfoScreen`, replace the `PlaceholderScreen` for `TopLevelDestination.IpInfo.route` with `IpInfoScreen()`
- **VALIDATE**: `./gradlew :app:compileDebugKotlin`

### Task 8: Create widget state model
- **ACTION**: Create `IpWidgetState(val ip: String, val isp: String, val isVpn: Boolean)` with DataStore key constants
- **VALIDATE**: Compiles

### Task 9: Create GlanceStateDefinition
- **ACTION**: Implement `GlanceStateDefinition<IpWidgetState>` that reads/writes IP, ISP, isVpn to `DataStore<Preferences>` using string/boolean preference keys
- **VALIDATE**: Compiles

### Task 10: Create refresh worker
- **ACTION**: `IpWidgetRefreshWorker : CoroutineWorker` that creates a Ktor client inline (workers can't use Hilt easily), fetches ip-api, writes to DataStore, calls `NetLensWidget().updateAll(context)`. Return `Result.success()` or `Result.retry()`.
- **VALIDATE**: Compiles

### Task 11: Create ActionCallback for tap actions
- **ACTION**: `IpWidgetRefreshAction : ActionCallback` that enqueues `OneTimeWorkRequest<IpWidgetRefreshWorker>`. `CopyIpAction : ActionCallback` that reads IP from state, copies to clipboard, shows toast.
- **VALIDATE**: Compiles

### Task 12: Update NetLensWidget
- **ACTION**: Override `stateDefinition` with `IpWidgetStateDefinition`. In `provideGlance`, read `currentState<IpWidgetState>()`. Display: "NetLens" title, IP address (large text), ISP name, colored dot (green = no VPN, yellow = VPN). Tap IP → `CopyIpAction`. Refresh icon → `IpWidgetRefreshAction`.
- **VALIDATE**: `./gradlew :widget:compileDebugKotlin`

### Task 13: Update NetLensWidgetReceiver
- **ACTION**: Override `onEnabled()` to enqueue initial `OneTimeWorkRequest<IpWidgetRefreshWorker>`
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - `IpInfoRepositoryImpl` — Ktor MockEngine returning valid JSON, error responses
  - `IpInfoViewModel` — Turbine: Loading → Success, Loading → Error, refresh cycle
  - `IpApiResponse` deserialization — edge cases (missing optional fields)
- **Integration tests for**:
  - Widget state round-trip: write to DataStore, read back via GlanceStateDefinition

## Validation
