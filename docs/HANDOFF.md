# Session Handoff — Devices Feature, Speed-Test Fixes, Perf Batch, v1.2.5 Release (2026-07-16 → 2026-07-21)

Supersedes the previous handoff (widget previews, F-Droid unblock, LAN scan fix, Play prep, 2026-07-12 → 2026-07-16) — that work is done and shipped in v1.2.3/v1.2.4. This covers what happened after.

## TL;DR — where things stand right now

- **Latest shipped: v1.2.5** (versionCode 12), on GitHub Releases with signed FOSS/GPlay APK+AAB, CI-green. Adds the **Devices** tool + background watch, three rounds of **speed-test fixes**, a **performance batch**, and an **in-app version display**.
- **Both test phones** (Pixel 10 Pro Fold, Pixel 9 Pro Fold — both Android 17 / API 37) run the **release-signed v1.2.5** build; debug builds removed.
- Working tree clean, `master == origin/master`. One feature branch open: `perf/baseline-profile` (draft PR #108).
- **Signing cert unchanged**: `8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4` — confirmed continuous through v1.2.5.

## What shipped this session (all on master, in v1.2.5)

1. **Devices feature** — 10-task subagent-driven plan (spec → plan → build → review per task). New `feature:devices` tool: persistent device inventory (custom names, first/last seen, search) + Pro background watch that notifies when a new device joins a *watched* network. Network identity = gateway MAC (no background location permission), cadence 15 m–6 h. Extracted the LAN scan engines + a shared `DeviceInventoryRepository` into new module **`core:scan`** (used by both lanscan and devices). Room **v12→v13** (`WatchedNetworkEntity`, `KnownDeviceEntity.customName/networkId`).
2. **Speed test — three root-caused rounds** (`/investigate`), all verified on-device against a browser reference:
   - **Latency** was a full HTTPS request (195 ms vs 8 ms) → rewrote as median TCP-connect RTT.
   - **Throughput** was Ktor CIO's connection handling (~⅓) → swapped speed-test engine to **OkHttp** (connection reuse + HTTP/2, KTOR-6503), parallel-stream steady-state measurement.
   - **Download read 0** → requested 120 MB/stream but Cloudflare `/__down` returns HTTP 403 for a `bytes` param over ~100 MB (probed: 90 MB→200, 100 MB→403); now requests 50→75 MB and fails loudly on non-2xx. Room **v13→v14** added `SpeedTestHistoryEntry.latencyMethod` to tag old vs new latency rows. Also a metered-connection data-use warning.
3. **Performance batch** — six reviewed fixes: LAN-scan emission batching (was O(n²log n) recompose storm per host), HomeScreen 1 Hz-tick isolation, `remember` fixes in Wifi/Devices, keystore read off the main thread, WorkManager on-demand init, Compose stability config.
4. **Review follow-ups** (6) + **CHANGELOG**, **in-app version display** (Settings › About, read from PackageManager), **codemap/docs refresh** (32 modules, Room v14).

## Open items (all your call / external)

1. **Baseline profile — draft PR #108** (`perf/baseline-profile`). Module, wiring, generator, and `androidx.baselineprofile` 1.4.0 bump are all correct. **Blocked: androidx.baselineprofile tooling doesn't support Android 17 / API 37** — a cascade of benchmark-vs-OS failures (launch-confirm ViewRootImpl-hash bug + `pm dump-profiles` parser bug, both fixed by 1.4.0; then ART returns an **empty profile** on Android 17, the hard wall). Both test phones are Android 17, so neither can generate. **To finish:** run `./gradlew :app:generateFossReleaseBaselineProfile` on an **API ≤36 emulator/device**, commit the generated `app/src/foss/generated/baselineProfiles/baseline-prof.txt`, then #108 merges. Full diagnosis in the PR body and `.omc/skills/baseline-profile-api37-expertise.md`.
2. **Play Console bootstrap** — the signed **gplay AAB is on the v1.2.5 release**. Play's API can't do the first-ever upload for a new package; it's a manual Console bootstrap (create app, Data Safety form, content rating, upload one AAB). Full checklist in `docs/play-store.md`. Once bootstrapped **and** the `PLAY_SERVICE_ACCOUNT_JSON` repo secret is set, future uploads automate via the `play-publish.yml` workflow (`gh workflow run play-publish.yml -f track=internal -f release_status=draft`).
3. **Rotate the release keystore passwords** — `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD` appeared in this session's terminal output. Only you can do this.
4. **F-Droid MR #42628** — still CI-green, awaiting maintainer `licaon-kter` to merge (nothing on our side). The in-repo recipe (`fdroid/com.ventouxlabs.netlens.yml`) is synced to 1.2.5/12; F-Droid auto-tracks the `v1.2.5` tag via `UpdateCheckMode: Tags` on its next sync (~24–48 h after merge).
5. **Deferred code follow-ups** (non-blocking, filed): speed-test download re-request loop for gigabit+ links (single-request fill tops out ~300 Mbps aggregate); a few device-inventory minors (see `.superpowers/sdd/progress.md` if present, and the review reports).

## Quick reference

- **Current version**: `netlens.versionName=1.2.5`, `netlens.versionCode=12` (`gradle.properties`).
- **Signing cert SHA-256** (verify continuity every release): `8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4`.
- **Release ritual**: `/android-release` skill (`.claude/skills/android-release/SKILL.md`) — pre-flight → signed local build → cert continuity → tag → push → verify. Note: a local `clean assembleRelease bundleRelease` can OOM the Kotlin daemon; retry with `-Pkotlin.daemon.jvmargs=-Xmx4g --max-workers=3` (env-only, not committed).
- **Design token source of truth**: `core/ui/NetLensPalette.kt` — never hardcode a color literal outside it.
- **Attached devices**: Pixel 10 Pro Fold `57211FDCG0023C`, Pixel 9 Pro Fold `4A111FDKD0000C` (both Android 17). Debug↔release sig differs → swapping builds needs uninstall (loses local data).
- **F-Droid MR**: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42628. GitLab API from this sandbox hits intermittent TLS timeouts (~1-in-3); plain retry succeeds. GitHub `gh` API was also flaky this session — retry rather than assume failure.
- **Learned skills** (this-machine-local, gitignored `.omc/skills/`): baseline-profile-api37, room-migration-schema-default, compose-stability-config.
