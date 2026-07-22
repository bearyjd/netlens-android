# Session Handoff — Baseline Profile Landed, Widget Rendering Bug Mid-Investigation (2026-07-21)

Supersedes the v1.2.5 handoff (Devices, speed-test, perf). That work is shipped; this covers the day after.

## TL;DR — where things stand right now

- **PR #108 merged** — baseline profile module + CI-generated profile on master, CI green.
- **PR #109 open and fully green, ready to merge** — applies all 9 items from the devil's-advocate review of #108. Merge is the only remaining step.
- **ACTIVE BUG, investigation mid-flight**: the NetLens home-screen widget renders the WRONG layout — a 4x2 (`FourByTwoWidgetReceiver`) instance is drawing what looks like the 2x1 `CompactFullContent`. Root cause NOT yet confirmed. Full evidence and next probes below — read before touching widget code.
- **This machine cannot run the Android emulator** (QEMU segfaults on kernel 6.19.11-ogc1.1; both emulator 35.3.11 and 37.2.1, every config). Anything emulator-bound goes through the `baseline-profile.yml` CI pattern. SDK emulator upgraded to 37.2.1; old one at `~/Android/Sdk/emulator.bak-35.3.11`.

## What happened this session (chronological)

1. **Baseline profile unblocked and merged (#108).** Local emulation proved impossible (7 crash configs), so generation moved to CI: `.github/workflows/baseline-profile.yml` boots an API 34 x86_64 emulator with KVM on a GitHub runner, runs the macrobenchmark journey, commits the profile back to the dispatching branch. Verified: `assets/dexopt/baseline.prof` embedded in the release APK. Squash-merged as `79c9414`.
2. **Devil's-advocate review of #108** produced 9 action items (2 HIGH: gplay flavor shipped with NO profile; workflow could bot-commit to master bypassing CI since GITHUB_TOKEN pushes don't trigger workflows).
3. **PR #109** (`fix/baseline-profile-review`) applies all 9: `mergeIntoMain = true` (profile now `app/src/main/generated/baselineProfiles/`, both flavors verified), workflow refuses master + rebases before push + dead trigger removed + task renamed to `generateBaselineProfile` (mergeIntoMain kills per-variant tasks), generator split into startup-only + journey passes (previously byte-identical profiles), `scrollGrid` fails loudly via `checkNotNull`, new path-filtered `profile-check.yml` (asserts profile lands in release APK on PRs), `/android-release` pre-flight warns on stale profile, `android-test` TOML alias dropped (unusable: AGP on classpath via build-logic → version-carrying alias fails; module uses `id()` form, documented inline). Regeneration re-ran on the branch: startup (21,357 rules) now distinct from baseline (23,040).
4. **Known CI flake**: `SpeedTestEngineImplTest` — "download aggregates bytes across four parallel streams" and "final download speed excludes warm-up bytes and time" failed once on a loaded runner (identical code passed 15 min prior; trigger commit only touched profile .txt files). Passed on rerun. Timing-sensitive; candidate for hardening.
5. **Widget bug reported** ("font does not fit the 4x2 widget… guess all widgets are messed up on font sizes") → `/investigate` in progress, see below.

## ACTIVE: widget rendering investigation — evidence so far

**Report:** 4x2 widget content doesn't fit / looks wrong on the test phones.

**Confirmed facts (evidence, not guesses):**
- Both phones run **font_scale = 1.3** (`adb shell settings get system font_scale`).
- The widget lives on the **Pixel 9 Pro Fold (4A111FDKD0000C), outer screen**. Pixel 10 (57211FDCG0023C) is adb-authorized now.
- `dumpsys appwidget` (Pixel 9): the ONLY NetLens instance is **id=34, provider `FourByTwoWidgetReceiver`**, host launcher3.
- Screenshot of that instance (see `scratchpad/widget-crop.png` if session dir survives; else re-capture): renders **two rows only** — `[🇺🇸][lock] WAN <ip>` and `[signal bars] LAN <ip>` — labels INLINE left of IPs, right half of the card empty, none of FourByTwo's sections (header row, dividers, sparkline, status line, tool chips).
- Layout-structure match: **`CompactFullContent`** (2x1) — its `IpText` puts an 8sp label inline before an 11sp bold IP, `FlagAndLock` leads the WAN row, `SignalBlock` leads the LAN row. `DashboardFullContent` stacks label ABOVE IP (doesn't match); `DashboardWidgetContent`/`FourByTwoWidgetContent` is three-column (doesn't match).
- Receiver→widget wiring in code is correct (each `*Receiver.glanceAppWidget` = its own widget class). Widget code unchanged since before v1.2.5 shipped.
- All four widgets: single `SizeMode.Responsive` bucket each (`FourByTwo` = 250×110), fixed sp text everywhere, no fontScale compensation.

**Working hypothesis (unconfirmed):** cross-widget content mix-up — the FourByTwo instance is showing RemoteViews composed for the Compact widget. Suspects, in order:
1. Glance `GlanceAppWidgetManager` receiver→widget mapping staleness (mapping datastore records class per receiver on update; an app update or the shared `WidgetRefresh`/`WidgetRefreshWorker` path calling `CompactWidget().updateAll(ctx)` etc. — `updateAll` filters by `getGlanceIds(javaClass)`, which is only as correct as the stored mapping).
2. The fixed-sp/fixed-dp + 1.3 font-scale overflow is REAL but SECONDARY — it explains "font doesn't fit," not "wrong layout." Fix both, but root-cause the mix-up first.

**Next probes (do these before writing any fix):**
1. Tap the widget's refresh (or run `adb shell am broadcast` for `TriggerScanAction`/`WidgetRefreshWorker`) → re-screenshot. Does content change/correct itself?
2. Remove + re-add a fresh 4x2 widget on the Pixel 9 → screenshot. Fresh instance correct ⇒ stale-mapping theory gains weight.
3. Grep Glance version in `gradle/libs.versions.toml` (Glance 1.1.1) for known `updateAll`/mapping bugs; check Glance 1.1.x release notes.
4. If needed: signed release build with extra logging installs OVER v1.2.5 without data loss — local signing env works (`assembleFossRelease` produces a signed APK, cert `8fdfc928…`; `adb install -r`).
5. THEN the sizing fix (separate concern): fontScale-compensated text helper + real size buckets + drop-sections-when-short for 4x2. Planned but NOT started — no widget code has been modified.

**Screencap gotchas:** folds report multiple displays (warning on stdout — pipe screencap to a file on-device and `adb pull`, don't `exec-out` mixed with stdout). FIRST capture accidentally caught the user's private Messages app — always confirm the home screen is frontmost before capturing, and delete strays immediately.

## Open items

1. **Merge PR #109** (user's call; it's green).
2. **Widget investigation** — continue from probes above.
3. **Speedtest test hardening** — the two flaky tests above; `test-engineer` + fake-clock/deterministic scheduling.
4. **Play Console bootstrap** — unchanged from last handoff (manual; checklist in `docs/play-store.md`).
5. **Rotate release keystore passwords** — still outstanding (exposed in a July terminal session; user-only).
6. **F-Droid MR #42628** — still awaiting maintainer merge; recipe synced to 1.2.5/12.

## Quick reference

- Version: 1.2.5 / versionCode 12. Cert SHA-256 `8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4` (continuity confirmed through v1.2.5).
- Devices: Pixel 9 Pro Fold `4A111FDKD0000C` (widget here, outer screen), Pixel 10 Pro Fold `57211FDCG0023C` (adb newly authorized). Both Android 17, font_scale 1.3. Release-signed v1.2.5 installed; signed local builds can `adb install -r` over it.
- Baseline profile regen: dispatch `baseline-profile.yml` from a BRANCH (`gh workflow run baseline-profile.yml --ref <branch>`); it refuses master by design. Bot-commit CI runs land as `action_required` → approve with `gh api -X POST repos/bearyjd/netlens-android/actions/runs/<id>/approve`.
- Emulator: DO NOT attempt locally (segfault, diagnosed exhaustively 2026-07-21). Memory file: `no-local-android-emulator.md`.
- `/android-release` pre-flight gained step 7: baseline-profile staleness warning.
