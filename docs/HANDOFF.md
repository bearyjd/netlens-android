# Session Handoff — Widget Cross-Render Bug Root-Caused & Fixed (2026-07-21, late)

Supersedes the earlier 2026-07-21 handoff (baseline profile landed, widget bug mid-investigation). That investigation is now **resolved** — see below.

## TL;DR — where things stand right now

- **PR #108 merged** — baseline profile module + CI-generated profile on master.
- **PR #109 merged** (`de33d32`) — all 9 devil's-advocate follow-ups from #108 review. Both flavors carry the profile now.
- **PR #110 OPEN** (`fix/widget-cross-render-mapping`) — **fixes the widget wrong-layout bug**. Root cause found, fix implemented, **verified on both physical phones** (install -r over v1.2.5). Watching CI at handoff time. Merge is the remaining step.
- **This machine cannot run the Android emulator** (QEMU segfaults on kernel 6.19.11-ogc1.1; every emulator/config). Emulator-bound work goes through the `baseline-profile.yml` CI pattern. Memory file: `no-local-android-emulator.md`.

## RESOLVED: widget rendering bug (PR #110)

**Symptom:** home-screen widgets render the wrong layout. Pixel 10 **4x2** (`FourByTwoWidgetReceiver`, appwidget **id=2**) drew `CompactFullContent` (two `defaultWeight()` rows — flag+lock+WAN over signal+LAN, stretched with a big gap). The **4x1 drifted the same way over time** (user-confirmed). The prior handoff guessed "stale receiver→widget mapping for one instance" — right neighborhood, but it's the **refresh dispatch**, and it degrades across *multiple* instances.

**Root cause (confirmed):** `refreshAllWidgets` / `WidgetRefreshWorker` updated widgets **by class** via `GlanceAppWidget.updateAll()`. `updateAll` resolves ids through Glance's persisted `providerToReceiver` DataStore map. That map degrades for instances placed under earlier builds; when stale, `CompactWidget().updateAll()` resolves a FourByTwo id and pushes compact `RemoteViews` onto it. Static wiring (manifest receivers, `glanceAppWidget` overrides, content composables) was always correct — only the dynamic dispatch trusted a corruptible map. Confirmed by fresh screenshots: id=2's layout is `CompactFullContent`'s exact two-weighted-row shape (`DashboardWidgetContent` would put WAN+LAN side-by-side; it doesn't), and only the older low-id instances were affected.

**Fix:** dispatch per receiver via `AppWidgetManager.getAppWidgetIds(ComponentName(context, receiver))`, pairing each id with the widget class its receiver declares (authoritative `WIDGET_RECEIVERS` list in `widget/.../WidgetRefresh.kt`). A corrupt Glance map can no longer cross-render; the next refresh self-heals affected instances. `WidgetRefreshWorker` now delegates to the shared `refreshAllWidgets` helper. Files: `WidgetRefresh.kt`, `WidgetRefreshWorker.kt`.

**Verification:** `:widget:compileDebugKotlin` + `testFossDebugUnitTest testDebugUnitTest` green. Signed FOSS release, cert continuity `8fdfc928…`, `adb install -r` over v1.2.5 on **Pixel 9 + Pixel 10** (state preserved). Pixel 10 4x2 (id=2 — Compact minutes earlier) now renders full 4x2 (header/sparkline/status/chips); header "Scanned just now" proves the **new** worker path repainted it. Both 4x1s correct. Screenshots in the session scratchpad (`before-*`, `postinstall-*`).

**Honest caveat:** `install -r` also repopulated Glance's map to healthy, so the device test proves the new path *works*; the *robustness* claim (immune to future degradation) rests on the code change — dispatch now keys off `ComponentName`, which is authoritative and cannot cross-map.

**Still open (separate concern):** the fontScale-1.3 sizing/overflow ("font doesn't fit the 4x2") originally reported is NOT addressed by #110 — that's a real secondary issue (fixed-sp/dp text, single `SizeMode.Responsive` bucket, no fontScale compensation). Plan if picked up: fontScale-compensated text helper + real size buckets + drop-sections-when-short for 4x2.

## How to work the widgets (device notes)

- **Cannot `am broadcast APPWIDGET_UPDATE` from adb** on these Android builds — `SecurityException: unknown caller`. Trigger refreshes via the widget's refresh button, a network toggle (fires the receiver's network callback → `enqueueWidgetRefresh` → worker), or `install -r` (package replace re-broadcasts update to providers).
- Installed app is **release-signed** → no `run-as`, can't inspect the Glance DataStore on-device. Don't `adb uninstall` a device that reproduces a widget bug — that destroys the on-device state that IS the repro.
- **Screencap gotchas:** folds report multiple displays; screencap to a file on-device and `adb pull` (don't mix `exec-out` with stdout). ALWAYS confirm the launcher is frontmost (`dumpsys activity activities | grep topResumedActivity`) before capturing — the user's private apps can be foreground.
- appwidget ids/providers: `adb -s <serial> shell dumpsys appwidget`. `min=(WxH)` values are TypedValue-complex-encoded dp (e.g. `28161`→110dp, `64001`→250dp).

## Open items

1. **Merge PR #110** (widget fix; user's call once CI green).
2. **Widget sizing/fontScale overflow** — separate open bug (above).
3. **Speedtest test hardening** — `SpeedTestEngineImplTest` "download aggregates bytes across four parallel streams" and "final download speed excludes warm-up bytes" flaked once on a loaded runner; timing-sensitive. `test-engineer` + fake-clock/deterministic scheduling.
4. **Play Console bootstrap** — unchanged (manual; checklist in `docs/play-store.md`).
5. **Rotate release keystore passwords** — still outstanding (exposed in a July terminal session; user-only).
6. **F-Droid MR #42628** — awaiting maintainer merge; recipe synced to 1.2.5/12.

## Quick reference

- Version: 1.2.5 / versionCode 12. Cert SHA-256 `8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4` (continuity confirmed through v1.2.5; the local signed FOSS release built this session matches).
- Devices: Pixel 9 Pro Fold `4A111FDKD0000C` (has the 4x1, outer screen), Pixel 10 Pro Fold `57211FDCG0023C` (4x2 id=2 + 4x1 id=3). Both Android "17" pre-release, font_scale 1.3. Signed local builds `adb install -r` over the installed release without data loss.
- Widgets: 4 receivers → 4 GlanceAppWidget classes → content composables. Compact(2x1, 110×40)→`CompactFullContent`; Standard(2x2, 110×110)→`StandardWidgetContent`; Dashboard(4x1, 250×50)→`DashboardFullContent`; FourByTwo(4x2, 250×110)→`FourByTwoWidgetContent` (which embeds `DashboardWidgetContent` at `showHeader=false`). All updates flow through `refreshAllWidgets`.
- No Robolectric/instrumentation in the repo — widget rendering has no automated test; physical-device verification is the only path.
- Emulator: DO NOT attempt locally (segfault, diagnosed exhaustively). Baseline profile regen: dispatch `baseline-profile.yml` from a BRANCH (refuses master); bot-commit CI runs land as `action_required` → `gh api -X POST repos/bearyjd/netlens-android/actions/runs/<id>/approve`.
