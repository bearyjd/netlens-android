# Session Handoff — #117-#121 landed; release 1.3.0 gated on a hardware walk (2026-07-31)

Supersedes the 2026-07-27 handoff. Everything in it is now released or merged. Two
long-standing beliefs were corrected by evidence during this work — read those first, so
they don't get re-litigated.

## TL;DR — where things stand

- **v1.2.6 released** — https://github.com/bearyjd/netlens-android/releases/tag/v1.2.6
  (widget cross-render fix, 4x2 fontScale fix + enrichment, baseline profile). The
  *published* APK was downloaded and verified: cert `8fdfc928…`, `versionCode='13'`.
- **PR #116 merged** — `78fa6e0`, squashed. Device tagging, Wi-Fi coverage survey,
  launchable services. master CI green. 752 tests.
- **The survey is verified on hardware, and device use found TWO defects the reviews missed** —
  a crash on the primary path (`dc03409`) and a fold that still killed the capture (`811c9e1`).
  Capturing multiple points and sharing both work. See "What device use found".
- **F-Droid MR #42628 updated** to 1.2.6 / 13 and awaiting maintainers.
- **Only `master` exists on the remote.** All feature branches deleted.
- **Nothing is in flight.** No open PRs, no running jobs. #117/#118/#119 landed 2026-07-30 and
  #120/#121 on 2026-07-31 — see the two "landed" sections below.
- **The Pixel 10 Pro Fold is running master, not 1.2.6, and its About screen says otherwise.**
  See "Device state".
- **The two phones are on different builds** — Pixel 10 has the fix, Pixel 9 does not. See
  "Device state" before testing on either.

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

Both phones are signed with the real cert and their NetLens database is at **schema v15**, but
they are on **different builds**:

**They are no longer on the same build (2026-07-31).**

- **Pixel 10 Pro Fold `57211FDCG0023C`** — running **master @ `d7b5a38`**, a signed FOSS release
  build installed over the top with `adb install -r` (cert `8fdfc928…` verified matching, so
  data was preserved; schema still v15 both sides, so the destructive-downgrade path never
  triggered). **Its About screen still reads 1.2.6 / versionCode 13** — `gradle.properties` was
  deliberately not bumped, since that is release prep. So the version string on this phone is a
  lie: it is post-#117 master, which includes the PortScan prefill fix and the #116 LOWs.
- **Pixel 9 Pro Fold `4A111FDKD0000C`** — untouched, still the real 1.2.6 / 13 from `811c9e1`.

Reinstall from master after any survey change rather than assuming. To build one:
`./gradlew assembleFossRelease` — add `--max-workers=6` on a many-core machine or it OOMs, see
the note above.

- **Do not install v1.2.6 on them.** It is schema **v14**, and `provideDatabase` has
  `fallbackToDestructiveMigrationOnDowngrade` — Room will **wipe the database**.
- A build from current master is fine (also v15).
- **On-device UI automation on these phones is unreliable.** GrapheneOS updates, Settings and
  the user's own apps repeatedly stole foreground mid-sequence. A capture guarded on
  `topResumedActivity` is **not sufficient** — the notification shade overlays the app without
  changing it, and one capture caught personal notifications. Prefer having the user drive the
  UI; the survey needs someone walking the house anyway.

## Open items

1. **Confirm the fold fix on hardware.** Multiple-point capture and sharing are verified. The
   fold fix (`811c9e1`) is on both phones but **not yet confirmed by a walk**: fold mid-capture
   should now keep counting, and backgrounding past 3s should discard with `CAPTURE_INTERRUPTED`.
   Until someone folds a phone mid-burst, that fix has the same status the last one had when it
   turned out not to work.
2. **Next release is 1.3.0** — needs `versionCode 14` and
   `fastlane/metadata/android/en-US/changelogs/14.txt`. The `[Unreleased]` CHANGELOG block is
   already written and describes #116's three features; **it does not yet mention #117's fixes**.
   Signing path is proven, so `/android-release` should be mechanical. **Do item 1 first** — the
   survey is the headline feature of this release and is 0 for 2 on shipping unverified.
3. **F-Droid MR** — awaiting maintainers; read the thread manually (API 401s).
4. **Play Console** — manual; the v1.2.6 gplay AAB is attached to the GitHub release.
   Checklist in `docs/play-store.md`.
5. ~~`PortScanScreen.kt` prefill bug~~ — **done, PR #117.**
6. ~~Deferred LOWs from #116~~ — **done, PR #117.**
7. **`.claude/PRPs` is gitignored**, so review artifacts (including
   `.claude/PRPs/reviews/pr-116-review.md`) are local-only and lost on a fresh clone. Already
   tracked in `.agent_native/agent_roadmap.md` as needing a human decision.
8. **A device check for the PortScan prefill path** is unticked on #117: "scan this host" from LAN
   Scan, then fold mid-edit. Same reasoning as item 1 — the prefill fix is Compose lifecycle
   behaviour, which nothing in CI reaches.

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

**Release-build CI cost:** 9m cold, ~1m20s warm — on #121 it was faster than `build-and-test`.
The one-off cold figure is not the steady state.

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

800 unique tests, up from 752 at the last handoff. (Result files aggregate to 822 because `app/src/test` runs under both flavors.)

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

- Version 1.2.6 / code 13. Cert `8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4`.
- Devices: Pixel 9 Pro Fold `4A111FDKD0000C`, Pixel 10 Pro Fold `57211FDCG0023C`.
- Release signing lives in `local.properties` (git-ignored) or the `RELEASE_*` env vars,
  per-field. An `*-unsigned.apk` means the wiring is wrong — fix it, don't push.
- Emulator: DO NOT attempt locally (QEMU segfaults on this kernel). Emulator-bound work goes
  through the `baseline-profile.yml` CI pattern.
- No Robolectric, no instrumentation, no screenshot tests anywhere in the repo. Anything
  touching `Context`, `WifiManager`, `TelephonyManager`, or a live Room/DataStore instance is
  unverifiable without a physical device — say so rather than assuming a test can be added the
  way it can elsewhere. Known-untested invariants are listed in
  `.agent_native/agent_roadmap.md` under the `core:data` Room testing backlog item.
- PR #114's branch was deleted; its `UnconfinedTestDispatcher` deflake approach measured ~35%
  failures locally and is a **dead end** — the knowledge is here, the code is gone
  deliberately. Don't retry it.
