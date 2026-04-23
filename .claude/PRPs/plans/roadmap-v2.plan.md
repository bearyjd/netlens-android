# Plan: NetLens Roadmap v2 — Launch Readiness

## Summary
A phased roadmap to make NetLens launch-ready: add traceroute (table-stakes missing feature), enhance LAN scanner with device fingerprinting, extract hardcoded strings for localization, configure F-Droid reproducible builds, and add WiFi speed test + outage alerts. Each phase is a separate PR.

## User Story
As a network engineer, I want a polished, comprehensive tool suite so that NetLens competes with Fing and Network Analyzer on features while staying open-source and ad-free.

## Problem → Solution
12 tools exist but lack polish; missing traceroute and device fingerprinting vs competitors; all strings hardcoded; no F-Droid distribution path → Phased improvements bringing NetLens to feature parity and distribution readiness.

## Metadata
- **Complexity**: XL (split into 7 independent PRs)
- **Source PRD**: N/A
- **Estimated Files**: ~40 across all phases

---

## Implementation Phases

Each phase is an independent PR. Phases 1-4 are launch-critical. Phases 5-7 are post-launch enhancements.

---

### Phase 1: Traceroute Feature (PR) — `feature:traceroute`
**Status**: pending
**Priority**: P0 — every competitor has this
**Complexity**: Medium (follows ping pattern exactly)

**What to build:**
- New module `feature/traceroute/`
- `TracerouteScreen` composable with host input + live hop list
- `TracerouteViewModel` with StateFlow
- `TracerouteImpl` engine using `ProcessBuilder("traceroute", "-n", "-m", "30", host)`
- Parse each hop line: hop number, IP, RTT values
- Data model: `TracerouteHop(hopNumber: Int, ip: String?, rttMs: List<Float>, isTimeout: Boolean)`
- SSRF guard: validate host via `SsrfGuard.isPrivateOrLoopback()` before running
- Register in NavHost, TopLevelDestination, settings.gradle.kts, app/build.gradle.kts

**Pattern to mirror**: `feature/ping/` — identical architecture (ProcessBuilder + Flow + line parser)

**Files to create:**
| File | Action |
|------|--------|
| `feature/traceroute/build.gradle.kts` | CREATE — copy ping's build file |
| `feature/traceroute/src/main/AndroidManifest.xml` | CREATE — namespace only |
| `feature/traceroute/src/main/kotlin/.../model/TracerouteHop.kt` | CREATE |
| `feature/traceroute/src/main/kotlin/.../engine/Tracer.kt` | CREATE — interface |
| `feature/traceroute/src/main/kotlin/.../engine/TracerImpl.kt` | CREATE — ProcessBuilder |
| `feature/traceroute/src/main/kotlin/.../TracerouteViewModel.kt` | CREATE |
| `feature/traceroute/src/main/kotlin/.../TracerouteScreen.kt` | CREATE |
| `feature/traceroute/src/main/kotlin/.../di/TracerouteModule.kt` | CREATE — Hilt binds |
| `settings.gradle.kts` | UPDATE — add include |
| `app/build.gradle.kts` | UPDATE — add dependency |
| `app/.../navigation/TopLevelDestination.kt` | UPDATE — add enum |
| `app/.../navigation/NetLensNavHost.kt` | UPDATE — add composable |

**GOTCHA**: Android may not ship `traceroute` binary on all devices. Fallback: use `ping -t <TTL>` with incrementing TTL (1..30) to simulate traceroute. Check `which traceroute` at runtime.

**Tests**: TracerouteHop parsing tests, SSRF validation test.

---

### Phase 2: LAN Scanner Device Fingerprinting (PR)
**Status**: pending
**Priority**: P1 — differentiator vs basic LAN scanners
**Complexity**: Medium

**What to build:**
- Extend `LanDevice` data class with `deviceType: String?` and `osGuess: String?`
- mDNS-based fingerprinting: query `_services._dns-sd._udp.local` for each discovered device to detect Apple/Chromecast/printer/etc service types
- NetBIOS name resolution (port 137 UDP) for Windows device identification
- SSDP/UPnP discovery (port 1900 UDP multicast) for smart home devices, media servers
- Map discovered service types to human-readable device categories
- Update `DeviceCard` UI to show device type icon + OS guess

**Pattern to mirror**: `feature/lanscan/` existing scanner + `feature/mdns/` for mDNS queries

**Files to change:**
| File | Action |
|------|--------|
| `feature/lanscan/src/.../model/LanDevice.kt` | UPDATE — add fields |
| `feature/lanscan/src/.../engine/SubnetScannerImpl.kt` | UPDATE — add fingerprinting after discovery |
| `feature/lanscan/src/.../engine/DeviceFingerprinter.kt` | CREATE — fingerprinting logic |
| `feature/lanscan/src/.../LanScanScreen.kt` | UPDATE — show device type |

**GOTCHA**: mDNS queries require `CHANGE_WIFI_MULTICAST_STATE` permission (already declared). NetBIOS and SSDP use UDP — no raw socket needed, standard `DatagramSocket` works.

---

### Phase 3: String Extraction / Localization (PR)
**Status**: pending
**Priority**: P1 — required for F-Droid community translations
**Complexity**: Medium (tedious but mechanical)

**What to build:**
- Extract all hardcoded strings from 12 feature screens + widget into `strings.xml`
- Create per-module `src/main/res/values/strings.xml` files
- Replace hardcoded strings with `stringResource(R.string.xxx)` calls
- Naming convention: `feature_action_description` e.g. `lanscan_label_vendor`, `ping_button_start`
- Keep app-level `strings.xml` for shared strings (app name, common labels)

**Scope**: Every `.kt` file containing `Text("hardcoded string")` or `contentDescription = "hardcoded"`

**Files to change:** All 12 feature Screen.kt files + widget composables + new strings.xml files (~24 files)

**GOTCHA**: Compose `stringResource()` requires the string resource to be in the same module's `res/`. Feature modules already have their own namespace, so each gets its own `strings.xml`.

---

### Phase 4: F-Droid Reproducible Builds (PR)
**Status**: pending
**Priority**: P0 — primary distribution channel
**Complexity**: Small

**What to build:**
- Add `metadata/en-US/` directory structure for F-Droid:
  - `full_description.txt`
  - `short_description.txt`
  - `changelogs/1.txt`
- Create `.fdroid.yml` (or rely on auto-detection)
- Ensure `build.gradle.kts` uses deterministic build config:
  - Pin all dependency versions (already done via version catalog)
  - Disable R8 source file/line info stripping (helps reproducibility)
  - Set `archivesBaseName` explicitly
- Add Fastlane-compatible metadata structure (F-Droid reads this)
- Add IzzyOnDroid submission metadata
- Document build instructions in README for F-Droid build server

**Files to create:**
| File | Action |
|------|--------|
| `fastlane/metadata/android/en-US/full_description.txt` | CREATE |
| `fastlane/metadata/android/en-US/short_description.txt` | CREATE |
| `fastlane/metadata/android/en-US/changelogs/1.txt` | CREATE |
| `fastlane/metadata/android/en-US/images/icon.png` | CREATE (placeholder) |
| `README.md` | UPDATE — add build instructions |

---

### Phase 5: WiFi Speed Test (PR) — post-launch
**Status**: pending
**Priority**: P2 — Fing's most popular feature
**Complexity**: Large

**What to build:**
- New module `feature/speedtest/`
- Download speed: HTTP GET a known large file, measure throughput
- Upload speed: HTTP POST random data to a test endpoint
- Use public speed test servers (e.g., Cloudflare speed.cloudflare.com endpoints)
- Live progress UI with download/upload/ping gauges
- Results history stored in Room

**GOTCHA**: Speed test servers may rate-limit or block. Use multiple fallback servers. Cloudflare's speed test API is documented and doesn't require auth.

**NOT building**: Full Ookla-style server selection. Single-server test is sufficient for v1.

---

### Phase 6: Outage Alerts (PR) — post-launch
**Status**: pending
**Priority**: P2 — differentiator for power users
**Complexity**: Large

**What to build:**
- Extend endpoint monitor with background WorkManager periodic checks
- Configurable alert thresholds (consecutive failures before alert)
- Android notification channel for outage alerts
- Notification shows: endpoint name, failure count, last success time
- Snooze/acknowledge from notification action
- Dashboard widget showing overall endpoint health (green/yellow/red)

**Dependencies**: Requires existing `feature/monitor/` endpoint monitor

**GOTCHA**: WorkManager minimum interval is 15 minutes. For faster checks, use `setExpeditedWork()` or foreground service (battery impact trade-off).

---

### Phase 7: Core 6 Polish Pass (PR) — launch-critical
**Status**: pending
**Priority**: P0 — these are what users judge first
**Complexity**: Medium

**What to build:**
Polish the 6 core tools that users evaluate first:

1. **IP Info** — Add copy-to-clipboard for each field, share button for full info
2. **LAN Scan** — Add pull-to-refresh, sort options (IP/vendor/latency), export to CSV
3. **Port Scan** — Add common port presets (web, mail, database, gaming), service name display
4. **DNS Lookup** — Add record type selector chips, copy results button
5. **Ping** — Add statistics summary (min/max/avg/jitter), chart visualization
6. **WoL** — Add saved devices list (persist MAC+label in Room), quick-send from list

Each tool gets:
- Loading/error/empty state handling audit
- Consistent Snackbar error pattern
- `rememberSaveable` for surviving config changes
- Edge case handling (no network, timeout, invalid input)

---

## Validation Commands

```bash
# Per-phase validation
./gradlew assembleDebug
./gradlew testDebugUnitTest

# Localization check
./gradlew lint 2>&1 | grep -i "hardcoded"
```

## Acceptance Criteria
- [ ] Each phase is a separate, mergeable PR
- [ ] All tests pass after each phase
- [ ] Build succeeds after each phase
- [ ] No regressions in existing features

## Risks
| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| `traceroute` binary missing on some devices | Medium | High | Fallback to TTL-incrementing ping |
| Speed test server rate limiting | Medium | Medium | Multiple fallback servers |
| F-Droid build server compatibility | Low | High | Test with `fdroidserver` locally first |
| String extraction breaks UI | Low | Medium | Visual regression test after extraction |
| WorkManager 15-min minimum for alerts | Certain | Low | Document limitation, offer foreground service option |

## Suggested Implementation Order
1. **Phase 7** (Core 6 Polish) — immediate impact, low risk
2. **Phase 1** (Traceroute) — table-stakes feature gap
3. **Phase 3** (Localization) — enables community translations
4. **Phase 4** (F-Droid) — distribution readiness
5. **Phase 2** (Device Fingerprinting) — differentiator
6. **Phase 5** (Speed Test) — post-launch
7. **Phase 6** (Outage Alerts) — post-launch

## Notes
- Phases 1-4 and 7 should ship before the launch post on HN/Reddit
- Phases 5-6 are post-launch enhancements driven by user feedback
- Each phase follows the existing convention plugin pattern (`netlens.android.feature` for new modules)
- All new network features must use `SsrfGuard` for SSRF prevention
