# Plan: Fix Fable Audit Findings (Security, Feature Parity, UI Consistency)

> **Relocation note (2026-08-08).** This file moved here from `.claude/PRPs/plans/`, which was
> deleted. Every `.claude/PRPs/...` path below is dead. Two that matter:
>
> - **The audit report this plan remediates —
>   `.claude/PRPs/reports/fable-audit-2026-07-04.md` — no longer exists anywhere.** It was never
>   git-tracked, so there is nothing to recover. Each task below restates its own finding, so the
>   plan is self-contained; what is lost is the original write-up, not the work.
> - The companion handoff is now `docs/backlog/fable-audit-handoff-2026-07-04.md`.
>
> Status is unchanged: Phase 1 (Tasks 1-2) is done and merged; **Tasks 3-11 are not started.**
> Task 6 needs a product decision from a human before it can be implemented.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remediate the findings in `.claude/PRPs/reports/fable-audit-2026-07-04.md` — one critical SSRF bypass, two feature-parity gaps, and five UI/functionality consistency issues — without introducing new abstractions beyond what each fix requires.

**Architecture:** Each task is scoped to the existing module it fixes; no new modules or shared infrastructure needed except where a task extends an existing shared type (`HistoryRepository`, `LocalStatusColors`).

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Hilt, Room, Ktor (CIO), JUnit 5 + Turbine + kotlinx-coroutines-test.

## Global Constraints
- Follow the existing feature module pattern documented in `CLAUDE.md` (ViewModel/Screen/di/engine/model) — do not restructure modules.
- Result export follows the `buildExportText()` + `ResultExporter.shareAsText()`/`copyToClipboard()` convention; Pro-gating follows one of the 3 documented patterns (direct `if (isPro)`, nullable lambda, boolean param) — pick the one matching each screen's existing architecture.
- Preserve the existing `catch (e: CancellationException) { throw e }`-before-broad-catch convention in any ViewModel/engine code touched.
- Prefer hand-written fakes over mocking frameworks in tests, per `CLAUDE.md`.
- Each task ends with `./gradlew :feature:<module>:testDebugUnitTest` (or `:core:<module>:testDebugUnitTest`) passing, plus a commit. Note: product flavors (`foss`/`gplay`) are defined only in `app/build.gradle.kts` — feature and core modules have no flavor dimension, so their unit-test/assemble tasks are unqualified (`testDebugUnitTest`, `assembleDebug`), not `testDebugUnitTest`/`assembleDebug`. (CLAUDE.md's "Build Commands" section states `./gradlew :feature:ping:testDebugUnitTest`, which is stale — worth fixing alongside Task 11.)

---

## Phase 1 — Critical: Security

### Task 1: Disable/re-validate Ktor redirect following in HTTP Tester (Finding 1)

**Files:**
- Modify: `feature/httptester/src/main/kotlin/com/ventouxlabs/netlens/feature/httptester/engine/HttpRequesterImpl.kt:24-58`
- Test: `feature/httptester/src/test/kotlin/com/ventouxlabs/netlens/feature/httptester/engine/HttpRequesterImplTest.kt`

**Interfaces:**
- Consumes: `SsrfGuard.isPrivateOrLoopback(host: String): Boolean` from `core:network`
- Produces: no change to `HttpRequesterImpl`'s public request-execution signature

- [ ] **Step 1: Read the current implementation in full**
  Read `HttpRequesterImpl.kt` end-to-end (not just lines 24-58) to confirm the exact `HttpClient(CIO) { ... }` builder block and the `client.request(url)` call site, since this plan was written from an audit summary, not a live diff.

- [ ] **Step 2: Write the failing test**
  Add a test that stands up a local Ktor `MockEngine` (already available via `ktor-client-mock` if present, otherwise add it as a `testImplementation` dependency in `feature/httptester/build.gradle.kts`) that responds to the initial request with `302 Found` and `Location: http://127.0.0.1:9/private` on redirect. Assert that `HttpRequesterImpl` either (a) does not follow the redirect at all, or (b) throws/returns a blocked-result before issuing the second request. A minimal shape:
  ```kotlin
  @Test
  fun `does not follow redirect into private address`() = runTest {
      val engine = MockEngine { request ->
          respond(
              content = "",
              status = HttpStatusCode.Found,
              headers = headersOf(HttpHeaders.Location, "http://127.0.0.1:9/private")
          )
      }
      val requester = HttpRequesterImpl(HttpClient(engine))
      val result = requester.execute(HttpRequestSpec(url = "https://example.com/redirect", method = "GET"))
      assertTrue(result is HttpResult.Blocked || result.finalUrl?.contains("127.0.0.1") != true)
  }
  ```
  Adjust the assertion to match `HttpRequesterImpl`'s actual return type once Step 1 confirms it.

- [ ] **Step 3: Run test to verify it fails**
  Run: `./gradlew :feature:httptester:testDebugUnitTest --tests "*HttpRequesterImplTest*"`
  Expected: FAIL — the mock redirect is followed and the assertion trips.

- [ ] **Step 4: Fix the client configuration**
  In the `HttpClient(CIO) { ... }` builder, disable automatic redirects:
  ```kotlin
  install(HttpRedirect) {
      checkHttpMethod = false
  }
  ```
  then explicitly set `followRedirects = false` on the engine config, or — if Ktor's CIO engine in the pinned version does not expose a per-request override — read the `Location` header on a 3xx response manually, run the resolved host through `SsrfGuard.isPrivateOrLoopback` before issuing the follow-up request, and cap the number of hops (e.g. max 3) to avoid redirect loops. Whichever approach: the guard must execute again for every hop, not just the first request.

- [ ] **Step 5: Run test to verify it passes**
  Run: `./gradlew :feature:httptester:testDebugUnitTest --tests "*HttpRequesterImplTest*"`
  Expected: PASS

- [ ] **Step 6: Run full module test suite**
  Run: `./gradlew :feature:httptester:testDebugUnitTest`
  Expected: all PASS, no regressions to existing HTTP tester tests

- [ ] **Step 7: Commit**
  ```bash
  git add feature/httptester/src/main/kotlin/com/ventouxlabs/netlens/feature/httptester/engine/HttpRequesterImpl.kt \
          feature/httptester/src/test/kotlin/com/ventouxlabs/netlens/feature/httptester/engine/HttpRequesterImplTest.kt \
          feature/httptester/build.gradle.kts
  git commit -m "fix: prevent SSRF via HTTP redirect in HTTP Tester"
  ```

### Task 2: Audit shared Ktor client config for the same redirect gap (Finding 2)

**Files:**
- Read/inspect: DI module providing `@IpInfoHttpClient` (locate via `grep -r "IpInfoHttpClient" feature/ipinfo`)
- Modify only if the same unguarded-redirect gap is confirmed

- [ ] **Step 1: Locate the `@IpInfoHttpClient`-qualified `HttpClient` provider** and read its builder block in full.
- [ ] **Step 2: Confirm all call sites using this client only ever call fixed, hardcoded hosts** (not user input) — grep for usages of the qualifier across `feature/ipinfo`.
- [ ] **Step 3: If confirmed fixed-host-only**, add a one-line comment at the provider noting why redirect-following is safe here (fixed host, no user input reaches it), so a future contributor adding a user-supplied-URL call site knows to route through `SsrfGuard` first. No functional change needed.
- [ ] **Step 4: If any call site does pass user input to this client**, apply the same fix as Task 1 (disable auto-redirect, re-validate via `SsrfGuard` per hop).
- [ ] **Step 5: Commit** (only if a change was made):
  ```bash
  git add feature/ipinfo/...
  git commit -m "docs: document SSRF-safety rationale for IpInfo Ktor client" # or "fix: ..." if a real gap was found
  ```

---

## Phase 2 — High: Feature Parity

### Task 3: Add result export to monitor, posture, vpnstatus, wifiaudit, wol (Finding 3)

**Files (per module, repeat pattern 5x):**
- Modify: `feature/<module>/src/main/kotlin/com/ventouxlabs/netlens/feature/<module>/<Module>ViewModel.kt`
- Modify: `feature/<module>/src/main/kotlin/com/ventouxlabs/netlens/feature/<module>/<Module>Screen.kt`
- Test: `feature/<module>/src/test/kotlin/com/ventouxlabs/netlens/feature/<module>/<Module>ViewModelTest.kt`

**Interfaces:**
- Consumes: `ResultExporter.shareAsText(context, title, text)` / `ResultExporter.copyToClipboard(context, text)` from `core:network/export/ResultExporter.kt`; `LocalProStatus.current` for Pro gating
- Produces: `fun buildExportText(): String` on each ViewModel

- [ ] **Step 1: Read one existing implementation as the reference pattern** — `feature/monitor` peers already have working examples; read `feature/wol/../WolViewModel.kt` alongside a module that already implements this (e.g. `feature/ping/../PingViewModel.kt`'s `buildExportText()`) to confirm the exact signature and serialization style to mirror.

- [ ] **Step 2: For each of the 5 modules, write a failing test first**, asserting `buildExportText()` returns a string containing the key fields for that tool's current `UiState` (e.g. for WoL: target MAC, broadcast IP, port, last-send result and timestamp; for Posture: overall score and each factor's pass/fail; for VpnStatus: active/inactive state and interface name; for WifiAudit: each `AuditFinding`'s severity and description; for Monitor: each endpoint's URL, status, and latency history entry). Example shape (WoL):
  ```kotlin
  @Test
  fun `buildExportText includes target and last result`() {
      val viewModel = WolViewModel(fakeWolSender, fakeSavedTargetsRepository)
      viewModel.onTargetChanged(mac = "AA:BB:CC:DD:EE:FF", broadcastIp = "192.168.1.255", port = 9)
      viewModel.sendPacket()
      val text = viewModel.buildExportText()
      assertTrue(text.contains("AA:BB:CC:DD:EE:FF"))
      assertTrue(text.contains("192.168.1.255"))
  }
  ```

- [ ] **Step 3: Run each new test to verify it fails** with "unresolved reference: buildExportText".

- [ ] **Step 4: Implement `buildExportText()` on each ViewModel**, serializing the current `UiState` to a plain-text string in the same style as the 15 existing implementations (labelled lines, no JSON) — do not reuse `netlog`'s `buildExportJson()` style, since that is itself flagged as inconsistent (Finding 4).

- [ ] **Step 5: Run tests to verify they pass.**

- [ ] **Step 6: Wire the Share/Copy `IconButton`s into each Screen's `TopAppBar`**, choosing the Pro-gating pattern that matches each screen's existing architecture:
  - Monitor, Posture, VpnStatus, WifiAudit, Wol are all single-composable screens (no separate `Content` composable receiving callbacks) — use the **direct `if (isPro)`** pattern: read `LocalProStatus.current` in the screen composable, wrap the Share `IconButton` in `if (isPro) { ... }`, matching the 11 screens already using this pattern (DNS, HTTP, IpCalc, IpInfo, Ping, PortScan, SpeedTest, TLS, Traceroute, WHOIS, WiFi). Confirm this against each screen's actual structure in Step 1's reading before assuming — if any of the 5 turns out to have a separate `Content` composable, use the nullable-lambda pattern instead (LanScan/mDNS precedent).

- [ ] **Step 7: Run full module test suites for all 5 modules:**
  Run: `./gradlew :feature:monitor:testDebugUnitTest :feature:posture:testDebugUnitTest :feature:vpnstatus:testDebugUnitTest :feature:wifiaudit:testDebugUnitTest :feature:wol:testDebugUnitTest`
  Expected: all PASS

- [ ] **Step 8: Commit** (one commit per module keeps this reviewable):
  ```bash
  git add feature/wol/...
  git commit -m "feat: add result export to Wake-on-LAN"
  # repeat for monitor, posture, vpnstatus, wifiaudit
  ```

### Task 4: Pro-gate netlog's Share button (Finding 4)

**Files:**
- Modify: `feature/netlog/src/main/kotlin/com/ventouxlabs/netlens/feature/netlog/NetLogScreen.kt:94-108`
- Test: `feature/netlog/src/test/kotlin/com/ventouxlabs/netlens/feature/netlog/NetLogScreenTest.kt` (create if no Compose UI test exists yet for this screen; otherwise extend it)

- [ ] **Step 1: Read `NetLogScreen.kt` in full** to confirm current structure around the Share `IconButton` (lines 94-108) and how `LocalProStatus` is imported/used elsewhere in the app (e.g. `feature/ping/../PingScreen.kt`).

- [ ] **Step 2: Wrap the existing Share `IconButton` in `if (isPro)`**, reading `val isPro by LocalProStatus.current.isPro.collectAsState()` (or the exact accessor pattern used in `PingScreen.kt`) at the top of the composable, keeping the existing `uiState.events.isNotEmpty()` guard as an additional condition:
  ```kotlin
  val isPro by LocalProStatus.current.isPro.collectAsState()
  // ...
  if (isPro && uiState.events.isNotEmpty()) {
      IconButton(onClick = { /* existing share logic */ }) { /* existing icon */ }
  }
  ```

- [ ] **Step 3: Verify manually or via a Compose UI test** that the Share icon is absent when `LocalProStatus` reports `isPro = false`, present when `true` and events exist.

- [ ] **Step 4: Run:** `./gradlew :feature:netlog:testDebugUnitTest`
  Expected: PASS

- [ ] **Step 5: Commit:**
  ```bash
  git add feature/netlog/src/main/kotlin/com/ventouxlabs/netlens/feature/netlog/NetLogScreen.kt
  git commit -m "fix: gate network log export behind Pro status"
  ```

### Task 5: Wire SpeedTest history into the combined History aggregator (Finding 5)

**Files:**
- Modify: `core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/repository/HistoryRepository.kt:33-90`
- Test: `core/data/src/test/kotlin/com/ventouxlabs/netlens/core/data/repository/HistoryRepositoryTest.kt`

- [ ] **Step 1: Read `HistoryRepository.kt` in full**, specifically how the other 11 DAOs are combined in `CombinedHistoryResults`, `allRecent`, and `searchAll`, and read `SpeedTestHistoryDao`'s query signatures to confirm they match the shape expected (a `Flow`/`suspend fun getAll()`-style entry point).

- [ ] **Step 2: Write a failing test** asserting a speed-test history entry inserted via `SpeedTestHistoryDao` appears in `HistoryRepository.allRecent()`'s result set alongside entries from other DAOs (use existing test fakes/in-memory Room DB setup from `HistoryRepositoryTest.kt` if it exists, or the pattern used in another `core:data` test).

- [ ] **Step 3: Run test, confirm it fails** (speed-test entry absent from combined results).

- [ ] **Step 4: Add `SpeedTestHistoryDao` as a 12th source** in `HistoryRepository`, following the exact merge/mapping pattern used for the existing 11 DAOs (same `combine`/`flatMapLatest` structure, same sealed-class-or-common-model mapping if one exists).

- [ ] **Step 5: Run test, confirm it passes.**

- [ ] **Step 6: Run:** `./gradlew :core:data:testDebugUnitTest`
  Expected: PASS

- [ ] **Step 7: Commit:**
  ```bash
  git add core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/repository/HistoryRepository.kt
  git commit -m "fix: include speed test runs in combined history timeline"
  ```

---

## Phase 3 — Medium: Correctness & Consistency

### Task 6: Replace Posture's hardcoded `untrustedNetwork = true` stub (Finding 6)

**This task requires a product decision before implementation** — do not silently pick a heuristic.

- [ ] **Step 1: Read `PostureViewModel.kt` and `PostureScoreEngine.kt` in full** to see exactly how `untrustedNetwork` feeds the score, and what signals are already available on the injected dependencies (e.g. does the ViewModel already have a `ConnectivityManager` or `NetworkMonitor` reference from `core:network` it could read `NetworkCapabilities.NET_CAPABILITY_VALIDATED` / VPN transport / metered status from?).
- [ ] **Step 2: Present options to the user/product owner** — e.g. (a) trust = has a VPN active, (b) trust = user-maintained allowlist of network SSIDs/BSSIDs, (c) trust = not-a-public-hotspot heuristic via `NetworkCapabilities.NET_CAPABILITY_NOT_METERED` — before writing code, since this changes a user-facing score's meaning.
- [ ] **Step 3: Once a signal is chosen**, write a failing test asserting `untrustedNetwork` reflects that signal (e.g. `false` when VPN capability is present in a fake `NetworkMonitor`), implement, and verify.
- [ ] **Step 4: Run:** `./gradlew :feature:posture:testDebugUnitTest`
- [ ] **Step 5: Commit:**
  ```bash
  git add feature/posture/...
  git commit -m "fix: derive network-trust signal instead of hardcoded stub in posture score"
  ```

### Task 7: Fix cancellation for blocking sockets in SsdpScanner and NetBiosProber (Finding 7)

**Files:**
- Modify: `feature/lanscan/src/main/kotlin/com/ventouxlabs/netlens/feature/lanscan/engine/SsdpScanner.kt:38-68`
- Modify: `feature/lanscan/src/main/kotlin/com/ventouxlabs/netlens/feature/lanscan/engine/NetBiosProber.kt:19-38`
- Test: `feature/lanscan/src/test/kotlin/com/ventouxlabs/netlens/feature/lanscan/engine/SsdpScannerTest.kt`, `NetBiosProberTest.kt`

- [ ] **Step 1: Read both files in full** to confirm the exact `DatagramSocket` lifecycle (where it's opened, where `receive()` blocks, where it's currently closed).

- [ ] **Step 2: Write a failing test** that starts a scan/probe, cancels the coroutine `Job` shortly after starting (before the socket's own timeout would fire), and asserts the call returns/throws promptly (e.g. within 100ms) rather than waiting out the full `soTimeout`.

- [ ] **Step 3: Run test, confirm it fails** (or is flaky/slow — takes the full timeout to return).

- [ ] **Step 4: Add cancellation-triggered socket close.** Wrap the blocking section so the socket closes when the coroutine is cancelled:
  ```kotlin
  suspendCancellableCoroutine<Result> { cont ->
      val socket = DatagramSocket().apply { soTimeout = TIMEOUT_MS }
      cont.invokeOnCancellation { socket.close() }
      try {
          // existing blocking receive() logic, using `socket`
      } catch (e: SocketException) {
          if (cont.isActive) cont.resume(/* empty/cancelled result */)
      }
  }
  ```
  Adapt to each file's actual control flow (loop vs. single receive) confirmed in Step 1 — the key invariant is: cancellation must close the socket so `receive()` unblocks via `SocketException` rather than waiting for `soTimeout`.

- [ ] **Step 5: Run test, confirm it passes** (returns promptly after cancellation).

- [ ] **Step 6: Run:** `./gradlew :feature:lanscan:testDebugUnitTest`
  Expected: PASS, no regression to existing scan-completion tests

- [ ] **Step 7: Commit:**
  ```bash
  git add feature/lanscan/src/main/kotlin/com/ventouxlabs/netlens/feature/lanscan/engine/SsdpScanner.kt \
          feature/lanscan/src/main/kotlin/com/ventouxlabs/netlens/feature/lanscan/engine/NetBiosProber.kt \
          feature/lanscan/src/test/...
  git commit -m "fix: close sockets on cancellation so LAN scan stop is immediate"
  ```

### Task 8: Fix hardcoded contentDescription strings (Finding 8)

**Files:**
- Modify: `feature/portscan/src/main/kotlin/com/ventouxlabs/netlens/feature/portscan/PortScanScreen.kt:90`
- Modify: `feature/wol/src/main/kotlin/com/ventouxlabs/netlens/feature/wol/WolScreen.kt:88`

- [ ] **Step 1: Confirm `R.string.navigate_back` exists in both modules' `strings.xml`** (or a shared module) — grep `navigate_back` across `feature/portscan/src/main/res` and `feature/wol/src/main/res`. If either module lacks the string resource, add it: `<string name="navigate_back">Back</string>` to that module's `res/values/strings.xml`.
- [ ] **Step 2: Replace `contentDescription = "Back"` with `contentDescription = stringResource(R.string.navigate_back)`** in both files.
- [ ] **Step 3: Run:** `./gradlew :feature:portscan:testDebugUnitTest :feature:wol:testDebugUnitTest`
  (No behavior to unit test here beyond compilation — this is a lint-level fix. Confirm the module still builds.)
  Run: `./gradlew :feature:portscan:assembleDebug :feature:wol:assembleDebug`
  Expected: BUILD SUCCESSFUL
- [ ] **Step 4: Commit:**
  ```bash
  git add feature/portscan/src/main/kotlin/com/ventouxlabs/netlens/feature/portscan/PortScanScreen.kt \
          feature/wol/src/main/kotlin/com/ventouxlabs/netlens/feature/wol/WolScreen.kt
  git commit -m "fix: use string resource for back button content description"
  ```

### Task 9: Migrate Posture/VpnStatus to shared LocalStatusColors (Finding 9)

**Files:**
- Modify: `feature/posture/src/main/kotlin/com/ventouxlabs/netlens/feature/posture/PostureScreen.kt:64-69,196-198`
- Modify: `feature/vpnstatus/src/main/kotlin/com/ventouxlabs/netlens/feature/vpnstatus/VpnStatusScreen.kt:42-45`
- Reference: `core/ui/src/main/kotlin/com/ventouxlabs/netlens/core/ui/StatusColors.kt`

- [ ] **Step 1: Read `StatusColors.kt` in full** to confirm the exact token names (`LocalStatusColors.current.pass`, `.warn`, `.fail`, etc.) and how `ipinfo`/`lanscan`/`portscan` consume it (read one, e.g. `feature/portscan/.../PortScanScreen.kt:241,276,364`).
- [ ] **Step 2: In Posture and VpnStatus, delete the local `private val StatusGreen`/etc. `Color(0xFF...)` constants** and replace each usage with the equivalent `LocalStatusColors.current.<token>`, matching colors 1:1 (e.g. `StatusGreen` → `.pass`) based on what each constant represents.
- [ ] **Step 3: Manually verify (or add a screenshot test if the project has one) that both screens render correctly in light and dark theme** after the change — this is the actual bug being fixed (colors not adapting to theme).
- [ ] **Step 4: Run:** `./gradlew :feature:posture:testDebugUnitTest :feature:vpnstatus:testDebugUnitTest`
- [ ] **Step 5: Commit:**
  ```bash
  git add feature/posture/src/main/kotlin/com/ventouxlabs/netlens/feature/posture/PostureScreen.kt \
          feature/vpnstatus/src/main/kotlin/com/ventouxlabs/netlens/feature/vpnstatus/VpnStatusScreen.kt
  git commit -m "fix: use shared LocalStatusColors token in posture and VPN status screens"
  ```

### Task 10: Trace and, if missing, wire up `HistoryRepository.clearOlderThan()` (Finding 10)

- [ ] **Step 1: Search the codebase for any call site** — `grep -rn "clearOlderThan" --include=*.kt` across the whole repo (search beyond `core:data`, e.g. `app/`, WorkManager workers, `NetLensApplication.onCreate()`).
- [ ] **Step 2a: If a caller exists**, document it with a one-line comment at `clearOlderThan`'s declaration noting where/when it's invoked, and close this task with no functional change.
- [ ] **Step 2b: If no caller exists**, add one. Simplest correct option: invoke `historyRepository.clearOlderThan(days = 90)` (or a value confirmed with the user) from `NetLensApplication.onCreate()` inside a `applicationScope.launch(Dispatchers.IO)` block, guarded so it runs at most once per app process start.
- [ ] **Step 3: If Step 2b applies, write a test** confirming `clearOlderThan` is invoked on `Application` creation (or, if that's hard to test directly, a unit test on whatever wrapper function you introduce).
- [ ] **Step 4: Run:** `./gradlew :app:testDebugUnitTest :core:data:testDebugUnitTest`
- [ ] **Step 5: Commit:**
  ```bash
  git commit -m "fix: invoke history pruning on app startup" # only if Step 2b applied
  ```

---

## Phase 4 — Low: Polish / Documentation

### Task 11: Fix CLAUDE.md module-count doc drift and log remaining backlog items (Finding 11)

- [ ] **Step 1:** In `CLAUDE.md`, find the sentence documenting "All 13 tool ViewModels ... expose `fun buildExportText()`" (see "Result export pattern" section) and update the count/list to include `celltower` and `dnsleak` (15 total), and add the 5 modules from Task 3 once Phase 2 lands.
- [ ] **Step 2:** Add a backlog note (in `.claude/PRPs/plans/` or wherever the project tracks backlog, e.g. a new entry in `docs/CODEMAPS/features.md` if that's where tool-level notes live) for: DNS SRV/PTR/CAA record types; Ping interval/packet-size options (flag as "likely won't-fix — Android raw ICMP requires root"); a follow-up audit pass on celltower, dnsleak, wifiaudit, vpnstatus, and remaining `*Screen.kt` files for typography/accessibility.
- [ ] **Step 3: Commit:**
  ```bash
  git add CLAUDE.md docs/CODEMAPS/features.md
  git commit -m "docs: correct export-module count and log audit follow-up backlog"
  ```

---

## Suggested Execution Order

1. Task 1 (critical security) — ship independently and fast.
2. Task 2 (audit-only, cheap) — can run in parallel with Task 1.
3. Tasks 3-5 (feature parity) — independent of each other, can run in parallel across 3 subagents/branches.
4. Tasks 7-9 (medium consistency fixes) — independent of each other and of Phase 2, can run in parallel.
5. Task 6 (posture trust signal) — blocked on a product decision; surface this to the user before starting.
6. Task 10 (history pruning trace) — quick, can run any time.
7. Task 11 (docs) — do last, once Task 3's module list is final.

**Branch suggestion:** one branch per task (or per phase, if reviewing in bulk), e.g. `fix/httptester-ssrf-redirect`, `feat/export-parity-5-modules`, `fix/posture-status-colors`.
