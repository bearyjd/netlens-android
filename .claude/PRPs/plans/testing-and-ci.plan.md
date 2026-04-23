# Plan: Unit Tests + CI Pipeline

## Summary
Add test infrastructure (dependencies, convention plugin), write unit tests for the most critical classes (SsrfGuard, WhoisClientImpl parsing, EndpointCheckerImpl, RdnsResolverImpl, MonitorViewModel), and set up GitHub Actions CI.

## User Story
As a contributor, I want automated tests and CI so that regressions are caught before merge.

## Metadata
- **Complexity**: Medium
- **Estimated Files**: ~12

---

## Patterns to Mirror

### BUILD_GRADLE_FEATURE
// SOURCE: feature/monitor/build.gradle.kts
```kotlin
plugins {
    id("netlens.android.feature")
}
dependencies {
    implementation(project(":core:network"))
    // testImplementation deps go here
}
```

### CONVENTION_PLUGIN
// SOURCE: build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt
```kotlin
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // apply other plugins, add dependencies via libs catalog
    }
}
```

### VIEWMODEL_PATTERN
// SOURCE: feature/monitor/src/main/kotlin/.../MonitorViewModel.kt
```kotlin
@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val endpointChecker: EndpointChecker,
    private val endpointDao: EndpointDao,
) : ViewModel() {
    private val _state = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = _state.asStateFlow()
}
```

---

## Step-by-Step Tasks

### Task 1: Add test dependencies to version catalog
- **ACTION**: Add junit5, kotlinx-coroutines-test, turbine, ktor-client-mock, and room-testing to libs.versions.toml
- **VALIDATE**: `./gradlew help` (catalog parses)

### Task 2: Wire test dependencies into convention plugin
- **ACTION**: Update AndroidLibraryConventionPlugin to add testImplementation deps automatically
- **VALIDATE**: `./gradlew assembleDebug` (no breakage)

### Task 3: Add testImplementation to core:network build.gradle.kts
- **ACTION**: Add junit5 testImplementation (core:network uses netlens.android.library, not feature)
- **VALIDATE**: `./gradlew :core:network:testDebugUnitTest` (runs, even if 0 tests)

### Task 4: Write SsrfGuard unit tests
- **ACTION**: Create core/network/src/test/kotlin/us/beary/netlens/core/network/SsrfGuardTest.kt
- **TESTS**: localhost, 127.x, 10.x, 192.168.x, 172.16.x, public IP, unresolvable host, IPv6 loopback, unique-local
- **VALIDATE**: `./gradlew :core:network:testDebugUnitTest`

### Task 5: Write WhoisClientImpl parsing tests
- **ACTION**: Create feature/whois/src/test/kotlin/.../engine/WhoisClientImplTest.kt
- **TESTS**: parseRefer extraction, parseWhoisResponse fields, validateDomain edge cases, bounded rawQuery (via testing the char limit constant)
- **GOTCHA**: rawQuery does real I/O — test parsing functions only. Make them internal for testing or test via public surface.
- **VALIDATE**: `./gradlew :feature:whois:testDebugUnitTest`

### Task 6: Write RdnsResolverImpl tests
- **ACTION**: Create feature/whois/src/test/kotlin/.../engine/RdnsResolverImplTest.kt
- **TESTS**: isValidIpLiteral for IPv4, IPv6, garbage input, single-colon string
- **VALIDATE**: `./gradlew :feature:whois:testDebugUnitTest`

### Task 7: Write EndpointCheckerImpl URL validation tests
- **ACTION**: Create feature/monitor/src/test/kotlin/.../engine/EndpointCheckerImplTest.kt
- **TESTS**: rejects non-http URLs, rejects private/loopback hosts
- **GOTCHA**: Actual HTTP calls need mocking or will fail — test validation path only via require() exceptions
- **VALIDATE**: `./gradlew :feature:monitor:testDebugUnitTest`

### Task 8: Create GitHub Actions CI workflow
- **ACTION**: Create .github/workflows/ci.yml — assembleDebug + testDebugUnitTest
- **VALIDATE**: File exists, YAML is valid

---

## Validation Commands

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Acceptance Criteria
- [ ] Test dependencies in version catalog and convention plugin
- [ ] SsrfGuard has 8+ unit tests covering all address ranges
- [ ] WhoisClientImpl parsing has 5+ tests
- [ ] RdnsResolverImpl has 4+ tests
- [ ] EndpointCheckerImpl URL validation has 3+ tests
- [ ] All tests pass
- [ ] CI workflow file exists
- [ ] Build still passes
