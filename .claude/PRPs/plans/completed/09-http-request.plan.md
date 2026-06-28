# Plan: HTTP Request Builder

## Summary

Implement an HTTP request builder tool that lets users craft custom HTTP requests (method, URL, headers, body), execute them via Ktor, view responses with syntax highlighting, and save request history to Room.

## User Story

As a user, I want to build and send custom HTTP requests with full control over method, headers, and body, so that I can test APIs and debug HTTP endpoints.

## Metadata

- **Complexity**: Large
- **Branch**: feat/http-request
- **PR**: PR-09
- **Depends On**: scaffold
- **Estimated Files**: 14
- **New Modules**: feature/httprequest (new)

## Patterns to Mirror

### FEATURE_MODULE
// SOURCE: feature/ipinfo/build.gradle.kts — with Ktor + serialization deps

### ROOM_ENTITY
// Pattern from PR-02/PR-06 for entity + DAO additions

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `feature/httprequest/build.gradle.kts` | CREATE | netlens.android.feature + kotlin-serialization, deps: :core:network, :core:data, Ktor libs, kotlinx-serialization |
| `settings.gradle.kts` | UPDATE | Add `include(":feature:httprequest")` |
| `app/build.gradle.kts` | UPDATE | Add implementation project dep |
| `core/data/src/main/kotlin/com.ventouxlabs.netlens/core/data/entity/HttpRequestEntity.kt` | CREATE | @Entity: id, method, url, headersJson, body, responseStatus, responseBody, latencyMs, timestamp |
| `core/data/src/main/kotlin/com.ventouxlabs.netlens/core/data/dao/HttpRequestDao.kt` | CREATE | @Dao: getAll (Flow, ordered by timestamp desc), insert, delete, deleteAll |
| `core/data/src/main/kotlin/com.ventouxlabs.netlens/core/data/NetLensDatabase.kt` | UPDATE | Add HttpRequestEntity, bump version, add abstract DAO |
| `core/data/src/main/kotlin/com.ventouxlabs.netlens/core/data/di/DataModule.kt` | UPDATE | @Provides HttpRequestDao |
| `feature/httprequest/src/main/kotlin/com.ventouxlabs.netlens/feature/httprequest/model/HttpRequestConfig.kt` | CREATE | data class: method (GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS), url, headers (Map<String,String>), body (String?) |
| `feature/httprequest/src/main/kotlin/com.ventouxlabs.netlens/feature/httprequest/model/HttpResponseResult.kt` | CREATE | data class: statusCode, statusText, headers (Map<String,List<String>>), body, latencyMs |
| `feature/httprequest/src/main/kotlin/com.ventouxlabs.netlens/feature/httprequest/model/HttpRequestUiState.kt` | CREATE | data class: config, response, isLoading, history list, error, showHistory |
| `feature/httprequest/src/main/kotlin/com.ventouxlabs.netlens/feature/httprequest/engine/HttpRequestExecutor.kt` | CREATE | Interface: suspend fun execute(config: HttpRequestConfig): Result<HttpResponseResult> |
| `feature/httprequest/src/main/kotlin/com.ventouxlabs.netlens/feature/httprequest/engine/HttpRequestExecutorImpl.kt` | CREATE | Uses Ktor HttpClient. Builds request from config. Measures latency. Returns status + headers + body. |
| `feature/httprequest/src/main/kotlin/com.ventouxlabs.netlens/feature/httprequest/di/HttpRequestModule.kt` | CREATE | @Module binding executor, providing Ktor client |
| `feature/httprequest/src/main/kotlin/com.ventouxlabs.netlens/feature/httprequest/HttpRequestViewModel.kt` | CREATE | @HiltViewModel, send(), save to history, load history, replay from history |
| `feature/httprequest/src/main/kotlin/com.ventouxlabs.netlens/feature/httprequest/HttpRequestScreen.kt` | CREATE | Method dropdown, URL field, expandable headers editor (key-value pairs), body editor (for POST/PUT/PATCH), Send button, response viewer (status badge, headers, body with monospace font), history drawer |
| `app/src/main/kotlin/com.ventouxlabs.netlens/navigation/NetLensNavHost.kt` | UPDATE | Add composable route for httprequest |

## Step-by-Step Tasks

### Task 1: Create feature module
- **ACTION**: Create build.gradle.kts with Ktor deps (ktor-client-core, ktor-client-cio, ktor-client-content-negotiation, ktor-serialization-json), kotlinx-serialization, :core:data. Update settings + app build.
- **VALIDATE**: `./gradlew :feature:httprequest:compileDebugKotlin`

### Task 2: Create Room entity + DAO
- **ACTION**: `HttpRequestEntity` with `@PrimaryKey(autoGenerate = true) val id: Long = 0`, method, url, headersJson (serialized Map as String), body, responseStatus, responseBody (truncated to 10KB), latencyMs, timestamp. DAO with insert, getAll(Flow ordered by timestamp DESC limit 50), delete, deleteAll.
- **VALIDATE**: Compiles

### Task 3: Update NetLensDatabase + DataModule
- **ACTION**: Add entity, bump version, add DAO getter + provider
- **VALIDATE**: Compiles

### Task 4: Create domain models
- **ACTION**: HttpRequestConfig, HttpResponseResult as specified
- **VALIDATE**: Compiles

### Task 5: Create HttpRequestExecutor
- **ACTION**: Interface + impl. Build Ktor `HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = 30_000 } }`. `val start = System.nanoTime()`. Build request with method, url, headers, body. `val response = client.request { ... }`. Capture status, headers, body text (limit to 100KB). Compute latencyMs. Wrap in runCatching.
- **VALIDATE**: Unit test with MockEngine

### Task 6: Create DI module
- **ACTION**: Bind executor, provide Ktor client
- **VALIDATE**: Compiles

### Task 7: Create HttpRequestViewModel
- **ACTION**: `@HiltViewModel`, `fun send(config: HttpRequestConfig)`. Saves to history after execution. `fun loadHistory()`, `fun replayRequest(entity: HttpRequestEntity)` populates config from history entry. `fun clearHistory()`.
- **VALIDATE**: Unit test with Turbine

### Task 8: Create HttpRequestScreen
- **ACTION**: ExposedDropdownMenu for method (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS). URL OutlinedTextField. "Headers" expandable section with add/remove key-value rows. "Body" section (visible only for POST/PUT/PATCH). Send button. Response section: colored status code badge (2xx green, 4xx orange, 5xx red), response headers, monospace body text with horizontal scroll. History icon in top bar opens bottom sheet with recent requests.
- **VALIDATE**: Preview renders

### Task 9: Wire navigation
- **ACTION**: Add route to NavHost
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - `HttpRequestExecutorImpl` — Ktor MockEngine for various status codes, methods
  - `HttpRequestViewModel` — Turbine: send lifecycle, history management
- **Integration tests for**:
  - `HttpRequestDao` — Room in-memory: insert, query, delete

## Validation
