# Session Handoff — anti-slop pass, #129/#130 merged (2026-08-05)

Supersedes the 2026-08-02 handoff (kept below from "TL;DR — 2026-08-02" onward; its
release history is still accurate, its **device state is not**).

## TL;DR — 2026-08-04/05

- **#129 merged** (`4049c2b`) — anti-slop pass. `ResultActions` in `core:ui` replaces a
  ~15-line copy/share block duplicated across 14 screens; nine port/service types moved
  `feature:portscan → core:scan`; three `PortScanner` fakes collapsed into one in
  `core:scan-testing`. **No feature→feature dependency remains anywhere in the repo.**
- **#130 merged** (`8293928`) — `testLogging` + doc corrections. See the two CORRECTED
  sections immediately below; both were wrong claims, not stale ones.
- **CI runs THREE test tasks, and every doc said two.** `testFossDebugUnitTest
  testGplayDebugUnitTest testDebugUnitTest`. Only the gplay task reaches `src/testGplay`,
  where `GplayProStatusTest`'s 12 billing tests live. **858 tests with two tasks, 895 with
  three.** A two-task run skips them silently rather than failing. Fixed in `CLAUDE.md`,
  `CONTRIBUTING.md`, `dependencies.md`, `agent_roadmap.md` — but if you see a two-task
  command anywhere, it is wrong.
- **Test failures now print a real stack trace.** There was no `testLogging` anywhere in
  `build-logic/`; Gradle printed only a failing test's *name*, in every module. That is why
  a flake cost a full investigation that ended in "cannot reproduce".
- **LAN Scan "Inventory" tab no longer wraps mid-word** (`2950887`) — landed with this
  handoff via `fix/lanscan-tab-label-wrap`. Verified on the Fold's cover screen. The
  first attempt was wrong in an instructive way; see Open items.
- **The Pixel 9 Pro Fold was WIPED and is no longer on a published build.** Its schema is
  now **v16**. Read "Device state" before installing anything on it — installing published
  1.3.0 would be a *downgrade* and Room will destroy the database.
- Codemaps refreshed (first since 2026-07-19; `architecture.md` and `data.md` were both
  >30% stale). `.reports/codemap-diff.txt` has the full diff and open findings.

## CORRECTED: the DevicesViewModelTest dispatcher wiring is FINE (don't "fix" it)

An earlier claim in this session — and in PR #129's body before it was corrected — said
`setUp()` running outside `runTest` left `Dispatchers.setMain(UnconfinedTestDispatcher())`
and the injected `defaultDispatcher` on schedulers unrelated to each test's `runTest`
scheduler, making its `advanceUntilIdle()` calls no-ops. **That is false.** A probe proved
all three share one scheduler:

```
testScheduler === setUpDispatcher.scheduler      -> true
testScheduler === injectedDispatcher.scheduler   -> true
testScheduler === TestCoroutineScheduler()       -> false   (control)
```

`kotlinx-coroutines-test` auto-links them: `setMain` publishes its dispatcher's scheduler,
and any `TestDispatcher` constructed afterwards **without an explicit scheduler argument**
adopts it. Only an explicitly-passed `TestCoroutineScheduler` creates a real split. Acting
on the wrong diagnosis would have "fixed" 20+ tests that were never broken.

Also: `Dispatchers.Main` is a `TestMainDispatcher` **wrapper** and throws
`ClassCastException` if cast to `TestDispatcher`. Hold a reference to the dispatcher you
created instead.

## CORRECTED: `local.properties` does NOT contain only `sdk.dir`

`CLAUDE.md` asserted that for months. On the primary dev machine all four `release.*` keys
are set and are what signs local release builds (keystore at
`/home/user/keys/netlens/netlens-release.keystore`). The file is git-ignored and
machine-local, so **check it, don't assume** — that assumption cost a wrong recommendation
during this session. `CLAUDE.md` now describes the per-field fallback mechanism instead of
asserting machine state, and records the cert continuity baseline
`8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4`.

Note the `signatures:[…]` value in `adb shell dumpsys package` is a **Java object
hashCode, not a cert digest** — it cannot be used for a continuity check. Use
`apksigner verify --print-certs`.

## The flake that is still live — do not chase it cold

`DevicesViewModelTest > watchCurrentNetwork is a no-op when gateway is unresolvable`
failed twice under full-suite runs, then survived **26 reproduction attempts** (12 idle
module runs, 8 under CPU saturation, 6 full-suite with the exact failing command). No root
cause was established and **nothing was patched** — deliberately.

Why the assertion itself is near-unfailable: `watchCurrentNetwork()` only reaches
`watchedNetworkDao.upsert` when `gatewayMac` **and** `subnet` are non-null, the test nulls
`gatewayMac`, and the init block only *reads* `observeAll()`. A genuine assertion failure
needs a writer that does not exist. The likelier story is misattribution — the class
comment at `DevicesViewModelTest.kt:47-51` already describes a DataStore collector
resuming on a later test's dispatcher and surfacing "as a flake in a test that did nothing
wrong".

Remaining unproven candidate: `@TempDir` deletion racing a still-running DataStore write
(fits "only fails under load"). Not fixed because joining a scope whose children run on the
test dispatcher risks turning a rare flake into a reliable hang.

**What changed: its next firing will print a real stack trace.** Wait for that, then act on
it. Do not spend time reproducing it cold — that path is already exhausted.

## TL;DR — 2026-08-02

- **v1.3.0 RELEASED (2026-07-31)** — https://github.com/bearyjd/netlens-android/releases/tag/v1.3.0
  Device tags, Wi-Fi coverage survey, launchable services, plus #117's fixes. Workflow
  `30662655390` succeeded. **Both published APKs were downloaded and verified**: cert
  `8fdfc928…ae2b4` (identical to 1.2.6, so in-place updates work), `versionCode='14'`,
  `versionName='1.3.0'`. All four artefacts attached.
  - **The device verification behind this release was a user confirmation, not an instrumented
    result.** The user reported the four checks fine on 2026-07-31; no sample counts or logs were
    captured. Relevant because the fold fix had previously shipped green *twice* and been wrong
    both times. If a fold-related report comes in, do not assume the path was measured.
  - Shipped with a **stale baseline profile** (last regenerated 2026-07-21, predating the v1.2.6
    tag). Explicitly accepted at tag time — unmatched rules are ignored, so the cost is some
    startup speedup, not correctness. **Regenerated afterwards in #123** (run 30663855762, API 34
    emulator); master now carries a profile that has actually seen the survey, tagging and
    launchable-services paths. It will ship in 1.3.1 — 1.3.0 as published does not have it.
- **v1.2.6 released** — https://github.com/bearyjd/netlens-android/releases/tag/v1.2.6
  (widget cross-render fix, 4x2 fontScale fix + enrichment, baseline profile). The
  *published* APK was downloaded and verified: cert `8fdfc928…`, `versionCode='13'`.
- **PR #116 merged** — `78fa6e0`, squashed. Device tagging, Wi-Fi coverage survey,
  launchable services. master CI green. 752 tests.
- **The survey is verified on hardware, and device use found TWO defects the reviews missed** —
  a crash on the primary path (`dc03409`) and a fold that still killed the capture (`811c9e1`).
  Capturing multiple points and sharing both work. See "What device use found".
- **F-Droid MR #42628 still open at 1.2.6 / 13, awaiting maintainers — so F-Droid will NOT ship
  1.3.0 until it merges.** `AutoUpdateMode: Version` + `UpdateCheckMode: Tags` means the bot picks
  up later tags automatically *once merged*; nothing on our side speeds that up. The in-repo copy
  (`fdroid/com.ventouxlabs.netlens.yml`) was synced to 1.3.0 / 14 — that file is a mirror and
  gates nothing.
- **Only `master` exists on the remote.** All feature branches deleted.
- **Nothing is in flight.** No open PRs, no running jobs, clean tree. v1.3.0 is out.
  #117-#119 landed 2026-07-30, #120-#122 on 2026-07-31, #123-#127 on 2026-08-01/02 — see the
  "landed" sections below.
  *(Was true on 2026-08-02, and true again after `fix/lanscan-tab-label-wrap` merged on
  2026-08-05. Only `master` exists on the remote.)*
- **The Pixel 10 Pro Fold is running master, not 1.2.6, and its About screen says otherwise.**
  See "Device state".
- ~~**Both phones now run published v1.3.0 / versionCode 14.**~~ **NO LONGER TRUE for the
  Pixel 9 Pro Fold** — it was wiped and rebuilt on 2026-08-05. See "Device state".

## CORRECTED: release signing secrets are valid (don't re-flag)

`gh secret list` shows all four `RELEASE_*` secrets last updated **2026-05-07**, while the
keystore passwords were rotated **2026-07-23**. That looks like CI would fail at signing.
It doesn't.

**Disproven by a `workflow_dispatch` dry run of `release.yml`** — safe, because every
publishing step is gated on `startsWith(github.ref, 'refs/tags/v')`, so a branch dispatch
validates and builds but publishes nothing. `Decode and validate release keystore`
succeeded and printed `SHA256: 8F:DF:C9:28:…:B4`. The rotation evidently did not change the
store/key passwords. Cert continuity confirmed **four** independent ways: local FOSS build,
CI keystore, local gplay build, published release APK.

**Reusable technique:** dispatching `release.yml` on a branch is a zero-risk way to prove the
signing path before tagging. Do this before any release where signing is in doubt.

## CORRECTED: the F-Droid MR was two releases behind, not one

The previous handoff recorded "recipe synced to 1.2.5/12". That was only the **in-repo copy**
(`fdroid/com.ventouxlabs.netlens.yml`). The upstream MR — the thing that actually gates
F-Droid — was still on **1.2.4 / 11**; it was never updated when 1.2.5 shipped. Don't confuse
the two files again.

Now at 1.2.6 / 13 @ `27404f27`, pushed as a single build entry (one build for the reviewer to
verify; `AutoUpdateMode: Version` + `UpdateCheckMode: Tags` picks up later releases
automatically). Open, no conflicts, no maintainer activity since 2026-07-14. Merging is
theirs — nothing on our side speeds it up.

**Note:** the MR discussion could not be read — the `notes` API 401s without auth on project
36528. Check the thread manually before assuming there are no review comments.

## PR #116 — three review passes, and the pattern in them

- **Pass 1** (the feature): 2 HIGH, 2 security, 7 MEDIUM, 9 LOW.
- **Pass 2** (the *fixes* from pass 1, which nobody had reviewed): 1 HIGH, 3 MEDIUM. **Two
  re-opened the exact bugs their own commit messages claimed to close.**
- **Devil's advocate** (the fixes from pass 2): 8 items, including an unbounded capture hold
  that could record a "spot" spanning two rooms.

All closed. The pattern is the useful part: **every round's fixes were correct for what they
tested, and each opened an adjacent hole.** Three consecutive rounds found lifecycle-timing
defects at boundaries the previous round's tests didn't reach. The commit messages were
accurate about what they tested — they were just testing too narrow a slice.

That is what drove the final change: `WifiSurveyViewModel`'s lifecycle is now **one sealed
`SurveyPhase`** (`Idle` / `Starting(job, sessionId?)` / `Running(sessionId, samplingJob?)`)
instead of five loose fields (`starting`, `startJob`, `samplingJob`, `openSessionId`, and
`activeSessionId` read out of UI state). Each defect had been a *legal* combination of those
fields; the illegal ones existed only in comments. Two simplifications fell out: a cancelled
start closes its own row and is the only thing that does (the id lives in a local owned by
that start), and `onScreenStopped` became a three-case `when`.

**Non-obvious hazard found while doing it:** both jobs are launched `CoroutineStart.LAZY` and
started *after* the phase is published. On an unconfined dispatcher an eagerly-started
coroutine can complete before `launch()` returns, so assigning `Starting(job)` afterwards
clobbers the `Running` it had already set.

Worth knowing about the shipped code:

- `WifiSignalSampler.samples()` returns `Flow<WifiSignalSample?>`; **null means "polled, not
  associated"**. Skipping the tick was indistinguishable from "still working", which left a
  capture waiting forever for samples that could not arrive.
- Full BSSIDs never leave the device — public wardriving databases resolve an AP MAC to a
  street address. UI and export share `apShortName`.
- `ServiceLauncher.forPort` **validates** the host rather than escaping it (no legitimate host
  needs `/ ? # @`).
- A stopped capture is **suspended, never abandoned at ON_STOP**. `onScreenStarted` judges it by
  the elapsed gap alone: under `MAX_CAPTURE_GAP_MS` (3s) the burst resumes, past it the burst is
  discarded with `CAPTURE_INTERRUPTED`. A fold survives; a pocketed phone doesn't. Do NOT
  reintroduce `isChangingConfigurations` — see below for why it doesn't work.

## Testing lessons worth keeping

**Two tests were passing for the wrong reason**, both found only by aiming at them:

1. `FakeWifiSignalSampler` returned `emptyFlow()` when disconnected — which *completes* — so
   the start-timeout test never exercised `withTimeoutOrNull`. Deleting the timeout kept it
   green while the real app would hang forever. The fake now polls and emits nulls like
   production; with the timeout removed that test **hangs** instead of passing.
2. `DevicesViewModelTest` failed ~1 run in 10 with "uncaught exceptions before the test
   started" — leaked DataStore/ViewModel collectors resuming on a *later* test's Main
   dispatcher. Measured 1/10 before, 0/25 after.

**Discipline that caught real problems, use it:** revert each fix individually and confirm its
test fails, and *only* its test. This caught an attempted fix of mine that passed a shallow
test but didn't work — `NonCancellable` recorded the session id, but nothing after that block
suspends, so a cancelled coroutine ran to completion, never threw, and never reached the catch
that closes the row. It needed an explicit `ensureActive()`. The same revert-check was re-run
against the `SurveyPhase` refactor to prove no regression test had been made vacuous.

## Room migration 14→15 — validated without a device

`MIGRATION_14_15` has no automated test (Room only validates at runtime). Verified by building
a real v14 SQLite database from `14.json`, seeding a row, applying the migration SQL parsed out
of `DataModule.kt`, and comparing against a fresh v15 database. **Zero differences across 21
tables**; the seeded row kept `customName` with new columns NULL.

**Trap:** comparing raw `sqlite_master` SQL text gives a **false** mismatch — `ALTER TABLE ADD
COLUMN` records the column name unquoted. Compare structurally (`PRAGMA table_info`), which is
what Room does.

## Device state — READ BEFORE INSTALLING ANYTHING

**The two phones are no longer in the same state. Updated 2026-08-05.**

- **Pixel 10 Pro Fold `57211FDCG0023C`** — running **published v1.3.0 / versionCode 14**,
  schema **v15**, the exact artefact downloaded from the GitHub release and verified (cert
  `8fdfc928…ae2b4`). Unchanged this session. `adb install -r` of a v15-or-later build
  preserves its data.
- **Pixel 9 Pro Fold `4A111FDKD0000C`** — **WIPED 2026-08-05.** Now running a **locally
  built, release-signed FOSS 1.3.1 / versionCode 15** at schema **v16**, launched clean.
  - **Its database was destroyed.** A debug-signed build cannot replace a release-signed
    one (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), so the app was uninstalled — the user chose
    this with the data loss stated. Device inventory, custom names, tags, saved scans and
    all history are gone. It is at first-run state.
  - **Do NOT install published 1.3.0 on it.** That is schema v15 against its v16 database —
    a *downgrade*, and `provideDatabase` has `fallbackToDestructiveMigrationOnDowngrade`, so
    Room will wipe it again. Only install 1.3.1+ / v16+ here.

Rule that still holds for both: verify the cert with `apksigner verify --print-certs` before
installing. A mismatch forces an uninstall, which wipes the database. Expected cert:
`8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4`, v2 scheme only (v1 and
v3 are off — fine for minSdk 29).

**This collision is avoidable and nobody has fixed it yet.** Debug and release share
`com.ventouxlabs.netlens` (no `applicationIdSuffix` on the debug buildType), so every
on-device debug test costs a wipe. See Open items.

To build something newer for testing: `./gradlew assembleFossRelease` — add `--max-workers=6` on
a many-core machine or it OOMs (see the parallelism note above). Signing resolves per field from
`local.properties` then `RELEASE_*` env vars; a `*-unsigned.apk` means it resolved to nothing.

**Driving the app via adb works well** and is worth reusing: `adb shell am start -a
android.intent.action.VIEW -d "netlens://feature/<route>"` jumps straight to any tool. Two
gotchas on the Fold: `screencap` needs an explicit `-d <displayId>` or it writes a warning
into the PNG and corrupts it, and the **outer** display is `4619827677550801153` (the inner
one captures pure black while folded). Fire the deep link twice after an `install -r` — the
first lands before nav is ready.

To build something newer for testing: `./gradlew assembleFossRelease` — add `--max-workers=6` on
a many-core machine or it OOMs (see the parallelism note above). Verify the cert with
`apksigner verify --print-certs` before installing; a mismatch forces an uninstall, which wipes
the database.

- **On-device UI automation on these phones is unreliable.** GrapheneOS updates, Settings and
  the user's own apps repeatedly stole foreground mid-sequence. A capture guarded on
  `topResumedActivity` is **not sufficient** — the notification shade overlays the app without
  changing it, and one capture caught personal notifications. Prefer having the user drive the
  UI; the survey needs someone walking the house anyway.

## Open items — added 2026-08-05

0. ~~LAN Scan "Inventory" tab wraps mid-word~~ — **done in `2950887` (2026-08-05)**, merged
   with this handoff. 895 tests green, verified on the Fold's cover screen.
   - **Worth keeping:** the first attempt (`softWrap = false` alone) only converted the wrap
     into a **clipped descender** — at normal zoom it looked fixed and was not. It took
     `labelMedium` as well. **Magnify a screenshot before believing a visual fix.**
   - `TabRow` was kept over `ScrollableTabRow` deliberately: the latter fixes the 1080px
     cover screen by left-bunching all four tabs on the 2076px inner one. Reasoning is in a
     comment at the call site so it does not get re-litigated.
A. **Location one-tap for LAN Scan — plan written, not implemented.**
   `.claude/PRPs/plans/lanscan-location-one-tap.plan.md`. The headline: **this is ~80%
   already built.** `ACCESS_FINE_LOCATION` is declared, the screen already requests it on
   entry (`LanScanScreen.kt:118-129`), a `LocationManager` fix is already read
   (`:722-731`) and already preferred over anything typed (`:137-140`). The manual
   lat/long fields are a silent fallback presented as the primary input. It is a UX
   correction plus a testability extraction, not a new capability.
   **Never reach for FusedLocationProvider** — needs Play Services, breaks `foss` and
   F-Droid.
B. **`FakeDataStore` is duplicated** in `feature/ipinfo/.../IpInfoViewModelTest.kt` and
   `feature/widgetsettings/.../WidgetSettingsViewModelTest.kt` instead of living in
   `core:data-testing`. Same copied-fake pattern that produced the drifted `FakeOuiLookup`,
   `FakeNetworkEventDao` and two `FakePortScanner` copies. Mechanical to fix;
   `FakePortScannerTest` is the template.
C. **`applicationIdSuffix ".debug"` on the debug buildType** — would stop debug and release
   colliding on `com.ventouxlabs.netlens` and end the wipe-per-debug-install tax that cost
   a database this session. One line, but check the Glance widget provider and the
   `netlens://` deep links tolerate the changed id.
D. **On-device verification of #129 is partial.** Confirmed by screenshot on the release
   build: `ResultActions` renders on Devices, correctly renders *nothing* on LAN Scan with
   no results, the v16 "Saved" tab exists, logcat clean of R8 fallout, and
   `proguard-rules.pro` keeps serializers correctly. **Not** exercised: saving an inventory,
   and port→service launching — both need a real LAN scan against live infrastructure.

## Open items — carried over

1. **F-Droid MR #42628** — still open at 1.2.6 / 13, so **F-Droid will not ship 1.3.0 until
   maintainers merge it**. `AutoUpdateMode: Version` + `UpdateCheckMode: Tags` picks up later
   tags automatically *once merged*; nothing on our side speeds that up. Read the thread
   manually — the `notes` API 401s without auth on project 36528.
2. **Play Console** — manual; the v1.3.0 gplay AAB is attached to the GitHub release.
   Checklist in `docs/play-store.md`.
3. **`.claude/PRPs` is still gitignored**, so review artifacts are local-only and lost on a fresh
   clone. Note that `.omc/skills/` had the *same* problem and was fixed on 2026-08-01 (`/.omc/*`
   plus `!/.omc/skills/`); the same shape of fix would work here. Tracked in the roadmap as
   needing a human decision.
4. ~~**Two screens have no composition guard**~~ — **done in `95c975c` (2026-08-02).**
   `DevicesContent` and `HomeContent` are state-driven Paparazzi targets. The system-service-bound
   Devices watch section remains a production slot because layoutlib does not implement the
   notification service; the list body is still exercised. Both new render suites pass.
5. **The baseline profile regenerated in #123 is NOT in published 1.3.0** — it landed after the
   tag. It ships in 1.3.1.
6. ~~Confirm the fold fix on hardware~~ — **user-confirmed 2026-07-31.** See the caveat in the
   TL;DR: this was a verbal confirmation, not an instrumented result.
7. ~~PortScan prefill bug~~ / ~~Deferred LOWs from #116~~ / ~~Release 1.3.0~~ — **all done.**

## What landed 2026-08-01/02 — five PRs (#123-#127), master green

- **#123** — baseline profile regenerated on an API 34 emulator. The previous one predated even
  the v1.2.6 tag. Ships in 1.3.1, not in published 1.3.0.
- **#124** — **composition smoke tests** via Paparazzi. Reverting `dc03409` now fails a test in
  26 seconds with the exact production message `IllegalArgumentException: Key "1" was already
  used` — the crash that survived three review passes, an adversarial round and 750 green tests
  and was found by a two-minute hardware walk.
- **#125** — extended to `HostDetailSheet`, and put `protocol` back in its row key. The key was
  `riskLevel_port` while `HostPortResult` carries a protocol, so 80/TCP and 80/UDP would have
  collided. Latent, not live: nothing emits non-TCP.
- **#126** — namespaced `DevicesScreen`'s section keys and wrote the convention into CLAUDE.md.
- **#127** — composition tests for monitor, traceroute, portscan, ping, dns. **36 render tests
  across seven screens.** Required five `*Content` composables to go `private` → `internal`.

**Use Paparazzi as a composition check, not a screenshot check.** No golden images, no PNGs
committed, `verifyPaparazzi` never runs. Duplicate keys, composition errors and measure/layout
failures all throw at *render* time, so `paparazzi.snapshot { }` in a plain unit test is the whole
assertion. Three gotchas: the render exception escapes via the JUnit rule, NOT out of
`snapshot { }` (a `try/catch` at the call site silently passes — assert nothing); only
state-driven composables work; and a screen calling `rememberLauncherForActivityResult` needs
`LocalActivityResultRegistryOwner` provided (see `PingContentRenderTest`).

**Lazy-list keys were swept — all 28 call sites, 2026-08-01.** All currently safe. The useful
split is keys safe *by construction* (namespaced: wifi, history, home, lanscan, dns) versus safe
*by an invariant living elsewhere* (devices' `partition`, netlog's single table, single-list
containers). The convention is now in CLAUDE.md. `HistoryScreen` already namespaced by tool
because its rows come from 11 tables — someone hit #116's problem independently and solved it.

**Learned skills are tracked now** (`.omc/skills/`, 8 files). `.gitignore` had excluded all of
`.omc/`, so five skills from earlier sessions were one `git clean` from gone. Two subtleties in
the fix, both of which bit: `/.omc/*` not `/.omc/` (a trailing-slash exclude stops Git descending,
so a negation inside can never re-include), and a pattern containing a slash is root-anchored, so
`**/*/.omc/` is needed to keep ignoring stray nested `.omc` dirs that tooling writes elsewhere.

**874 tests**, up from 752 at the start of 2026-07-30. Every module has coverage.

**Read `.omc/skills/` before re-deriving anything.** Three skills written 2026-08-01 capture what
cost real debugging: `unenforced-verification-expertise` (six instances where green meant
nothing), `gradle-local-parallelism-expertise` (the cold-build OOM and the test-worker
`Connect timed out` are one cause — worker count, not heap — and the tempting repo-level heap
fix both fails and endangers F-Droid's from-source build), and
`paparazzi-composition-smoke-expertise`.

## What landed this session (2026-07-30) — three PRs, all squashed, master green

- **#117** — PortScan prefill + the four deferred #116 LOWs. Two adversarial rounds on top of the
  original commit, and **each round found a real defect in the previous round's fix**: round one
  cleared `SIGNAL_LOST` on the next reading, which silently discarded a capture after a roam;
  round two's `HostName` scope-id guard was ineffective for any name spelling hex
  (`cafe%evil.example` → `cafe`) and only appeared to work because the case tested, `nas`, does
  not spell hex. That is now four consecutive rounds where the fixes needed fixing — the pattern
  from #116 held exactly.
- **#118** — shared test doubles as `:core:scan-testing` / `:core:data-testing`. Three fakes had
  been copied out of their home module and **every copy had drifted weaker**, which is worse than
  duplication because a loose double turns a red test green. Full accounting in the roadmap.
- **#119** — `SsrfRedirectProbe` in `:core:network-testing`, replacing a byte-identical MockEngine
  in both `httptester` and `monitor`.

### 2026-07-31 — two CI gaps, and a mistake worth reading

- **#120** — CI now builds release variants (`assembleRelease bundleRelease`, both flavors) on
  every PR. Nothing did before: `ci.yml` was `assembleFossDebug` only, and `release.yml` runs on
  tag push, so R8, resource shrinking and lintVital were first exercised *at the moment of
  tagging*. Unsigned, so no secrets reach PR builds.
- **#121** — CI now runs `testGplayDebugUnitTest`. **`GplayProStatusTest` — 12 tests over the
  purchase path — existed, passed, and had never once run in CI.**

**The mistake, because it is the more useful artefact:** #120 asserted in three places that
`app/src/gplay/` had *zero tests*. It has twelve. I had checked whether the gplay tests *ran*,
found nothing, and concluded none *existed*. Those are different failures and the second is
worse — coverage that nothing enforces reads as coverage to everyone who looks. It is the same
shape as the two defects found the day before (a fake that accepted filter arguments and
discarded them; a scope guard whose test passed for an incidental reason). **Before recording any
module as untested, check whether the tests exist and are simply not being run.** Anything under
`src/test*` that no CI task names is decoration.

Swept afterwards: `src/testFoss` and `src/testGplay` are the only flavored test trees, and there
are no `androidTest` trees anywhere, so #121 closes the class rather than one instance.

- **#122** — `HistoryViewModel` covered (16 tests). **Every module in the repo now has unit
  tests.** Unblocked by splitting `HistoryRepository` into an interface plus
  `HistoryRepositoryImpl` (`@Binds` in `core/data/di/RepositoryModule.kt`) — the ViewModel had
  depended on the concrete class wrapping eleven Room DAOs and `withTransaction`. Use that split
  if you hit the same wall.

  **Writing the tests found a latent defect:** `private var allItems` was declared *below*
  `init { loadHistory() }`, so Kotlin's declaration-order initialization had the load populate it
  and the initializer then reset it to `emptyList()`. `viewModelScope` runs on
  `Dispatchers.Main.immediate`, so collection starts synchronously and anything that emits on
  subscribe hits it — the symptom is the first filter-chip tap blanking the list. **Production was
  unaffected only because Room's flows emit asynchronously.** That is luck; one `stateIn`, cache
  or `replay` would have exposed it. The declaration must stay above `init` — there is a comment
  saying so.

**Release-build CI cost:** 9m cold, ~1m20s warm — on #121 it was faster than `build-and-test`.
The one-off cold figure is not the steady state.

**Unexplained, not reproducing:** `:feature:posture:testDebugUnitTest` failed once during a local
full-suite run on 2026-07-31 and passed on two subsequent runs, leaving **no failure entry in its
result XML** — which points at a Gradle worker dying rather than an assertion failing, plausibly
the same memory pressure as the cold-release OOM above. Green in CI. Recorded so a second sighting
is recognised as a pattern rather than treated as a first.

**When watching a device with `adb logcat`, filter by tag, not by grepping the package name.**
`AndroidRuntime.*ventouxlabs` matches sibling apps under the same vendor prefix — a
`com.ventouxlabs.relais.izzy` crash-loop was twice reported as a NetLens crash — and grepping the
package name alone also matches `adbd` echoing your own `adb shell pidof` probes. Use
`adb logcat -b crash,main AndroidRuntime:E WifiSurvey:W ActivityManager:E '*:S'`.

**A local trap, not a repo bug:** a cold `assembleFossRelease` OOMs on a many-core machine —
`org.gradle.parallel=true` with `workers.max` unset runs one Kotlin compilation per core against
a 2048m heap sized for CI's 4-core runners. Raising the heap does *not* help (Kotlin compiles
in-process via `NoIsolationWorkerFactory`, so `kotlin.daemon.jvmargs` is never consulted). Fix it
per-machine with `org.gradle.workers.max=6` in `~/.gradle/gradle.properties`. **Do not raise
`org.gradle.jvmargs` in the repo** — F-Droid builds this from source and a workstation-sized heap
is its own failure mode there. Five consecutive releases have built cold at 2048m on
`ubuntu-latest`.

**Do not reintroduce `testFixtures` source sets.** AGP 8.9 registers the variant; Kotlin 2.1.0
registers no Kotlin compilation for it, so `compileDebugTestFixturesJavaWithJavac` exists,
`compileDebugTestFixturesKotlin` does not, and Kotlin fixtures compile to nothing while consumers
fail with `Unresolved reference`. This was tried first and reverted. The `-testing` module is the
working shape.

816 unique tests, up from 752 at the last handoff. Every module has tests. (Result files aggregate to 822 because `app/src/test` runs under both flavors.)

## What device use found (2026-07-28) — two defects, both invisible to CI

The user walked a survey, captured one point, and the app closed. Root cause, from the device
crash buffer:

```
IllegalArgumentException: Key "1" was already used.
```

`WifiSurveyTab` renders captured spots and past sessions with two `items()` calls inside **one**
`LazyColumn`, both keyed on the raw row id. Points and sessions come from different tables with
independent autoincrement sequences, so point 1 and session 1 are the same key. It fires on the
first capture — the moment a survey first holds both. Sharing looked broken for the same reason:
copy/share are gated on `points.isNotEmpty()`, so the crash beat the buttons onto the screen.

Fixed in `dc03409` by namespacing through `surveyPointKey`/`surveySessionKey`, pinned by
`SurveyListKeysTest`. Repo swept for the same shape — `MonitorScreen` uses two separate
`LazyColumn`s, `HomeScreen` already namespaces (`"tool_"`/`"search_"`), and `DevicesScreen`'s two
`items()` are partitions of one list. This was the only instance.

### Second defect: the fold fix did not work

Folding mid-capture still showed *"NetLens went to the background mid-capture"* — the exact
wrong-cause message the pass-2 HIGH fix was meant to remove. No "no host Activity" warning was
logged, so the Activity unwrap succeeded and **`isChangingConfigurations` simply reports false for
a fold on a real foldable**. The fix had shipped never having been runnable on a device.

Replaced in `811c9e1` by removing the question entirely: ON_STOP always suspends the burst, and
`onScreenStarted` judges it by the elapsed gap. That deleted `isConfigurationChange`, the Activity
unwrap, the null fallback and its log — 56 deletions against 29 insertions — and specifically
deleted the one part of the feature that no unit test could reach. The surviving decision is pure
ViewModel logic with a controllable clock, pinned by tests both ways.

**Do not reintroduce `isChangingConfigurations` here.** It was tried, it reports false for a fold,
and the gap-based rule is both simpler and testable.

### Why this matters beyond the bugs

The crash was self-inflicted: converting the coverage map from one
`item{}` into per-bar `items(key = { it.id })` was a *performance fix* from an earlier review
round; before it, points had no keys and could not collide. Review pass 2 looked straight at that
line and cleared it ("uses DB primary keys, so no duplicate-key crash") — true within the list,
wrong across two lists sharing a column. **Three review passes, an adversarial round and 750 unit
tests missed a crash on the feature's primary path, and a two-minute device walk found it
instantly.** The fold defect is the same story: a fix for a HIGH finding shipped green and did not
work, because the thing it depended on could not be tested here.

That is the cost of having no Compose or instrumentation tests, stated plainly. Treat "CI is
green" as saying nothing about Compose runtime invariants or platform signals. **For this feature,
a hardware walk is a required step, not an optional one** — it is 2 for 2 against the full review
apparatus.

## Quick reference

- **Version 1.3.1 / code 15** (was wrongly listed here as 1.2.6 / 13 until 2026-08-05).
  Cert `8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4`, v2 scheme only.
- **Tests: `./gradlew testFossDebugUnitTest testGplayDebugUnitTest testDebugUnitTest`** —
  all three, always. 895 tests. Two of them is 858 and silently skips the gplay billing tests.
- Devices: Pixel 9 Pro Fold `4A111FDKD0000C`, Pixel 10 Pro Fold `57211FDCG0023C`.
  **They are no longer in the same state — read "Device state".**
- Release signing lives in `local.properties` (git-ignored) or the `RELEASE_*` env vars,
  per-field. An `*-unsigned.apk` means the wiring is wrong — fix it, don't push.
- Emulator: DO NOT attempt locally (QEMU segfaults on this kernel). Emulator-bound work goes
  through the `baseline-profile.yml` CI pattern.
- No Robolectric and no instrumentation anywhere in the repo. **There ARE 10 Paparazzi
  composition smoke tests** (`netlens.android.screenshot`) — this line used to say "no
  screenshot tests anywhere", which is wrong; what is true is that **no golden images are
  recorded or committed** and `verifyPaparazzi` never runs. They catch duplicate
  `LazyColumn` keys and composition/measure crashes, nothing visual. Note **no test renders
  a `*Screen`** — they all render the stateless `*Content` below the Scaffold's `topBar`, so
  anything in a `TopAppBar` (e.g. `ResultActions`) is uncovered. Anything
  touching `Context`, `WifiManager`, `TelephonyManager`, or a live Room/DataStore instance is
  unverifiable without a physical device — say so rather than assuming a test can be added the
  way it can elsewhere. Known-untested invariants are listed in
  `.agent_native/agent_roadmap.md` under the `core:data` Room testing backlog item.
- PR #114's branch was deleted; its `UnconfinedTestDispatcher` deflake approach measured ~35%
  failures locally and is a **dead end** — the knowledge is here, the code is gone
  deliberately. Don't retry it.
