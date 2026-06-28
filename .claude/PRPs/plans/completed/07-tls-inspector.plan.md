# Plan: TLS Inspector

## Summary

Implement a TLS certificate inspector that connects to a host:port via SSL, retrieves the certificate chain, and displays certificate details including subject, issuer, SANs, validity dates, and expiry warnings.

## User Story

As a user, I want to inspect TLS certificates for any host, so that I can verify certificate validity, check expiry dates, and audit the certificate chain.

## Metadata

- **Complexity**: Medium
- **Branch**: feat/tls-inspector
- **PR**: PR-07
- **Depends On**: scaffold
- **Estimated Files**: 10
- **New Modules**: feature/tls (new)

## Patterns to Mirror

### FEATURE_MODULE
// SOURCE: feature/portscan/build.gradle.kts — minimal feature with :core:network dep

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `feature/tls/build.gradle.kts` | CREATE | netlens.android.feature plugin, namespace com.ventouxlabs.netlens.feature.tls, dep on :core:network |
| `settings.gradle.kts` | UPDATE | Add `include(":feature:tls")` |
| `app/build.gradle.kts` | UPDATE | Add `implementation(project(":feature:tls"))` |
| `feature/tls/src/main/kotlin/com.ventouxlabs.netlens/feature/tls/model/TlsCertInfo.kt` | CREATE | data class: subject, issuer, sans (List<String>), notBefore, notAfter, serialNumber, signatureAlgorithm, isExpired, daysUntilExpiry |
| `feature/tls/src/main/kotlin/com.ventouxlabs.netlens/feature/tls/model/TlsInspectUiState.kt` | CREATE | data class: host, port, certChain (List<TlsCertInfo>), isLoading, error |
| `feature/tls/src/main/kotlin/com.ventouxlabs.netlens/feature/tls/engine/TlsInspector.kt` | CREATE | Interface: suspend fun inspect(host: String, port: Int): Result<List<TlsCertInfo>> |
| `feature/tls/src/main/kotlin/com.ventouxlabs.netlens/feature/tls/engine/TlsInspectorImpl.kt` | CREATE | SSLSocketFactory.getDefault().createSocket(host, port) → SSLSocket.startHandshake() → session.peerCertificates. Cast to X509Certificate. Extract: subjectDN, issuerDN, subjectAlternativeNames, notBefore, notAfter, serialNumber, sigAlgName. Compute daysUntilExpiry. Run on Dispatchers.IO. |
| `feature/tls/src/main/kotlin/com.ventouxlabs.netlens/feature/tls/di/TlsModule.kt` | CREATE | @Module @Binds TlsInspector |
| `feature/tls/src/main/kotlin/com.ventouxlabs.netlens/feature/tls/TlsInspectorViewModel.kt` | CREATE | @HiltViewModel, inspect(host, port), state with cert chain |
| `feature/tls/src/main/kotlin/com.ventouxlabs.netlens/feature/tls/TlsInspectorScreen.kt` | CREATE | Host:port TextField, Inspect button, cert chain list (expandable cards per cert), expiry warning banner (red <30 days, yellow <90 days) |
| `app/src/main/kotlin/com.ventouxlabs.netlens/navigation/TopLevelDestination.kt` | UPDATE | Add TlsInspect entry (or keep as secondary nav — see PR-13) |
| `app/src/main/kotlin/com.ventouxlabs.netlens/navigation/NetLensNavHost.kt` | UPDATE | Add composable route for TLS |

## Step-by-Step Tasks

### Task 1: Create feature module
- **ACTION**: Create `feature/tls/build.gradle.kts` with `netlens.android.feature` plugin, namespace `com.ventouxlabs.netlens.feature.tls`, dependency on `:core:network`. Update `settings.gradle.kts` to include `:feature:tls`. Update `app/build.gradle.kts` to add implementation dependency.
- **VALIDATE**: `./gradlew :feature:tls:compileDebugKotlin`

### Task 2: Create TlsCertInfo model
- **ACTION**: `data class TlsCertInfo(val subject: String, val issuer: String, val sans: List<String>, val notBefore: Long, val notAfter: Long, val serialNumber: String, val signatureAlgorithm: String, val isExpired: Boolean, val daysUntilExpiry: Long)`
- **VALIDATE**: Compiles

### Task 3: Create TlsInspector engine
- **ACTION**: Interface + impl. On `Dispatchers.IO`: create `SSLSocketFactory.getDefault().createSocket()` → connect → `(socket as SSLSocket).startHandshake()` → `session.peerCertificates.map { it as X509Certificate }`. Extract fields. Compute `daysUntilExpiry = ChronoUnit.DAYS.between(Instant.now(), cert.notAfter.toInstant())`. Close socket in finally block. Wrap in `runCatching`.
- **VALIDATE**: Unit test (can mock with local test server or test parsing logic separately)

### Task 4: Create DI module
- **ACTION**: `TlsModule` with `@Binds` for TlsInspector
- **VALIDATE**: Compiles

### Task 5: Create TlsInspectorViewModel
- **ACTION**: `@HiltViewModel`, `fun inspect(host: String, port: Int = 443)`. Parse "host:port" input. State with cert chain.
- **VALIDATE**: Unit test with Turbine

### Task 6: Create TlsInspectorScreen
- **ACTION**: OutlinedTextField with "host:port" placeholder (default port 443). Inspect button. LazyColumn of expandable cert cards: subject (bold), issuer, SANs as chips, validity period, serial number, sig algorithm. Warning banner at top if leaf cert expiring soon.
- **VALIDATE**: Preview renders

### Task 7: Wire navigation
- **ACTION**: Add route to NavHost. For now, add as non-bottom-nav composable route accessible via deep link or direct navigation (since bottom bar has 6 items already). PR-13 will reorganize.
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - TlsCertInfo construction from mock X509Certificate
  - `TlsInspectorViewModel` — Turbine: inspect lifecycle
  - Host:port input parsing
- **Integration tests for**:
  - Inspect google.com:443 (network-dependent, optional)

## Validation
