# Session Handoff — v1.2.6 shipped; #116 merged after three review passes (2026-07-28)

Supersedes the 2026-07-27 handoff. Everything in it is now released or merged. Two
long-standing beliefs were corrected by evidence during this work — read those first, so
they don't get re-litigated.

## TL;DR — where things stand

- **v1.2.6 released** — https://github.com/bearyjd/netlens-android/releases/tag/v1.2.6
  (widget cross-render fix, 4x2 fontScale fix + enrichment, baseline profile). The
  *published* APK was downloaded and verified: cert `8fdfc928…`, `versionCode='13'`.
- **PR #116 merged** — `78fa6e0`, squashed. Device tagging, Wi-Fi coverage survey,
  launchable services. master CI green. 750 tests.
- **F-Droid MR #42628 updated** to 1.2.6 / 13 and awaiting maintainers.
- **Only `master` exists on the remote.** All feature branches deleted.
- **Nothing is in flight.** No open PRs, no running jobs.

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
- `MAX_CAPTURE_GAP_MS` (3s) bounds how long a capture may be held across a configuration
  change. A fold survives; a pocketed phone doesn't.

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

- **Pixel 10 Pro Fold `57211FDCG0023C` — versionCode 13**, a master build including the
  duplicate-key crash fix. This is the one the survey was verified on.
- **Pixel 9 Pro Fold `4A111FDKD0000C` — versionCode 12**, a pre-refactor #116 branch build. It
  still has the crash. Reinstall from master before testing anything on it.

- **Do not install v1.2.6 on them.** It is schema **v14**, and `provideDatabase` has
  `fallbackToDestructiveMigrationOnDowngrade` — Room will **wipe the database**.
- A build from current master is fine (also v15).
- **On-device UI automation on these phones is unreliable.** GrapheneOS updates, Settings and
  the user's own apps repeatedly stole foreground mid-sequence. A capture guarded on
  `topResumedActivity` is **not sufficient** — the notification shade overlays the app without
  changing it, and one capture caught personal notifications. Prefer having the user drive the
  UI; the survey needs someone walking the house anyway.

## Open items

1. **Survey verified on hardware (2026-07-28) — and the walk found a crash the reviews didn't.**
   See "The crash a device walk found" below. Capturing multiple points and sharing both work.
   Still NOT exercised: fold mid-capture (must survive) and background past
   `MAX_CAPTURE_GAP_MS` (must discard with `CAPTURE_INTERRUPTED`). Those two paths remain
   device-only and unverified.
2. **Next release is 1.3.0** — needs `versionCode 14` and
   `fastlane/metadata/android/en-US/changelogs/14.txt`. The `[Unreleased]` CHANGELOG block is
   already written and describes #116's three features. Signing path is proven, so
   `/android-release` should be mechanical.
3. **F-Droid MR** — awaiting maintainers; read the thread manually (API 401s).
4. **Play Console** — manual; the v1.2.6 gplay AAB is attached to the GitHub release.
   Checklist in `docs/play-store.md`.
5. **`PortScanScreen.kt` prefill bug** — `var host by rememberSaveable { mutableStateOf("") }`
   never syncs with `initialHost`, so "scan this host" from another tool silently does nothing.
   Now safe to fix (host validation landed in #116).
6. **Deferred LOWs from #116** (documented, not lost): `sanitizeHost` rejects underscore
   hostnames (`my_nas`), the sibling `onNavigateToTool` URI is still unsanitized, survey errors
   stick after recovery, and one test depends on `replay = 1` delivery order.
7. **`.claude/PRPs` is gitignored**, so review artifacts (including
   `.claude/PRPs/reviews/pr-116-review.md`) are local-only and lost on a fresh clone. Already
   tracked in `.agent_native/agent_roadmap.md` as needing a human decision.

## The crash a device walk found (2026-07-28)

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

**Why it matters beyond the bug.** It was self-inflicted: converting the coverage map from one
`item{}` into per-bar `items(key = { it.id })` was a *performance fix* from an earlier review
round; before it, points had no keys and could not collide. Review pass 2 looked straight at that
line and cleared it ("uses DB primary keys, so no duplicate-key crash") — true within the list,
wrong across two lists sharing a column. **Three review passes, an adversarial round and 750 unit
tests missed a crash on the feature's primary path, and a two-minute device walk found it
instantly.** That is the cost of having no Compose UI tests, stated plainly. Treat "CI is green"
as saying nothing about Compose runtime invariants.

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
