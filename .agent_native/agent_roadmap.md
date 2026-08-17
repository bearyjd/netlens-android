# Agent-Native Roadmap

Goal: an AI agent should be able to pick up a raw bug report or feature request against
NetLens and autonomously reproduce, implement, test, and verify it with minimal human
input. This audit maps where that breaks down today and what to fix, ordered by
**Human-Attention-Saved per Unit of Effort** — cheap fixes that remove a chokehold a
human currently has to unblock, ranked above expensive fixes that save less.

Audit date: 2026-07-07. Scope: `app/`, `core/*`, `feature/*`, `widget/`, `build-logic/`,
`.github/workflows/`, `docs/`, `fastlane/`, `fdroid/`. No Gradle builds were run; findings
are from static inspection of build files, source, and test trees.

---

## Top 5 — immediately actionable

### 1. Fix CLAUDE.md's stale CI-scope claim (effort: 5 min, saves: every future session) — DONE (2026-07-07)

**Problem:** `CLAUDE.md` (pre-existing, line 20) states CI "currently tests only:
`:core:network`, `:feature:lanscan`, `:feature:whois`, `:feature:monitor`". This is
false — `.github/workflows/ci.yml` runs `testFossDebugUnitTest testDebugUnitTest`, which
executes unit tests in **every** module that has a `src/test` tree (23 of 27
core+feature+app+widget modules currently have tests).

> **Update 2026-08-04:** the CI command quoted above is itself now out of date. `ci.yml`
> runs **three** tasks — `testFossDebugUnitTest testGplayDebugUnitTest testDebugUnitTest`.
> The gplay task was added because it is the only one reaching `src/testGplay`, where
> `GplayProStatusTest`'s 12 billing tests live; they had been written but never executed.
> Every module now has tests. Run all three locally — a two-task run skips those 12
> silently rather than failing. An agent that trusts the stale
claim will assume most modules are untested by CI and either skip writing tests
(wrongly believing they won't run) or waste effort re-verifying test execution that CI
already guarantees.

This was already flagged in `.claude/PRPs/HANDOFF-fable-audit-2026-07-04.md` (Phase 4,
Task 11) but never applied because Phases 2-4 of that plan were deferred.

**Fix:** Already applied in this pass — see the "Build Commands" section update in
`CLAUDE.md`.

**Acceptance criteria:** `CLAUDE.md`'s CI description matches `.github/workflows/ci.yml`
verbatim (task names and scope).

---

### 2. Give `core:oui` and `core:billing` unit test coverage (effort: 1-2 hrs, saves: hours of manual QA per money/data-path change) — DONE (2026-07-07)

**Status:** Completed. `core/oui/src/main/kotlin/.../OuiLookupImpl.kt` had its parsing logic extracted
into two testable companion functions (`parseOuiTable`, `normalizePrefix`) — same pattern as
`ArpTableReaderImpl.parseArpTable` in lanscan — and covered by
`core/oui/src/test/kotlin/.../OuiLookupImplTest.kt` (9 tests: known-vendor lookup, unknown-prefix
fallback, malformed lines, case-insensitivity, dash/colon separators). `app/src/testGplay/.../GplayProStatusTest.kt`
already existed with full reconnect-counter coverage (including the exact max-3-attempts assertion this
item called for) — no gap there. Only `FossProStatus` (in `app/src/foss/`, not `core:billing` itself —
the flavor implementations live in `app/src/{foss,gplay}`, `core:billing` only holds the `ProStatus`
interface) was untested; added `app/src/testFoss/.../FossProStatusTest.kt` (3 tests). All new tests verified
via `./gradlew :core:oui:testDebugUnitTest :app:testFossDebugUnitTest` — passing.



**Problem:** `core/oui` (MAC vendor lookup — pure function, zero Android framework
deps, used by `feature:lanscan` and `feature:wifiaudit`) and `core/billing` (the
`ProStatus` interface plus flavor-specific `FossProStatus`/`GplayProStatus`, i.e. the
entire monetization path) both have **zero** test files. `core:oui`'s
`OuiLookupImpl.kt` (`core/oui/src/main/kotlin/com/ventouxlabs/netlens/core/oui/OuiLookupImpl.kt`)
is exactly the kind of pure-logic module the repo's own testing conventions (JUnit 5,
hand-written fakes) are built for — there's no reason for it to be untested. Billing
is higher-risk: `GplayProStatus`'s reconnect-counter (max 3 attempts, per
`CLAUDE.md`) and `EncryptedSharedPreferences`-backed purchase state are exactly the
kind of state machine that regresses silently.

**Fix:**
- `core/oui`: add `OuiLookupImplTest.kt` covering known-vendor lookup, unknown-prefix
  fallback, malformed MAC input, case-insensitivity.
- `core/billing`: add a test for `FossProStatus` (trivial — always-Pro invariant) and,
  for `GplayProStatus`, extract the reconnect-counter logic behind a seam that can be
  driven by a `FakeBillingClientWrapper` (there's already a real
  `BillingClientWrapper` built "for testability" per `CLAUDE.md:40` — confirm it's
  actually exercised by a test, not just designed to be).

**Acceptance criteria:** `./gradlew :core:oui:testDebugUnitTest
:core:billing:testDebugUnitTest` passes with new tests; reconnect-counter max-3-attempts
behavior has an explicit test asserting it stops at 3, not N.

---

### 3. Add a hardware-seam + fakes for `feature:celltower` and `feature:wifiaudit` (effort: half a day, saves: the two modules currently un-verifiable by any agent) — DONE (celltower/wifiaudit 2026-07-07; history/widgetsettings since, confirmed 2026-08-08)

**Status:** Complete. `celltower` and `wifiaudit` were done 2026-07-07; `history` and
`widgetsettings`, recorded below as skipped, were finished later — **that correction block is
itself now out of date and is kept only for the reasoning it contains.** See "Correction, corrected"
after it. This item's own problem statement was stale on one point: `CellTowerReader` and
`WifiInfoReader` **already had interface seams** (`feature/celltower/.../engine/CellTowerEngine.kt`
defines `interface CellTowerReader` with `CellTowerReaderImpl`; `feature/wifiaudit/.../engine/WifiInfoReader.kt`
likewise) — what was actually missing was just `Fake*` doubles and tests exercising them, not the
interface extraction itself. Added:
- `feature/celltower/src/test/kotlin/.../engine/FakeCellTowerReader.kt`
- `feature/celltower/src/test/kotlin/.../CellTowerViewModelTest.kt` (11 tests)
- `feature/celltower/src/test/kotlin/.../model/SignalQualityTest.kt` (8 tests, boundary-value coverage of `rsrpQuality`/`rssiQuality`)
- `feature/wifiaudit/src/test/kotlin/.../engine/FakeWifiInfoReader.kt`
- `feature/wifiaudit/src/test/kotlin/.../FakeNetworkEventDao.kt`
- `feature/wifiaudit/src/test/kotlin/.../engine/WifiAuditEngineTest.kt` (21 tests covering every finding branch: WPA3/WPA2/WPA/WEP/open encryption, signal thresholds, hidden SSID, WPS, TKIP-only vs mixed, band, link speed, and severity ordering)
- `feature/wifiaudit/src/test/kotlin/.../WifiAuditViewModelTest.kt` (6 tests, including the reader-throws error path)

All verified via `./gradlew :feature:celltower:testDebugUnitTest :feature:wifiaudit:testDebugUnitTest` — passing (one test bug was found and fixed along the way: a `StateFlow` conflates equal consecutive values, so asserting a second emission after `onPermissionResult(false)` — which produces a state identical to the initial one — timed out; fixed to assert `expectNoEvents()` instead).

**Correction — `history` and `widgetsettings` were NOT done, and the roadmap's characterization of
them as "no excuse, same pattern as 19 other tested ViewModels" does not hold on inspection:**
- `WidgetSettingsViewModel` is an `AndroidViewModel(application)` that reads a real `Application`
  `Context` directly and drives `WidgetPreferencesRepository.observe(context)`, a DataStore-backed
  singleton with no interface seam at all. This is a Robolectric-class gap (see Backlog item below),
  not a same-day fake-and-test job.
- `HistoryViewModel` depends on `HistoryRepository`, a **concrete** `@Singleton class` (not an
  interface) that takes 11 Room DAOs plus a `NetLensDatabase` and calls `database.withTransaction { }`
  in `clearAll()`/`clearOlderThan()`. Faking this properly means either faking all 11 DAOs and finding
  a way around `withTransaction` (a `RoomDatabase` extension that needs a real Room instance), or
  standing up an in-memory Room DB — both meaningfully bigger than the celltower/wifiaudit fakes, and
  arguably belong with the Robolectric/Room-testing backlog items below rather than this one.

Given the no-commit, time-boxed constraints of this pass, these two were left for a follow-up. If
picked up next: `history` is the better next target (Room DAOs are plain interfaces, so an in-memory
`Room.inMemoryDatabaseBuilder()`-backed `HistoryRepository` — or Fake DAOs plus accepting
`withTransaction` needs a real `RoomDatabase` subclass — is tractable without Robolectric);
`widgetsettings` genuinely needs the Robolectric backlog item first.

**Correction, corrected (verified 2026-08-08): both were done, and neither needed Robolectric.**
The block above is wrong on its conclusion while still being right on its diagnosis — which is why
it is kept rather than deleted, because the two seams it called for are exactly what got built:

- `history` — `HistoryRepository` was split into an interface plus `HistoryRepositoryImpl`
  (`core/data/.../repository/HistoryRepository.kt:55`, `@Binds` in `core/data/di/RepositoryModule.kt`).
  The ViewModel now depends on the interface, so `FakeHistoryRepository` stands it up in a plain JVM
  test — `HistoryViewModelTest` has 13. The concrete-class dependency was the whole blocker; the 11
  DAOs and `withTransaction` never had to be faked at all.
- `widgetsettings` — `WidgetSettingsViewModelTest` covers it with 3 tests. The seam this block
  described as absent now exists: `UserPreferencesRepository` takes an injectable
  `DataStore<Preferences>` (use `FakeDataStore`), and `Application()` is directly constructible in a
  JVM test because `:app` sets `unitTests.isReturnDefaultValues = true`. **That last detail is the
  transferable one** — it is why this was never a Robolectric-class problem, and it applies to
  anything else here that looks `Context`-bound.

**Acceptance criteria met.** Every module with a `src/main` Kotlin tree now has a `src/test` except
`core:billing` — an interface-only module whose flavor implementations live in `app/src/{foss,gplay}`
and are tested there (`FossProStatusTest`, `GplayProStatusTest`) — and `core:network-testing`, which
is itself a test-double module. Robolectric remains at zero usage repo-wide, which is now a
deliberate outcome rather than a gap: the interface-seam discipline covered every case this item
raised. The Robolectric backlog entry below still stands on its own merits, but nothing in item 3
is waiting on it.

**Problem:** `celltower`, `history`, `widgetsettings`, and `wifiaudit` are the only 4
feature modules with **no test directory at all**. `history` and `widgetsettings` are
pure ViewModel/state-flow modules (no excuse — same pattern as the 19 other tested
feature ViewModels) and are cheap to fix. `celltower` and `wifiaudit` are harder: their
engines (`feature/celltower/.../engine/CellTowerEngine.kt`,
`feature/wifiaudit/.../engine/WifiInfoReader.kt`) read directly from
`TelephonyManager` / `WifiManager` system services with no interface seam — unlike
`feature:lanscan`'s engines (`ArpTableReader`, `NetBiosProber`, `SsdpScanner`,
`SubnetScanner`), which already have `Fake*` counterparts — these now live in
`core:scan-testing` (consumed via `testImplementation`), **not** under
`feature/lanscan/src/test/`, and must not be copied into a consuming module: every
copy made so far drifted weaker than the original. There is also **zero Robolectric usage
anywhere in the repo** (verified via repo-wide grep) — meaning any code that touches
`Context`, `WifiManager`, `TelephonyManager`, or a real `Room` instance is currently
verifiable only on a physical device or emulator, which an autonomous agent doesn't
have.

**Fix:**
- `history`, `widgetsettings`: add ViewModel unit tests now — trivial, same pattern as
  every other feature.
- `celltower`, `wifiaudit`: extract `WifiInfoReader` and the telephony-reading half of
  `CellTowerEngine` behind interfaces (mirroring `ArpTableReader`'s shape in lanscan),
  add `Fake*` test doubles, and cover the parsing/scoring logic (`SignalQuality`,
  `AuditSeverity`, `AuditFinding` derivation) with unit tests against the fakes. This
  doesn't require Robolectric — it just needs the same interface-seam discipline
  `lanscan` already uses.
- Longer-term (log as backlog, not top-5): introduce Robolectric to `build-logic`'s
  `netlens.android.library` convention plugin as an opt-in test dependency, so
  Context-dependent code (Room DAOs, DataStore, `ConnectivityManager` polling) becomes
  unit-testable without a device. This is the single highest-leverage structural fix
  for verification gaps but is bigger than a top-5 item — see Backlog below.

**Acceptance criteria:** All 27 modules with `src/main` Kotlin have a `src/test`
directory with at least one passing test; `WifiInfoReader` and `CellTowerEngine`'s
non-system-service logic reaches ≥1 fake-driven test each.

---

### 4. Un-silo the prior-audit trail — commit durable planning artifacts instead of gitignoring them (effort: 30 min, saves: re-discovery cost on every fresh clone/session) — REJECTED (2026-08-08, human decision)

**Status:** Decided against, deliberately. The Fix below was **not** adopted; the repo went the
other way. `.claude/PRPs/` stays gitignored, and the 51 files that were still tracked from before
`.gitignore:34` existed were untracked (`git rm --cached`, files kept on disk). The half-tracked
state is what this resolves: committed plans kept updating while every new plan, report and review
was invisible, so a fresh clone got an arbitrary 2026-era slice and nothing since.

**Do not re-propose committing `.claude/PRPs/`.** If a planning artifact matters beyond the session
that wrote it, the destination is `docs/` — `docs/HANDOFF.md` is the living example and is where
this trail now lands. Promoting a file out of `.claude/PRPs/` by hand remains fine; the blanket
policy is what was rejected.

**The three files worth keeping were rescued into `docs/` on 2026-08-08, and `.claude/PRPs/` was
then deleted entirely** (74 files: completed per-tool plans, per-PR reviews of long-merged PRs,
April/May widget reports). What survived, and where it now lives:

| Was | Now |
|---|---|
| `.claude/PRPs/plans/fable-audit-fixes.plan.md` | `docs/backlog/fable-audit-fixes.plan.md` |
| `.claude/PRPs/HANDOFF-fable-audit-2026-07-04.md` | `docs/backlog/fable-audit-handoff-2026-07-04.md` |
| `.claude/PRPs/reports/cso-security-audit.md` | `docs/security-audit-2026-04-21.md` |

So the re-discovery problem described below is **solved for the parts that mattered** — Phases 2-4
and the competitor research are now tracked and greppable, which is what this item actually wanted.
The rejected part was the mechanism, not the goal: `docs/`, not an un-ignored `.claude/PRPs/`.

Two notes for whoever reads those files. The security audit is dated **2026-04-21** in its header
despite a 2026-08-05 mtime — the mtime is a touch, not an edit, and every finding in it is marked
FIXED, so treat it as history rather than an open list. And the 51 files that were tracked before
the untrack are recoverable at `git show 651be87^:<path>`; the other 74 were never tracked and are
gone.

**Problem:** `.claude/PRPs/reports/` and `.claude/PRPs/plans/` are gitignored
(`.gitignore:23`). This repo has a rich history of prior audits, phased fix plans, and
architectural decisions (`fable-audit-2026-07-04.md`, `fable-audit-fixes.plan.md`,
`cso-security-audit.md`, `netlens-backlog-and-widget-redesign-report.md`, etc.) — but
none of it is visible to an agent working from a fresh clone or a different machine.
`.claude/PRPs/HANDOFF-fable-audit-2026-07-04.md` documents Phases 2-4 of a fix plan
that were deferred and never executed, plus an entire bucket of competitor-feature
research that (per the handoff's own admission) "exists in the conversation
transcript" and nowhere else retrievable. An agent picking up "what should I build
next" today has no way to find any of this — it's tribal knowledge trapped in one
person's local checkout.

**Fix:** Do not blanket-uncomment the gitignore (some of `.claude/PRPs/plans/` and
`reports/` genuinely are ephemeral scratch work). Instead: (a) force-add the specific
files that represent durable, still-relevant decisions —
`HANDOFF-fable-audit-2026-07-04.md`, `fable-audit-fixes.plan.md`,
`fable-audit-2026-07-04.md` — into a committed location, e.g. `docs/decisions/` or
`docs/backlog/`, since `.claude/PRPs/completed/` already establishes the precedent of
promoting finished plans out of the gitignored working area; (b) fold the
undocumented competitor-research bucket (Fing/PingTools/WiFiman comparison, listed in
the handoff) into a single committed `docs/backlog/competitor-features.md` before it's
lost for good — it currently exists only in a conversation transcript that will age
out of context.

**Acceptance criteria:** `git log --follow` on the new committed file(s) shows the
content is now trackable; the Phase 2-4 backlog (result-export for 5 modules,
netlog Pro-gating fix, SpeedTest history wiring, Posture trust-model decision, etc.)
is discoverable by `grep`ing the repo, not by asking a human who remembers the old
conversation.

---

### 5. Extract shared network-primitive fakes into a `testFixtures` source set (effort: half a day, saves: reinvented mocks on every future feature) — DONE (2026-07-30)

**Done as `:core:scan-testing` + `:core:data-testing`, not a `testFixtures` source set.** AGP 8.9
registers the `testFixtures` variant, but Kotlin 2.1.0 registers no Kotlin compilation for it —
`compileDebugTestFixturesJavaWithJavac` exists and `compileDebugTestFixturesKotlin` does not, so
Kotlin fixtures compile to nothing and consumers fail with `Unresolved reference`. The plain
library module this item already offered as an alternative works everywhere. Don't retry
`testFixtures` until KGP registers that compilation.

**What the migration actually found, which is the reason this mattered more than tidiness:** all
three duplicated fakes had drifted *weaker* than the originals, and a weaker double turns a red
test green.

- `:feature:devices`' `FakeOuiLookup` matched the full MAC; the real OUI database (and
  `:core:scan`'s fake) matches a three-octet prefix. Under the weak one, a caller that never
  normalised its MAC was indistinguishable from a correct one. `WatchRunnerTest` asserts `vendor`,
  so this was load-bearing — it passes against the prefix-keyed fake, which is what proves
  `WatchRunner` hands over something the real lookup could resolve.
- `:feature:wifiaudit`'s `FakeNetworkEventDao` returned `flowOf(inserted.take(limit))` from every
  query method — `types`, `hasTypeFilter`, `from` and `to` were accepted and discarded. No test
  failed because wifiaudit only asserts on the write path, which is exactly how it survived: a
  double that ignores its arguments fails nothing until someone writes the test it would have
  caught.
- `:feature:devices`' `FakeSubnetScanner` had no error-injection hook, so no devices test could
  cover a failing scan.

Both shared fakes now have their own unit tests pinning the strong behaviour (9 tests), so the
weak versions cannot come back silently. `FakeOuiLookupTest` includes an explicit
assert-the-non-behaviour case: keying the table on a full MAC must **not** match.

**The Ktor half is now done too**, as `:core:network-testing`. `:feature:httptester` and
`:feature:monitor` each carried a byte-identical 18-line `MockEngine` that 302s into a private
host and records whether anything followed — including the same comment explaining why the
redirect must keep the `https` scheme (a downgrade makes Ktor refuse for the *wrong* reason, and
the test then passes whether or not the SSRF guard exists). That is now `SsrfRedirectProbe`.

Verified non-vacuous rather than assumed: flipping `followRedirects = false` to `true` in each
module's `configureSecureDefaults()` fails exactly that module's redirect test, both before and
after the migration.

---

<details>
<summary>Original problem statement (kept for context)</summary>

**Problem:** `feature:lanscan` has 5 well-built `Fake*` engine doubles
(`FakeArpTableReader`, `FakeLanMdnsScanner`, `FakeNetBiosProber`, `FakeOuiLookup`,
`FakeSsdpScanner`) that are the best reproduction-harness pattern in the repo — but
they're private to `feature:lanscan/src/test` and can't be reused. `feature:wifiaudit`
needs an OUI lookup fake too (it has none — 0 tests), and any future feature touching
ARP/mDNS/SSDP/OUI will reinvent the same fakes from scratch. Separately,
`feature:httptester` and `feature:monitor` each hand-roll their own Ktor
`MockEngine` setup rather than sharing a canonical "mock HTTP fixture" helper, despite
solving the same problem (verifying SSRF-guard behavior on redirects, per the
`SsrfGuard.isPrivateOrLoopback()` fixes documented in the audit handoff).

**Fix:** Add a Gradle `testFixtures` source set (or a small internal
`core:network-testing` / `core:oui-testing` module) exposing `FakeOuiLookup` and a
canonical `MockEngine`-based HTTP client builder with configurable redirect/host
behavior. Migrate `feature:lanscan`'s existing fakes to be the seed content. This
directly unblocks item 3's `wifiaudit` test-doubles gap and gives every future
feature module (and every future agent bug-fix session) a canonical, already-reviewed
place to get network fixtures instead of writing bespoke ones.

**Acceptance criteria:** `feature:wifiaudit` consumes the shared `FakeOuiLookup`
instead of writing a new one; `feature:httptester` and `feature:monitor` both build
their `HttpClient(MockEngine)` test setup through the same shared helper.

</details>

**Note on the original text above:** it locates the fakes in `feature:lanscan/src/test`. They had
already moved to `core/scan/src/test` by the time this was executed, and `feature:wifiaudit` had
gained tests (item 3's pass) — its gap was a duplicated `FakeNetworkEventDao`, not a missing
`FakeOuiLookup`. The problem was real; the coordinates were stale.

---

## Backlog (lower H.A.S./effort ratio, or needs a human decision first)

- ~~**`app/src/gplay/` billing has zero tests**~~ — **this was wrong, and the truth was worse.**
  `app/src/testGplay/GplayProStatusTest.kt` exists with 12 passing tests over the purchase path,
  the reconnect cap, `ITEM_ALREADY_OWNED`, `USER_CANCELED` and acknowledgement. **CI had never
  run one of them**: `testFossDebugUnitTest` covers `src/test` + `src/testFoss`,
  `testDebugUnitTest` covers the unflavored library modules, and `:app` is flavored — reaching
  `src/testGplay` needs `testGplayDebugUnitTest`, which nothing invoked. Fixed 2026-07-31 by
  adding that task to the CI test step.
  **The transferable lesson, which is the reason this entry is kept rather than deleted:** the
  failure mode was not missing coverage, it was coverage that existed and was never enforced —
  green, committed, and invisible. Before recording any module as "untested", check whether the
  tests exist and are simply not being *run*; and when adding a flavored source set, add its task
  to `ci.yml` in the same change. Everything in `src/test*` that no CI task names is decoration.
- **Robolectric adoption — DONE, scoped to `core:data` only (2026-08-14).** Superseded the framing
  below. This is the one place in the repo Robolectric is the correct tool: real Room `@Query`
  execution and `Migration` validation need a real `Context` and real SQLite, which no interface
  seam can substitute for. It directly reopens item 3's 2026-08-08 correction, which said zero
  Robolectric usage was "a deliberate outcome rather than a gap" — that call was right for every
  case it covered (celltower, wifiaudit, history, widgetsettings all got interface seams instead)
  but wrong that it covered everything: no test anywhere exercised a real `@Query`, and all 12
  migrations (4→5 … 15→16) were untested. `core:data`'s `KnownDeviceDao.updateLastSeen`/
  `updateUserDetails` column-disjointness invariant — previously enforced only by a code comment —
  now has a real test (`KnownDeviceDaoTest`), as does the full migration chain and `MIGRATION_14_15`
  specifically (`MigrationTest`).

  **Dead end worth recording so it isn't re-attempted:** Room's Kotlin-Multiplatform JVM testing API
  (`Room.inMemoryDatabaseBuilder<T>()`, no `Context`, real SQL on the plain JVM with no Robolectric
  at all) was tried first and looked like the better answer — Android's own docs actively discourage
  Robolectric for Room in favor of it. It doesn't work here: that no-Context builder only ships in
  Room's `-jvm`-target artifact (confirmed by decompiling `room-runtime-android-2.7.2.jar` — only the
  `Context`-requiring overloads exist there), and a plain `com.android.library` module (not KMP)
  resolves the `-android` variant everywhere, including its test classpath. Bolting the `-jvm`
  coordinate on separately creates a duplicate-`androidx.room.Room`-class hazard, not a working test.
  Converting `core:data` to Kotlin Multiplatform would unlock it but is a much larger, separate
  decision — module graph, Hilt's `DataModule`, every consumer — not attempted here.

  Mechanism used instead: the classic, pre-KMP `MigrationTestHelper` constructor
  (`Instrumentation, Class<out RoomDatabase>, ...`), which Robolectric has supported for years via
  `InstrumentationRegistry` and which Room 2.7's KMP release removed — so `core:data` stays pinned to
  Room 2.6.1 deliberately, not as an oversight. New opt-in convention plugin
  `netlens.android.robolectric` (`build-logic/convention/.../AndroidRobolectricConventionPlugin.kt`),
  applied only to `core:data`; every other module stays at zero Robolectric, which remains the
  correct default per item 3's original finding.

  **`widget/` — DONE too (2026-08-14), scoped pilot.** `netlens.android.robolectric` applied to
  `widget/`. The headline fix — `refreshAllWidgets`'s cross-render bug guard — needed **no
  Robolectric at all**: its bug-class logic (never let one receiver's widget ids reach another
  receiver's widget instance) was pulled into an injectable `internal suspend fun refreshWidgets`
  (`WidgetRefresh.kt`) and pinned with a plain JUnit5 test (`WidgetRefreshTest`) using fakes —
  mirrors the repo's `ArpTableReaderImpl.parseArpTable`-style "extract the pure logic, test that
  hard" pattern. Robolectric covers what's genuinely Android-framework-bound instead: the
  `ConnectivityManager.NetworkCallback` register/unregister lifecycle including the
  private-static-companion double-registration hazard (`WidgetRefreshLifecycleTest`,
  `CompactWidgetReceiverTest` — one receiver stands in for all four, which are byte-identical
  apart from class name), and WorkManager enqueue shape via the officially-supported
  `androidx.work:work-testing` (`REPLACE`/`KEEP` policy, unique work names). Also added a trivial
  zero-cost win: `parseCapabilities` (already `internal`, pure, zero prior tests). All new tests
  mutation-checked — each one deliberately broken and confirmed to fail exactly the right test.

  **`DeeplinkAction`'s allowlist check — DONE (2026-08-14).** Turned out not to need a new Glance
  `ActionCallback` testing pattern beyond what `netlens.android.robolectric` already provides —
  `Uri.parse` is a genuine stub in the compileSdk 35 `android.jar` (confirmed via `javap`), so this
  needed Robolectric, unlike `WidgetRefreshTest`'s plain-JUnit5 path. The check
  (`uri.scheme == Deeplink.SCHEME && uri.host == Deeplink.HOST`) was pulled into a named
  `internal fun isAllowedDeeplinkUri` and covered by `DeeplinkActionTest`, including the adversarial
  case that actually matters: userinfo/authority confusion (`netlens://feature@evil.com/...` vs
  `netlens://evil.com@feature/...`) — confirmed `Uri.host` resolves the real authority host, not
  something a naive string check would be fooled by. Also confirmed (not assumed) the check is
  case-sensitive — `Uri` does not normalize scheme/host case — recorded as a fact, not "fixed."
  Mutation-checked: dropped the host check, flipped `&&`→`||`, and removed the guard from
  `onAction` entirely — each broke exactly the predicted test(s).

  **`NetworkCollector` — DONE (2026-08-15), partially.** Three pure derivations extracted to
  `internal` top-level functions and covered without Robolectric (`NetworkCollectorPureTest`):
  `cellGenerationFor` (a 15-branch constant mapping with no prior coverage), `isVpnInterfaceName`,
  and `cellularLinkSpeedMbps`. `collect()`'s reachable branches covered under Robolectric
  (`NetworkCollectorTest`): both early-return guards, cellular link-speed derivation, `isMetered`,
  all three `isCaptivePortal` combinations, VPN detection + tunnel-interface naming, and
  `dnsServers`.

  **Robolectric shadow limits, each confirmed empirically rather than assumed** — worth knowing
  before re-scoping anything else in this module:
  - `LinkProperties.interfaceName` and `setDnsServers` work. **`LinkAddress` is not constructible**
    (package-private constructor, no public `addLinkAddress`), so `localIp` and `hasIpv6` are
    untestable in a unit test.
  - **`WifiInfo` does not implement `TransportInfo` in Robolectric's SDK sandbox**, so
    `NetworkCapabilities.setTransportInfo(wifiInfo)` throws `ClassCastException`. This blocks real
    `rssi`/`linkSpeedMbps` on the WiFi branch — and it is a *different, broader* blocker than the
    one recorded below for `detectEncryptionType` (which is about `currentSecurityType`
    specifically). `ShadowWifiInfo.newInstance()`/`setRssi`/`setLinkSpeed` do exist; the cast is
    what fails.
  - **`WifiManager.calculateSignalLevel(rssi, 5)` is stubbed to a constant** — `-55` and `-95` both
    return `4`. Asserting `rssiLevel` would have been a green test proving nothing, so it isn't
    asserted.

  **Two findings recorded, deliberately not fixed here** (both are separate decisions, not test
  changes):
  - **Dead code:** `collect()` branches on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q` for the
    WiFi path, but `minSdk = 29` *is* `Q` — the pre-Q `WifiManager.connectionInfo` fallback is
    unreachable on every supported device.
  - **Silent degradation:** `collect()`'s outer `catch (_: Exception) { CollectedNetworkData() }`
    means any bug in its 113 lines renders as a normal-looking empty widget rather than an error.
    Changing that is a product decision about widget failure UX.

  **Both `widget/` items below were closed on 2026-08-16** — read the resolutions before
  re-planning either; each turned out to be a different shape than this entry predicted.

  - **`WidgetRefreshWorker.doWork()` — DONE, by extraction rather than by seaming the I/O.**
    This entry said it "needs interface seams before it's testable at all", which framed the work
    as *wrap the Room DAO / Ktor client / Socket in interfaces and inject fakes*. That is a lot of
    machinery for little return, because **almost none of the logic that decides what the widget
    shows lives in the I/O.** It lives in the derivations between the reads and the write. Those
    are now in `WidgetSnapshot.kt` — `resolveWidgetScore` (the 30-minute posture-score freshness
    window), `resolveIpDisplay` (the `ipinfo.io` validation and `org`-field split), and
    `applyWidgetSnapshot` (the DataStore write) — all framework-free and covered by 17 tests, with
    `doWork()` left as a thin shell that gathers inputs and calls them. The refactor was verified
    key-by-key as behavior-preserving against the pre-refactor `dataStore.edit { }` body; the one
    intentional change is that `LAST_SCAN_TIMESTAMP` and `LAST_REFRESH_MS` now share a single
    `nowMs` instead of two `System.currentTimeMillis()` calls microseconds apart.
    **What is still uncovered, and needs an instrumentation test:** `doWork()`'s own I/O — the
    `EntryPointAccessors` resolution, the `ConnectivityManager`/`WifiManager` reads, the live
    `ipinfo.io` call, the socket latency probe, and its `IOException`→`retry` / `Exception`→
    `failure` result mapping.
    **The `remove` calls in `applyWidgetSnapshot` are the subtle part** — SSID, encryption and the
    two top-issue keys must be *cleared* when absent (else a café's WPA3 badge survives onto the
    cellular widget), while a missing score or IP block deliberately leaves the previous value on
    screen so an offline blip doesn't blank the grade. That asymmetry is now pinned by tests rather
    than by comments.
  - **`detectEncryptionType` — DONE for the branch that is reachable; the other is blocked
    outright, not merely awkward.** The prediction above ("likely blocks it independently") was
    correct for the **API 31+ branch**: it reads `WifiInfo` via `NetworkCapabilities.transportInfo`,
    which is exactly the cast that throws, so `transportInfo` can only ever be null under
    Robolectric and every S+ assertion collapses to "returns null" — true with or without the logic
    under it. That branch needs a real API 31+ device.
    **What the prediction missed: the pre-S branch is live code, not legacy.** `minSdk` is 29, so
    API 29/30 devices take it, and it is fully testable — `DetectEncryptionTypeTest` covers the
    BSSID match (with a decoy AP listed first, so a "take the first scan result" bug fails) and the
    stale-WiFi transport guard.
    **Two traps found while writing it, both worth keeping:**
    - Robolectric fetches its `android-all` SDK jars on demand. Only the SDKs already in
      `~/.m2/repository/org/robolectric/android-all-instrumented/` work offline (29/33/34/35 on
      this machine); `@Config(sdk = [30])` tried to hit the network and failed. Prefer `sdk = [29]`
      for the pre-S path — it is `minSdk` and therefore the more meaningful target anyway.
    - **The transport guard cannot be asserted at the default SDK.** A first version of that test
      passed with the guard deleted, because at SDK 35 it took the S+ branch and returned null for
      an unrelated reason. It has to run at SDK 29 against a WiFi state that *would* resolve to
      WPA3 if the guard were gone.
  - `NetworkCollector.readCellularSignal` — **still open.** Needs `TelephonyManager.signalStrength`
    / `CellSignalStrength` construction. Its pure half is covered via `cellGenerationFor`.
  - **Fixed while extracting — a privacy defect the extraction surfaced.** `applyWidgetSnapshot`
    is the **only** writer of `PUBLIC_IP`/`ISP_NAME`/`ASN_NAME`/`COUNTRY_*`, and nothing anywhere
    removed them. `doWork()` sets `ipData = null` when `ipInfoConsentGranted` is false, so revoking
    ipinfo consent in Widget Settings left the last-known public IP and ISP on the home screen
    indefinitely (and readable via `DeeplinkAction.kt:51`). The bug was that a *revoked consent*
    and a *failed fetch* both arrive as a null `ipDisplay` and the code could not tell them apart;
    `WidgetSnapshot.ipConsentGranted` now distinguishes them — failed fetch keeps the last values,
    revocation clears the block. **This is a behavior change**, not part of the extraction's
    behavior-preservation claim.
  - **Noted, deliberately not fixed** (pre-existing, unchanged by the extraction): a persisted
    posture score with a *future* timestamp is treated as fresh forever, because the freshness
    check is `(nowMs - timestampMs) < WINDOW` and any negative delta satisfies it. Reachable only
    via a clock rollback. `resolveWidgetScore` is now the one place to fix it if it is ever worth
    fixing — `in 0 until POSTURE_SCORE_FRESHNESS_MS`.
  - **Also noted, not fixed:** `isEncryptionSecure(null)` returns **`true`** — it fails *open* on a
    security-adjacent predicate. Unreachable through the widget today (`applyWidgetSnapshot` only
    calls it inside a non-null branch), but it is `internal`, so the next caller inherits the wrong
    default. Changing it is a behavior change and may move `WidgetScoringTest`.
  - **Known untestable hazard:** `WidgetSnapshot` has 14 constructor params including two
    same-typed adjacent pairs (`latencyMs`/`nowMs` as `Long`, `pingMs`/`deviceCount` as `Int`).
    `WidgetSnapshotTest` catches a transposition inside `applyWidgetSnapshot` (every key asserted
    against a distinct sentinel value), but **nothing catches a transposition at the
    `WidgetSnapshot(...)` construction in `doWork()`**, because no unit test can construct it the
    way `doWork` does. That risk transfers to the instrumentation test whenever it gets written.
- **Compose screenshot/snapshot tests** — **PARTIALLY DONE (2026-08-01).** This entry used to
  read "there are none anywhere in the repo", which is no longer true; read the split below
  before planning against it, because only half of what was asked for exists.
  **Cost that motivated it, 2026-07-28:** a duplicate-`LazyColumn`-key crash on the Wi-Fi
  survey's primary path shipped through three review passes, an adversarial round and 750 green
  unit tests, and was found by a two-minute manual walk (`dc03409`).
  - **Done — composition smoke tests.** `Paparazzi` was chosen (JVM-only, no emulator, matches
    the "no physical device" constraint) and wired as the `netlens.android.screenshot`
    convention plugin. Eleven `*RenderTest.kt` files across ten modules render a screen on the
    JVM and fail if it cannot compose, which closes the duplicate-key / composition-error /
    measure-failure class that motivated the entry. Covered: home, devices, dns, lanscan
    (`HostDetailSheet`, `ScanLocationSection`), monitor, ping, portscan, traceroute, wifi
    (`WifiSurveyTab`), and `core:ui`'s `ResultActions`.
  - **Still open — visual regression.** **No golden images are recorded**, nothing is committed,
    and `verifyPaparazzi` never runs. So the original goal of letting an agent verify
    *appearance* regressions (DESIGN.md's typography and spacing rules) is **not** met — a
    screen that composes fine but renders wrong still passes. Adopting goldens is a separate
    decision with real cost: committed PNGs, and font/renderer drift making them flaky across
    environments. Do not assume the existing tests provide it.
  - **Also still open:** the remaining screens have no render test at all. Mechanics and the two
    traps (the render exception escapes via the JUnit rule, so asserting inside `snapshot { }`
    silently passes; screens taking `hiltViewModel()` must have their list lifted into a
    stateless `internal fun *Content(...)` first) are documented in `CLAUDE.md` under "Testing".
- **Recorded network-scan fixture corpus** — no captured real-world ARP tables, SSDP
  responses, DNS response bytes, WHOIS text, or TLS handshakes exist anywhere in the
  repo as replay fixtures. An agent asked to reproduce "LAN scan doesn't find my
  printer" has to synthesize a fake scenario from scratch rather than replaying a
  captured trace. Worth a small `test-fixtures/` corpus of anonymized real scan
  output once a live device is available to capture one.
- **Phases 2-4 of `fable-audit-fixes.plan.md`** (see item 4) — feature-parity and
  consistency fixes already scoped, just not executed. Task 6 in that plan (Posture's
  trust-model) explicitly needs a product decision from a human before an agent can
  implement it — flag this rather than guessing when it's picked up.
- **`core:data` Room testing** — only `UserPreferencesRepositoryTest.kt` exists;
  no DAO-level tests use an in-memory Room database (would need Robolectric or
  `Room.inMemoryDatabaseBuilder` against a JVM-only test config — confirm which is
  wired before assuming either works today).
  - **Concrete consequence, worth knowing before you trust a green suite:** the
    `known_devices` write-path split is a *correctness invariant* with no automated
    guard. Scan-derived columns (`hostname`, `ip`, `vendor`, `deviceType`, `osGuess`)
    are written only by `KnownDeviceDao.updateLastSeen`; user-authored ones
    (`customName`, `tags`, `notes`, `location`) only by `updateUserDetails`. A re-scan
    must never clobber what the user typed. `DeviceTaggingTest` asserts this against
    `FakeKnownDeviceDao`, whose implementation copies exactly the four fields — so it
    verifies the fake, not the `@Query`. Adding `hostname = :hostname` to the real
    statement would ship with CI fully green. Same for `MIGRATION_14_15`: Room only
    validates a migration against the schema at *runtime*, so a mismatch is a
    first-launch crash for upgrading users that no JVM test can catch. Both were
    checked by hand (2026-07-26, PR #116) by diffing the migration DDL against the
    generated `15.json`. Re-check by hand after any edit to either, until an
    in-memory-Room test exists.

## Not a gap (verified, don't re-litigate)

- **Release process** is thoroughly codified in `.claude/skills/android-release/SKILL.md`
  — pre-flight checks, cert-continuity verification, F-Droid changelog gating, and the
  refuse-don't-autofix philosophy are all already agent-executable. No action needed.
- **Pro-gating patterns** (3 coexisting variants) are documented in both `CLAUDE.md`
  and `DESIGN.md` with explicit "choose based on screen architecture" guidance — this
  is exactly the kind of tribal knowledge that's supposed to be codified, and it is.
- **SSRF-guard discipline** — **the outbound-HTTP half only.** The `configureSecureDefaults()`
  pattern in `httptester` and `monitor` is settled and needs no further attention.
  **The SSDP half has been REMOVED from this section — see the warning below.** It was listed here
  as a "known deferred low-priority instance… not a new finding, just noting it's tracked", which
  turned out to be the single most misleading line in this file.

---

## The SSDP LOCATION guard — the opposite of "don't re-litigate" (added 2026-08-08)

`SsdpScannerImpl.isSafeLocationUrl` (`core/scan/.../engine/SsdpScanner.kt:72` — the file moved out
of `feature/lanscan` into `core:scan`, so the old path in this document's history is dead) decides
whether the app will fetch an attacker-supplied URL. **Any device on the LAN can answer an M-SEARCH
and choose that URL.**

This entry exists because the "Not a gap" section told a future agent that this code was tracked,
deferred and low-priority. On 2026-08-08 it produced **five security findings in one session**, and
that framing is part of why they sat there:

| # | Finding | Found by |
|---|---|---|
| 1 | DNS rebinding — resolved once to validate, `openConnection` resolved again to connect | scoping pass |
| 2 | Cross-host SSRF — nothing tied the URL to the responder, so `LOCATION: http://192.168.1.1/admin` was fetched | scoping pass |
| 3 | Redirects were never disabled; `HttpURLConnection` follows them by default, so the new host check was bypassable in one hop | `/review` |
| 4 | The loopback/link-local rejection was **deleted** while fixing 1-2 — replaced by the responder match rather than added to | `/codex review` |
| 5 | Device-supplied hostnames could forge rows in exported text (`DisplayText.flatten`, `core:network`) | mDNS data-flow review |

**Three of the five were introduced while fixing the first two.** The rules that came out of it,
which generalise past this file:

- **When tightening a security predicate, ADD the check — never let it replace the old one.** Both
  conditions were necessary; neither was sufficient. UDP source addresses are forgeable, so
  `host == responderIp` proves nothing, and another app on the same device can answer from
  `127.0.0.1`.
- **A test can encode the vulnerability as intended behaviour.** The test for finding 4 was named
  *"loopback and link-local stay rejected UNLESS they are the responder"* and asserted only the
  cases that still passed. It went green against the hole it described. A sibling test used
  `fe80::1` — itself link-local — to assert a special address was fetchable.
- **Run two independent reviewers on anything security-shaped, before merge.** `/review` (plus a
  security specialist subagent) and `/codex review` covered the same 137-line diff and found four
  real issues with **zero overlap**: one asked "is the new guard bypassable?", the other asked "what
  did the old guard do that the new one no longer does?" The specialist shared the first blind spot.

Current coverage: `SsdpLocationUrlTest` and `SsdpHostileInputTest` in `core/scan/src/test/`. Treat
this function as high-risk on every edit. **Do not restore it to "Not a gap".**
