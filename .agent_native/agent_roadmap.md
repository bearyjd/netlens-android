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

### 1. Fix CLAUDE.md's stale CI-scope claim (effort: 5 min, saves: every future session)

**Problem:** `CLAUDE.md` (pre-existing, line 20) states CI "currently tests only:
`:core:network`, `:feature:lanscan`, `:feature:whois`, `:feature:monitor`". This is
false — `.github/workflows/ci.yml` runs `testFossDebugUnitTest testDebugUnitTest`, which
executes unit tests in **every** module that has a `src/test` tree (23 of 27
core+feature+app+widget modules currently have tests). An agent that trusts the stale
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

### 3. Add a hardware-seam + fakes for `feature:celltower` and `feature:wifiaudit` (effort: half a day, saves: the two modules currently un-verifiable by any agent) — PARTIALLY DONE (2026-07-07)

**Status:** `celltower` and `wifiaudit` are done; `history` and `widgetsettings` were skipped — see
correction below. This item's own problem statement was stale on one point: `CellTowerReader` and
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

**Problem:** `celltower`, `history`, `widgetsettings`, and `wifiaudit` are the only 4
feature modules with **no test directory at all**. `history` and `widgetsettings` are
pure ViewModel/state-flow modules (no excuse — same pattern as the 19 other tested
feature ViewModels) and are cheap to fix. `celltower` and `wifiaudit` are harder: their
engines (`feature/celltower/.../engine/CellTowerEngine.kt`,
`feature/wifiaudit/.../engine/WifiInfoReader.kt`) read directly from
`TelephonyManager` / `WifiManager` system services with no interface seam — unlike
`feature:lanscan`'s engines (`ArpTableReader`, `NetBiosProber`, `SsdpScanner`,
`SubnetScanner`), which already have `Fake*` counterparts under
`feature/lanscan/src/test/.../engine/`. There is also **zero Robolectric usage
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

### 4. Un-silo the prior-audit trail — commit durable planning artifacts instead of gitignoring them (effort: 30 min, saves: re-discovery cost on every fresh clone/session) — SKIPPED (2026-07-07, needs a human)

**Status:** Not attempted in this pass. This item requires editing `.gitignore` and then git-committing
the newly un-ignored files — both are outside a no-commit automation pass (the agent doing items 2-3
in this session was explicitly instructed not to touch git). Left for the user: decide whether
`docs/decisions/` or `docs/backlog/` is the right destination, then force-add and commit
`HANDOFF-fable-audit-2026-07-04.md`, `fable-audit-fixes.plan.md`, `fable-audit-2026-07-04.md`, and a
new `docs/backlog/competitor-features.md` per the Fix section above.

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

### 5. Extract shared network-primitive fakes into a `testFixtures` source set (effort: half a day, saves: reinvented mocks on every future feature)

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

---

## Backlog (lower H.A.S./effort ratio, or needs a human decision first)

- **Robolectric adoption** for Context/Room/DataStore/ConnectivityManager-dependent
  code — highest structural leverage for verification gaps but repo-wide in scope
  (touches `build-logic/convention`); needs its own scoped plan.
- **Compose screenshot/snapshot tests** — there are none anywhere in the repo. Would
  let an agent verify UI regressions (e.g. DESIGN.md's typography/spacing rules)
  without a device. Consider `Paparazzi` (no emulator required, JVM-only, matches the
  "no physical device" constraint) over `Screenshot Testing for Compose` (needs a
  device/emulator).
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

## Not a gap (verified, don't re-litigate)

- **Release process** is thoroughly codified in `.claude/skills/android-release/SKILL.md`
  — pre-flight checks, cert-continuity verification, F-Droid changelog gating, and the
  refuse-don't-autofix philosophy are all already agent-executable. No action needed.
- **Pro-gating patterns** (3 coexisting variants) are documented in both `CLAUDE.md`
  and `DESIGN.md` with explicit "choose based on screen architecture" guidance — this
  is exactly the kind of tribal knowledge that's supposed to be codified, and it is.
- **SSRF-guard discipline** — already fixed and documented per the prior audit handoff
  (`configureSecureDefaults()` pattern in `httptester` and `monitor`); one known
  deferred low-priority instance remains at `feature/lanscan/.../SsdpScanner.kt:74`
  (LAN-local SSDP spoofing, different threat model, explicitly deferred — not a new
  finding, just noting it's tracked).
