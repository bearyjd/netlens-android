# Session Handoff — Widgets Fixed/Enriched + Speedtest Deflaked (2026-07-23)

Supersedes the earlier 2026-07-21/22 handoffs. The three widget issues (cross-render, fontScale overflow, 4x1/4x2 sameness) and the speedtest flake are all **shipped to master**; two device-side follow-ups remain — see below.

## TL;DR — where things stand right now

- **PR #108 / #109 merged** — baseline profile module + CI profile + the 9 review follow-ups. Both flavors carry the profile.
- **PR #110 merged** (`2a8113b`) — **widget cross-render fix** (widgets drew the wrong layout). Verified on both phones.
- **PR #111 merged** (`79f2ab8`) — **4x2 fontScale-overflow fix + 4x2 enrichment** (two commits). Font pin verified on-device; enrichment CI-green but its on-device visual fit was NOT captured (phone asleep) — see follow-ups.
- **PR #115 merged** (`e876973`) — **speedtest test deflake** via a deterministic byte-source seam. CI green; 42/42 local. **PR #114 was closed** — its `UnconfinedTestDispatcher` approach was wrong (~35% fail locally); see the RESOLVED section.
- **Open PRs #112/#113** — Dependabot GitHub-Actions bumps, unrelated.
- **This machine cannot run the Android emulator** (QEMU segfaults on kernel 6.19.11-ogc1.1; every emulator/config). Emulator-bound work goes through the `baseline-profile.yml` CI pattern. Memory file: `no-local-android-emulator.md`.

## RESOLVED: fontScale overflow + 4x2 enrichment (PR #111)

**fontScale fix** (`5609573`): widget text was fixed `sp`, which Glance multiplies by the user's `font_scale`; at 1.3 the 4x2 overflowed (VPN label clipped, sections fell off). New `widgetSp()` helper (`widget/ui/WidgetTextScaling.kt`) divides design sp by the live fontScale to **pin rendered text to design pixel size** (like system clock/weather widgets); `maxScale` clamp (default `1f`) allows partial growth later. Applied to the four **4x2-only** composables (`DashboardWidgetContent`, `StatusLineContent`, `ToolChipsRow`, `WidgetHeaderRow`). **Verified fitting** on Pixel 10 at 1.3.

**4x2 enrichment** (`7d6e6af`): the 4x2 read too much like the 4x1, so it now surfaces data the worker already computes — a new `FourByTwoHeader` leading with the security grade (A–F, color-coded) + top issue (→ posture screen); ISP/carrier under the WAN IP (behind IP-info consent gate); device count + WiFi encryption on the status line's second row (replacing the redundant "Scanned" line — timestamp moved to header). All 4x2-only; 2x1/2x2/4x1 untouched.

**Verification GAP (do this):** the enrichment's on-device visual fit was never captured — the Pixel 10 was asleep/in-use the whole session (watcher timed out 3×). If the added security row overflows the 110dp card, the ready lever is **dropping the LatencySparkline** (lowest-value element) from `FourByTwoWidgetContent` to reclaim ~16dp. Screenshots to date: `before-fontscale-*`, `after-fontscale-*` (font pin confirmed); no `enriched-*` capture yet.

## RESOLVED: widget rendering bug (PR #110)

**Symptom:** home-screen widgets render the wrong layout. Pixel 10 **4x2** (`FourByTwoWidgetReceiver`, appwidget **id=2**) drew `CompactFullContent` (two `defaultWeight()` rows — flag+lock+WAN over signal+LAN, stretched with a big gap). The **4x1 drifted the same way over time** (user-confirmed). The prior handoff guessed "stale receiver→widget mapping for one instance" — right neighborhood, but it's the **refresh dispatch**, and it degrades across *multiple* instances.

**Root cause (confirmed):** `refreshAllWidgets` / `WidgetRefreshWorker` updated widgets **by class** via `GlanceAppWidget.updateAll()`. `updateAll` resolves ids through Glance's persisted `providerToReceiver` DataStore map. That map degrades for instances placed under earlier builds; when stale, `CompactWidget().updateAll()` resolves a FourByTwo id and pushes compact `RemoteViews` onto it. Static wiring (manifest receivers, `glanceAppWidget` overrides, content composables) was always correct — only the dynamic dispatch trusted a corruptible map. Confirmed by fresh screenshots: id=2's layout is `CompactFullContent`'s exact two-weighted-row shape (`DashboardWidgetContent` would put WAN+LAN side-by-side; it doesn't), and only the older low-id instances were affected.

**Fix:** dispatch per receiver via `AppWidgetManager.getAppWidgetIds(ComponentName(context, receiver))`, pairing each id with the widget class its receiver declares (authoritative `WIDGET_RECEIVERS` list in `widget/.../WidgetRefresh.kt`). A corrupt Glance map can no longer cross-render; the next refresh self-heals affected instances. `WidgetRefreshWorker` now delegates to the shared `refreshAllWidgets` helper. Files: `WidgetRefresh.kt`, `WidgetRefreshWorker.kt`.

**Verification:** `:widget:compileDebugKotlin` + `testFossDebugUnitTest testDebugUnitTest` green. Signed FOSS release, cert continuity `8fdfc928…`, `adb install -r` over v1.2.5 on **Pixel 9 + Pixel 10** (state preserved). Pixel 10 4x2 (id=2 — Compact minutes earlier) now renders full 4x2 (header/sparkline/status/chips); header "Scanned just now" proves the **new** worker path repainted it. Both 4x1s correct. Screenshots in the session scratchpad (`before-*`, `postinstall-*`).

**Honest caveat:** `install -r` also repopulated Glance's map to healthy, so the device test proves the new path *works*; the *robustness* claim (immune to future degradation) rests on the code change — dispatch now keys off `ComponentName`, which is authoritative and cannot cross-map.

**Note:** the fontScale overflow this section originally listed as open was fixed in PR #111 (above).

## RESOLVED: speedtest test flake (PR #115)

**Symptom:** `SpeedTestEngineImplTest` "download aggregates bytes across four parallel streams" and "final download speed excludes warm-up bytes and time" flaked once on a loaded CI runner (passed on rerun; identical code passed 15 min prior).

**Root cause:** the download tests fed bytes through Ktor `MockEngine`, whose reads hop off the coroutines-test **virtual clock** onto a real dispatcher. Under CI load a read can still be pending while `runTest` auto-advances virtual time through the sampling loop's `delay(EMIT_INTERVAL_MS)` steps; the engine breaks that loop on `now - startedAt >= MEASURE_WINDOW_MS` (`now` = virtual `timeSource`), so virtual time races past the window before bytes are counted → truncated `bytesTransferred` → assertion fail. Load-sensitive by construction.

**Dead end (recorded so nobody retries it):** swapping the download tests to `UnconfinedTestDispatcher` is WRONG — measured ~35% failures locally (7/20, no load); it reshuffles the emit/read interleaving instead of removing the real-async hop. That was PR #114, now closed.

**Fix:** a test-only `downloadStreamOverride` seam on `SpeedTestEngineImpl` (defaults `null`; production unchanged). The four byte-counting tests inject a **pure-coroutine byte source** that lives entirely on the virtual scheduler — no Ktor, no real async → deterministic by construction. Warm-up/steady-state/aggregation logic still exercised; the real Ktor read + error paths stay covered by the untouched non-2xx and all-streams-failing tests (still `MockEngine`). Files: `SpeedTestEngineImpl.kt`, `SpeedTestEngineImplTest.kt`.

**Verification:** 30/30 no-load + 12/12 full-module (`SpeedTestViewModelTest` included) under 22 CPU burners; CI green on #115. The flake itself was never reproducible locally (once on CI) — but a fully-virtual test cannot have a real-clock race, so the fix is sound by construction, not just by sampling. **Lesson: don't stress-test with >1× cores of CPU burners — 2× oversubscription produces Gradle build-infra failures that masquerade as flakes.**

## How to work the widgets (device notes)

- **Cannot `am broadcast APPWIDGET_UPDATE` from adb** on these Android builds — `SecurityException: unknown caller`. Trigger refreshes via the widget's refresh button, a network toggle (fires the receiver's network callback → `enqueueWidgetRefresh` → worker), or `install -r` (package replace re-broadcasts update to providers).
- Installed app is **release-signed** → no `run-as`, can't inspect the Glance DataStore on-device. Don't `adb uninstall` a device that reproduces a widget bug — that destroys the on-device state that IS the repro.
- **Screencap gotchas:** folds report multiple displays; screencap to a file on-device and `adb pull` (don't mix `exec-out` with stdout). ALWAYS confirm the launcher is frontmost (`dumpsys activity activities | grep topResumedActivity`) before capturing — the user's private apps can be foreground.
- appwidget ids/providers: `adb -s <serial> shell dumpsys appwidget`. `min=(WxH)` values are TypedValue-complex-encoded dp (e.g. `28161`→110dp, `64001`→250dp).

## Open items

1. **Verify enriched 4x2 fit on device** (PR #111 already merged) — capture the Pixel 10 4x2 on its home screen; if the security row overflows, drop the sparkline (see the fit-gap note above). Also **grant IP-info consent on the Pixel 10** (NetLens → IP Info) so WAN + ISP populate there — until then those two fields are blank by design (`ipInfoConsentGranted` defaults false; gates `fetchIpInfo()` in `WidgetRefreshWorker`).
2. **Merge or close Dependabot PRs #112/#113** — GitHub-Actions version bumps (setup-java 5.5→5.6, checkout 7.0.0→7.0.1); trivial, just need a glance.
3. **Play Console bootstrap** — unchanged (manual; checklist in `docs/play-store.md`).
4. **Rotate release keystore passwords** — still outstanding (exposed in a July terminal session; user-only).
5. **F-Droid MR #42628** — awaiting maintainer merge; recipe synced to 1.2.5/12.

## Quick reference

- Version: 1.2.5 / versionCode 12. Cert SHA-256 `8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4` (continuity confirmed through v1.2.5; the local signed FOSS release built this session matches).
- Devices: Pixel 9 Pro Fold `4A111FDKD0000C` (has the 4x1, outer screen), Pixel 10 Pro Fold `57211FDCG0023C` (4x2 id=2 + 4x1 id=3). Both Android "17" pre-release, font_scale 1.3. Signed local builds `adb install -r` over the installed release without data loss.
- Widgets: 4 receivers → 4 GlanceAppWidget classes → content composables. Compact(2x1, 110×40)→`CompactFullContent`; Standard(2x2, 110×110)→`StandardWidgetContent`; Dashboard(4x1, 250×50)→`DashboardFullContent`; FourByTwo(4x2, 250×110)→`FourByTwoWidgetContent` (which embeds `DashboardWidgetContent` at `showHeader=false`). All updates flow through `refreshAllWidgets`.
- No Robolectric/instrumentation in the repo — widget rendering has no automated test; physical-device verification is the only path.
- Emulator: DO NOT attempt locally (segfault, diagnosed exhaustively). Baseline profile regen: dispatch `baseline-profile.yml` from a BRANCH (refuses master); bot-commit CI runs land as `action_required` → `gh api -X POST repos/bearyjd/netlens-android/actions/runs/<id>/approve`.
