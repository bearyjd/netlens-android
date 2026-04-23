# Plan: Fix AGP 8.7.3 NPE & Expand Test Coverage

## Summary
Upgrade AGP from 8.7.3 to 8.9.0 to fix the `group.displayName must not be null` NPE that breaks `assembleDebug` locally and prevents running the aggregate `testDebugUnitTest` task. Then add unit tests for every untested engine/parser class across feature modules, bringing test coverage from 4 modules to 13+.

## User Story
As a developer, I want the build to work locally and I want test coverage on all pure-logic engine classes, so that regressions are caught before merge and I can iterate confidently.

## Problem → Solution
`./gradlew assembleDebug` crashes with an AGP NPE; CI works around it by targeting 4 specific modules → Upgrade AGP so the full build and aggregate test task work. Only 5 test files exist across 4 modules → Add tests for the 6 untested engine/parser classes (ping, traceroute, portscan, dns, lanscan, plus a host validation utility).

## Metadata
- **Complexity**: Medium
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: ~15

---

## UX Design
N/A — internal change

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `gradle/libs.versions.toml` | 1-3 | AGP + Kotlin version to bump |
| P0 | `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt` | 42-49 | Test task config and `hasTestSources` gate |
| P0 | `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt` | all | compileSdk/targetSdk — must match after AGP bump |
| P1 | `core/network/src/test/.../SsrfGuardTest.kt` | all | Canonical test style to mirror |
| P1 | `feature/lanscan/src/test/.../DeviceFingerprinterTest.kt` | all | Test style with helper factory |
| P1 | `feature/ping/src/main/.../engine/PingOutputParser.kt` | all | Primary test target — pure regex parsing |
| P1 | `feature/ping/src/main/.../engine/PingerImpl.kt` | 14-22 | HOST_PATTERN + validateHost — shared pattern |
| P1 | `feature/traceroute/src/main/.../engine/TracerImpl.kt` | 14-82 | parseHopOutput + regexes to test |
| P1 | `feature/portscan/src/main/.../engine/PortScannerImpl.kt` | 72-78 | Validation constants + HOST_PATTERN |
| P2 | `.github/workflows/ci.yml` | all | Update test command after fix |

---

## Patterns to Mirror

### TEST_STYLE
```kotlin
// SOURCE: core/network/src/test/.../SsrfGuardTest.kt:7-12
class SsrfGuardTest {
    @Test
    fun `localhost returns true`() {
        assertTrue(SsrfGuard.isPrivateOrLoopback("localhost"))
    }
}
```
- JUnit 5 with backtick-quoted names
- Direct assertions (`assertTrue`, `assertEquals`, `assertNull`)
- No mocking frameworks — call real code or use fakes
- `runTest` for suspend functions, plain calls for synchronous logic

### HELPER_FACTORY
```kotlin
// SOURCE: feature/lanscan/src/test/.../DeviceFingerprinterTest.kt
// Uses a private helper function to construct test data
private fun device(vendor: String? = null, hostname: String? = null) = LanDevice(...)
```

### CONVENTION_TEST_GATE
```kotlin
// SOURCE: build-logic/.../AndroidLibraryConventionPlugin.kt:42-48
val hasTestSources = !project.fileTree("src/test").isEmpty
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    filter { isFailOnNoMatchingTests = false }
    enabled = hasTestSources
}
```
Test dependencies (junit5, coroutines-test) are already added by the convention plugin to ALL library modules — no per-module build.gradle.kts changes needed for tests.

---

## Files to Change

### Part 1: Fix AGP NPE

| File | Action | Justification |
|---|---|---|
| `gradle/libs.versions.toml` | UPDATE | Bump `agp` from `8.7.3` to `8.9.0` |
| `.github/workflows/ci.yml` | UPDATE | Restore aggregate `testDebugUnitTest` task |

### Part 2: Add Tests

| File | Action | Justification |
|---|---|---|
| `feature/ping/src/test/kotlin/us/beary/netlens/feature/ping/engine/PingOutputParserTest.kt` | CREATE | Test regex parsing of ping reply lines and summary |
| `feature/ping/src/test/kotlin/us/beary/netlens/feature/ping/engine/PingerImplTest.kt` | CREATE | Test HOST_PATTERN validation |
| `feature/traceroute/src/test/kotlin/us/beary/netlens/feature/traceroute/engine/TracerImplTest.kt` | CREATE | Test hop output parsing and host validation |
| `feature/portscan/src/test/kotlin/us/beary/netlens/feature/portscan/engine/PortScannerImplTest.kt` | CREATE | Test input validation (ports range, count, host) |
| `feature/dns/src/test/kotlin/us/beary/netlens/feature/dns/engine/DnsResolverImplTest.kt` | CREATE | Test domain validation edge cases |
| `feature/lanscan/src/test/kotlin/us/beary/netlens/feature/lanscan/engine/ArpTableReaderTest.kt` | CREATE | Test ARP table line parsing |

## NOT Building
- ViewModel tests (require Hilt test infra and Turbine wiring — separate effort)
- Integration/E2E tests
- Tests for UI-only modules (tls, httptester, mdns, netlog, ipinfo — no testable engine layer)
- Mocking infrastructure or shared test utilities module

---

## Step-by-Step Tasks

### Task 1: Upgrade AGP to 8.9.0
- **ACTION**: Bump AGP version in version catalog
- **IMPLEMENT**: Change `agp = "8.7.3"` to `agp = "8.9.0"` in `gradle/libs.versions.toml`
- **GOTCHA**: AGP 8.9.0 requires Gradle 8.11+; we have Gradle 8.12 so this is fine. compileSdk 35 is supported. KSP version `2.1.0-1.0.29` is compatible.
- **VALIDATE**: `./gradlew assembleDebug` completes without NPE

### Task 2: Restore aggregate test task in CI
- **ACTION**: Replace per-module test commands with aggregate task
- **IMPLEMENT**: Change CI `run` line from `./gradlew :core:network:testDebugUnitTest :feature:lanscan:testDebugUnitTest ...` to `./gradlew testDebugUnitTest`
- **GOTCHA**: The convention plugin's `isFailOnNoMatchingTests = false` + `enabled = hasTestSources` should prevent failures on modules without tests. Verify locally first.
- **VALIDATE**: `./gradlew testDebugUnitTest` passes

### Task 3: PingOutputParser tests
- **ACTION**: Create test file for PingOutputParser regex parsing
- **IMPLEMENT**: Test `parseReplyLine()` and `parseSummary()` with representative ping output
- **MIRROR**: TEST_STYLE (JUnit 5, backtick names, direct assertions)
- **IMPORTS**: `org.junit.jupiter.api.Assertions.*`, `org.junit.jupiter.api.Test`, `us.beary.netlens.feature.ping.engine.PingOutputParser`, model classes
- **TEST CASES**:
  - `parseReplyLine` with standard reply: `"64 bytes from 8.8.8.8: icmp_seq=1 ttl=118 time=12.3 ms"` → PingResult(seq=1, latencyMs=12.3, ttl=118, ip="8.8.8.8")
  - `parseReplyLine` with timeout: `"From 192.168.1.1 icmp_seq=2 Destination Host Unreachable"` → PingResult(seq=2, isTimeout=true)
  - `parseReplyLine` with timed out variant: `"Request timeout for icmp_seq 3"` — check TIMEOUT_REGEX handles "timed out" vs "no answer"
  - `parseReplyLine` with irrelevant line → null
  - `parseSummary` with full stats block → PingSummary with all fields
  - `parseSummary` with no matching lines → null
  - `parseSummary` with packets but no RTT → PingSummary with zero RTT values
- **VALIDATE**: `./gradlew :feature:ping:testDebugUnitTest`

### Task 4: PingerImpl host validation tests
- **ACTION**: Create test file for HOST_PATTERN validation
- **IMPLEMENT**: Test `PingerImpl` constructor + `ping()` with invalid hosts that should throw
- **MIRROR**: TEST_STYLE
- **TEST CASES**:
  - Blank host → IllegalArgumentException
  - Host starting with `-` → IllegalArgumentException
  - Host with special chars (`; rm -rf /`) → IllegalArgumentException
  - Valid hostname (`example.com`) → does not throw (cancel immediately after)
  - Valid IP (`8.8.8.8`) → does not throw
  - Host exceeding 253 chars → IllegalArgumentException
- **GOTCHA**: `ping()` returns a Flow; collect to trigger validation, then cancel. Use `runTest` and catch the exception.
- **VALIDATE**: `./gradlew :feature:ping:testDebugUnitTest`

### Task 5: TracerImpl parsing and validation tests
- **ACTION**: Create test for `parseHopOutput` (private) via public interface, plus host validation
- **IMPLEMENT**: Since `parseHopOutput` is private, test the regex patterns directly and host validation via `trace()`. Extract regexes are testable via the companion object patterns.
- **MIRROR**: TEST_STYLE
- **TEST CASES**:
  - Host validation: blank, dash-prefix, special chars → IllegalArgumentException
  - FROM_REGEX match: `"64 bytes from 10.0.0.1: icmp_seq=1 ttl=64 time=1.23 ms"` → captures ip + rtt
  - EXCEEDED_REGEX match: `"From 10.0.0.1 icmp_seq=1 Time to live exceeded"` → captures ip
  - No match → timeout hop
- **GOTCHA**: `parseHopOutput` is private. Test regexes by testing the companion vals (FROM_REGEX, EXCEEDED_REGEX) directly — they're accessible via `TracerImpl` companion. If they're private, test via `trace()` Flow behavior with invalid hosts only.
- **VALIDATE**: `./gradlew :feature:traceroute:testDebugUnitTest`

### Task 6: PortScannerImpl validation tests
- **ACTION**: Create test for input validation in `scan()`
- **IMPLEMENT**: Test require() checks in scan()
- **MIRROR**: TEST_STYLE
- **TEST CASES**:
  - Too many ports (10,001 ports) → IllegalArgumentException
  - Port out of range (0, 65536) → IllegalArgumentException
  - Blank host → IllegalArgumentException
  - Host starting with `-` → IllegalArgumentException
  - Host with special chars → IllegalArgumentException
  - Valid input with 1 port → does not throw (will fail on I/O but validation passes)
- **GOTCHA**: `scan()` returns a Flow; must collect to trigger require checks. Use `runTest` + `toList()` and catch.
- **VALIDATE**: `./gradlew :feature:portscan:testDebugUnitTest`

### Task 7: DnsResolverImpl edge case tests
- **ACTION**: Create test for DNS lookup edge cases
- **IMPLEMENT**: Test behavior with edge-case domain inputs
- **MIRROR**: TEST_STYLE
- **TEST CASES**:
  - Empty domain → throws (dnsjava TextParseException)
  - Invalid domain format → throws
  - Valid domain with no results → empty list in Result.success
- **GOTCHA**: dnsjava `Lookup` constructor throws `TextParseException` for malformed domains. Since `resolveType` is not wrapped in `runCatching`, the outer `runCatching` in `lookup()` catches it. Tests should verify Result.isFailure.
- **VALIDATE**: `./gradlew :feature:dns:testDebugUnitTest`

### Task 8: ArpTableReader tests
- **ACTION**: Create test for ARP table parsing
- **IMPLEMENT**: Test line parsing with representative /proc/net/arp content. ArpTableReader reads from filesystem; if it has a parseable method, test that. Otherwise test the output parsing logic.
- **MIRROR**: HELPER_FACTORY pattern
- **TEST CASES**:
  - Standard ARP entry → parsed correctly
  - Incomplete MAC (00:00:00:00:00:00) → filtered out
  - Header line → skipped
  - Empty file → empty list
- **GOTCHA**: ArpTableReader reads `/proc/net/arp` directly. May need to check if the parsing logic is extractable, or if tests should verify validation only. If it does raw file I/O with no abstraction, skip this test (can't mock /proc on JVM).
- **VALIDATE**: `./gradlew :feature:lanscan:testDebugUnitTest`

### Task 9: Update CI and verify
- **ACTION**: Run full build + test suite to verify everything works
- **VALIDATE**: `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` both pass

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| PingOutputParser reply | Standard ICMP reply line | PingResult with seq, latency, ttl, ip | No |
| PingOutputParser timeout | Timeout/unreachable line | PingResult with isTimeout=true | No |
| PingOutputParser irrelevant | Non-ping output | null | Yes |
| PingOutputParser summary | Stats lines | PingSummary with all fields | No |
| PingOutputParser no stats | Empty lines | null | Yes |
| PingerImpl blank host | "" | IllegalArgumentException | Yes |
| PingerImpl injection | "; rm -rf /" | IllegalArgumentException | Yes — security |
| TracerImpl host validation | Various invalid | IllegalArgumentException | Yes |
| PortScanner too many ports | 10,001 ports | IllegalArgumentException | Yes — boundary |
| PortScanner port 0 | port=0 | IllegalArgumentException | Yes — boundary |
| PortScanner port 65536 | port=65536 | IllegalArgumentException | Yes — boundary |
| DnsResolver bad domain | "" | Result.isFailure | Yes |
| ArpTable incomplete MAC | 00:00:00:00:00:00 | Filtered out | Yes |

### Edge Cases Checklist
- [x] Empty input (blank host, empty domain)
- [x] Maximum size input (253-char host, 10,000 ports boundary)
- [x] Invalid types (out-of-range ports, malformed IPs)
- [ ] Concurrent access (not applicable — each call is independent)
- [ ] Network failure (not testing I/O — pure logic only)
- [x] Command injection (hosts with `;`, `-`, special chars)

---

## Validation Commands

### Build
```bash
./gradlew assembleDebug
```
EXPECT: BUILD SUCCESSFUL (no NPE)

### Unit Tests
```bash
./gradlew testDebugUnitTest
```
EXPECT: All tests pass across all modules

### Single Module Tests
```bash
./gradlew :feature:ping:testDebugUnitTest
./gradlew :feature:traceroute:testDebugUnitTest
./gradlew :feature:portscan:testDebugUnitTest
./gradlew :feature:dns:testDebugUnitTest
./gradlew :feature:lanscan:testDebugUnitTest
```
EXPECT: Each passes independently

---

## Acceptance Criteria
- [ ] `./gradlew assembleDebug` succeeds (AGP NPE fixed)
- [ ] `./gradlew testDebugUnitTest` runs all modules (aggregate task works)
- [ ] PingOutputParser has ≥7 test cases
- [ ] PingerImpl host validation has ≥5 test cases
- [ ] TracerImpl has ≥4 test cases
- [ ] PortScannerImpl has ≥5 test cases
- [ ] DnsResolverImpl has ≥3 test cases
- [ ] CI workflow updated to use aggregate task
- [ ] No new lint or type errors

## Completion Checklist
- [ ] Tests follow JUnit 5 + backtick naming convention
- [ ] No mocking frameworks introduced
- [ ] Tests are pure logic — no network I/O
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| AGP 8.9.0 introduces breaking changes | Low | High | Check release notes; KSP/Compose/Room versions are compatible |
| TracerImpl regexes are private companion vals | Medium | Low | Test via public `trace()` host validation; skip regex unit tests if inaccessible |
| ArpTableReader does raw /proc I/O | High | Low | Skip test if no parseable method; note as future refactor |
| Convention plugin `hasTestSources` check evaluated at config time | Medium | Medium | Adding test files may require cache invalidation (`--no-configuration-cache` first run) |

## Notes
- The AGP NPE root cause is documented at https://issuetracker.google.com/issues — AGP 8.7.x has a bug in test result grouping when modules lack a `group` property. Fixed in 8.8+.
- Gradle 8.12 supports AGP 8.9.0 (requires ≥ 8.11).
- All test dependencies (JUnit 5, coroutines-test) are already wired via `AndroidLibraryConventionPlugin` — no per-module build.gradle.kts changes needed.
- Turbine is declared in the version catalog but NOT added by the convention plugin. ViewModel tests (future work) will need it added.
