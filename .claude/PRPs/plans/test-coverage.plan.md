# Plan: Test Coverage Expansion

## Summary
Expand NetLens unit test coverage from ~28 tests across 4 files to 80%+ coverage across all 13 feature modules and 3 core modules. Add Turbine to test infrastructure, create fake implementations for all engine interfaces, test every ViewModel's state transitions, and configure Kover for coverage reporting.

## User Story
As a developer, I want comprehensive unit tests covering all ViewModels and engine logic, so that regressions are caught before they ship and the codebase is safe to refactor.

## Problem → Solution
~28 tests in 4 modules, 9 feature modules with zero tests → 120-150 tests, 80%+ line coverage, every ViewModel tested.

## Metadata
- **Complexity**: XL
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: ~30 new + 3 modified

---

## Current State

### Existing Tests (28 tests across 4 files)
| File | Tests | Pattern |
|------|-------|---------|
| `core/network/.../SsrfGuardTest.kt` | 10 | Direct object testing |
| `feature/whois/.../WhoisClientImplTest.kt` | 5 | Input validation |
| `feature/whois/.../RdnsResolverImplTest.kt` | 8 | Input validation with runTest |
| `feature/lanscan/.../DeviceFingerprinterTest.kt` | 23 | Pure function testing |
| `feature/monitor/.../EndpointCheckerImplTest.kt` | 5 | Input validation |

### Modules With No Tests
ping, dns, portscan, tls, httptester, mdns, netlog, wol, traceroute, ipinfo

### Test Infrastructure Present
- JUnit 5 (api + engine + params) via `AndroidLibraryConventionPlugin`
- `kotlinx-coroutines-test` via convention plugin
- `turbine` declared in version catalog but NOT added to convention plugin
- `ktor-client-mock` declared in version catalog but not used
- `useJUnitPlatform()` configured in convention plugin

### Missing Infrastructure
- Turbine not wired into test dependencies
- No coverage tooling (Jacoco/Kover)
- CI only runs tests for 4 modules explicitly
- No fake implementations exist for ViewModel testing

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt` | all | Test dependency wiring |
| P0 | `gradle/libs.versions.toml` | all | Version catalog for turbine, kover |
| P0 | `feature/whois/src/test/.../WhoisClientImplTest.kt` | all | Existing test pattern |
| P0 | `feature/monitor/src/test/.../EndpointCheckerImplTest.kt` | all | Existing test pattern |
| P1 | `.github/workflows/ci.yml` | all | CI test configuration |
| P1 | Any `*ViewModel.kt` | all | ViewModel pattern to test |
| P1 | Any `engine/*.kt` interface | all | Interface contracts for fakes |

---

## Patterns to Mirror

### TEST_STRUCTURE
// SOURCE: feature/whois/src/test/.../WhoisClientImplTest.kt
```kotlin
class WhoisClientImplTest {
    @Test
    fun `blank query returns failure`() = runTest {
        // arrange, act, assert
    }
}
```

### FAKE_PATTERN
```kotlin
class FakePinger : Pinger {
    var results: List<PingResult> = emptyList()
    var error: Throwable? = null
    override fun ping(host: String, count: Int): Flow<PingResult> = flow {
        error?.let { throw it }
        results.forEach { emit(it) }
    }
}
```

### VIEWMODEL_TEST_PATTERN
```kotlin
class DnsLookupViewModelTest {
    private val fakeDnsResolver = FakeDnsResolver()
    private lateinit var viewModel: DnsLookupViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = DnsLookupViewModel(fakeDnsResolver)
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `lookup sets isLoading then results on success`() = runTest {
        fakeDnsResolver.result = Result.success(listOf(testDnsResult))
        viewModel.state.test {
            // initial state
            awaitItem()
            viewModel.lookup()
            assertTrue(awaitItem().isLoading)
            assertEquals(listOf(testDnsResult), awaitItem().results)
        }
    }
}
```

---

## Files to Change

### Modified Files (3)
| File | Action | Justification |
|---|---|---|
| `build-logic/.../AndroidLibraryConventionPlugin.kt` | UPDATE | Add turbine to test deps |
| `gradle/libs.versions.toml` | UPDATE | Add kover version + plugin |
| `.github/workflows/ci.yml` | UPDATE | Run all module tests + coverage |

### New Files (~30)

**Fakes (13 files):**
| File | Implements |
|---|---|
| `feature/ping/src/test/.../engine/FakePinger.kt` | `Pinger` |
| `feature/dns/src/test/.../engine/FakeDnsResolver.kt` | `DnsResolver` |
| `feature/portscan/src/test/.../engine/FakePortScanner.kt` | `PortScanner` |
| `feature/tls/src/test/.../engine/FakeTlsInspector.kt` | `TlsInspector` |
| `feature/httptester/src/test/.../engine/FakeHttpRequester.kt` | `HttpRequester` |
| `feature/whois/src/test/.../engine/FakeWhoisClient.kt` | `WhoisClient` |
| `feature/whois/src/test/.../engine/FakeRdnsResolver.kt` | `RdnsResolver` |
| `feature/lanscan/src/test/.../engine/FakeSubnetScanner.kt` | `SubnetScanner` |
| `feature/traceroute/src/test/.../engine/FakeTracer.kt` | `Tracer` |
| `feature/mdns/src/test/.../engine/FakeMdnsScanner.kt` | `MdnsScanner` |
| `feature/netlog/src/test/.../engine/FakeNetworkMonitor.kt` | `NetworkMonitor` |
| `feature/wol/src/test/.../engine/FakeWolSender.kt` | `WolSender` |
| `feature/ipinfo/src/test/.../data/FakeIpInfoRepository.kt` | `IpInfoRepository` |

**Fake DAOs (3 files):**
| File | Implements |
|---|---|
| `feature/monitor/src/test/.../dao/FakeEndpointDao.kt` | `EndpointDao` |
| `feature/wol/src/test/.../dao/FakeWolTargetDao.kt` | `WolTargetDao` |
| `feature/netlog/src/test/.../dao/FakeNetworkEventDao.kt` | `NetworkEventDao` |

**ViewModel Tests (13 files):**
| File | Tests |
|---|---|
| `feature/dns/src/test/.../DnsLookupViewModelTest.kt` | 8 tests |
| `feature/tls/src/test/.../TlsViewModelTest.kt` | 3 tests |
| `feature/httptester/src/test/.../HttpTesterViewModelTest.kt` | 4 tests |
| `feature/ipinfo/src/test/.../IpInfoViewModelTest.kt` | 3 tests |
| `feature/portscan/src/test/.../PortScanViewModelTest.kt` | 6 tests |
| `feature/ping/src/test/.../PingViewModelTest.kt` | 9 tests |
| `feature/traceroute/src/test/.../TracerouteViewModelTest.kt` | 6 tests |
| `feature/mdns/src/test/.../MdnsViewModelTest.kt` | 6 tests |
| `feature/whois/src/test/.../WhoisViewModelTest.kt` | 8 tests |
| `feature/monitor/src/test/.../MonitorViewModelTest.kt` | 11 tests |
| `feature/wol/src/test/.../WolViewModelTest.kt` | 11 tests |
| `feature/netlog/src/test/.../NetLogViewModelTest.kt` | 8 tests |
| `feature/lanscan/src/test/.../LanScanViewModelTest.kt` | 7 tests |

**Utility Tests (1 file):**
| File | Tests |
|---|---|
| `feature/ping/src/test/.../engine/PingOutputParserTest.kt` | 7 tests |

## NOT Building
- Instrumented/UI tests (Espresso, Compose testing)
- Integration tests requiring real network
- Robolectric tests
- End-to-end tests

---

## Step-by-Step Tasks

### Phase 1: Infrastructure

#### Task 1.1: Wire Turbine into Convention Plugin
- **ACTION**: Add `add("testImplementation", libs.findLibrary("turbine").get())` to AndroidLibraryConventionPlugin.kt
- **VALIDATE**: `./gradlew :feature:ping:dependencies --configuration testDebugImplementation | grep turbine`

#### Task 1.2: Add Kover for Coverage
- **ACTION**: Add kover version+plugin to libs.versions.toml, apply to root build.gradle.kts
- **VALIDATE**: `./gradlew koverHtmlReportDebug` generates report

#### Task 1.3: Update CI
- **ACTION**: Replace explicit module list with `./gradlew testDebugUnitTest`, add coverage step
- **VALIDATE**: CI workflow YAML is valid

### Phase 2: Fakes (13 files)
- **ACTION**: Create one fake per engine interface in test source sets
- **IMPLEMENT**: Each fake stores configurable results/errors, returns them from interface methods
- **VALIDATE**: Each fake compiles

### Phase 3: Tier 1 ViewModel Tests (5 files — DNS, TLS, HTTP, IpInfo, PortScan)
- **ACTION**: Create test files with Turbine-based state assertions
- **IMPLEMENT**: Test initial state, input changes, success flow, error flow
- **VALIDATE**: `./gradlew :feature:<name>:testDebugUnitTest` passes

### Phase 4: Tier 2 ViewModel Tests (4 files — Ping, Traceroute, mDNS, WHOIS)
- **ACTION**: Test Flow-collecting ViewModels
- **GOTCHA**: Use `UnconfinedTestDispatcher` for Dispatchers.IO usage
- **VALIDATE**: All tests pass

### Phase 5: Tier 3 ViewModel Tests (3 files — Monitor, WoL, NetLog)
- **ACTION**: Test DAO-dependent ViewModels using fake DAOs
- **GOTCHA**: FakeDAOs must use MutableStateFlow to simulate Room reactive queries
- **VALIDATE**: All tests pass

### Phase 6: Utility Tests (1 file — PingOutputParser)
- **ACTION**: Test pure parsing logic
- **VALIDATE**: `./gradlew :feature:ping:testDebugUnitTest` passes

### Phase 7: LanScanViewModel (requires refactor)
- **ACTION**: Extract SubnetInfoProvider interface from LanScanViewModel, create fake, write tests
- **GOTCHA**: Only ViewModel requiring production code changes
- **VALIDATE**: Existing behavior unchanged, tests pass

---

## Priority Order
1. Phase 1 (Infrastructure) — unblocks everything
2. Phase 2 (Fakes) — unblocks all ViewModel tests
3. Phase 3 (Tier 1 VMs) — simplest, biggest coverage gain
4. Phase 6 (Utilities) — pure logic, high value
5. Phase 4 (Tier 2 VMs) — Flow-collecting
6. Phase 5 (Tier 3 VMs) — DAO-dependent
7. Phase 7 (LanScan) — requires refactor, do last

---

## Validation Commands

```bash
# Run all tests
./gradlew testDebugUnitTest

# Coverage report
./gradlew koverHtmlReportDebug
# Report at: build/reports/kover/htmlDebug/index.html

# Specific module
./gradlew :feature:ping:testDebugUnitTest
```

## Acceptance Criteria
- [ ] Turbine added to convention plugin test dependencies
- [ ] Kover configured and producing coverage reports
- [ ] CI runs tests for all modules
- [ ] Every feature ViewModel has test coverage
- [ ] Fake implementations exist for all engine interfaces and DAOs
- [ ] PingOutputParser has comprehensive tests
- [ ] Overall line coverage >= 80%
- [ ] All tests pass in CI

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ViewModels using Dispatchers.IO directly | High | Medium | UnconfinedTestDispatcher in setUp |
| LanScanViewModel needs Android Context | Certain | Medium | Extract SubnetInfoProvider interface |
| FakeDAOs don't match Room behavior | Medium | Medium | Use MutableStateFlow to simulate reactivity |
| Network-dependent engine tests flaky | Medium | Low | Focus on input validation, tag network tests |
