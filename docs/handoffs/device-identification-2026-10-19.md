# Claude handoff — device identification and inventory

Date: 2026-10-19. Repository: `/var/home/user/Documents/vibe-code/netlens-android`.

## User intent and authorization

User asked where NetLens stands on identifying/cataloging devices like Fing, then requested a handoff to Claude. This was a status investigation: no new identification implementation or broad implementation plan has been approved. Continue from the findings below; agree scope before broad changes.

## Repository and completed cleanup

- Current checkout: `fix/4x2-short-height`, HEAD `bfaff3a4d72791cca584c985d1f0e5e5cde2eb92`.
- That widget branch was deliberately closed **without merging**: user said widgets are fine and explicitly requested closing #172 and #178. Do not revive widget work.
- PRs #177, #163, #179, #183, #184, #185 merged; issue #169 closed; overlapping Renovate onboarding #181 closed unmerged.
- Last verified master: `f1064bff8311ca47818074b9a0ab07e8d91546c6`. CI https://github.com/bearyjd/netlens-android/actions/runs/39189269484 passed debug build, all three unit-test tasks, release APK/AAB builds, R8 and lintVital.
- GitHub open issue/PR lists were empty after the final closures. Recheck before relying on that snapshot.
- Preserve user changes: modified `.gitignore` adds `/graft/`; untracked `.ignore`. This handoff is also uncommitted.
- Before implementation, move to an appropriate branch based on current remote master while preserving those changes. Do not carry the closed widget diff into new work. Never push without user instruction.

## Implemented and released

- Persistent inventory, friendly names, known/new marking, search, first/last seen: v1.2.5, July 20.
- Pro background watch and new-device alerts; watched-network identity uses gateway MAC. Lightweight background scans use ping/ARP/OUI, not the full fingerprint probes.
- Tags, notes, physical location and tag filters: v1.3.0, July 31. Scanner updates preserve user-authored metadata.
- Saved immutable scan inventories and export: v1.3.1. Inventory text export includes names, IP/MAC, vendor, known/new and user annotations.
- Confidence-scored fingerprints: PR #166 merged September 6; released v1.3.7. Stronger signals replace weaker guesses; confidence/evidence persist.
- Foreground signals: MAC OUI vendor lookup, hostnames, mDNS services, SSDP, NetBIOS and host-detail port probes.

## Confirmed gaps in current code

1. **Manufacturer/model catalog:** SSDP parses `modelName`, but only uses it for a generic TV heuristic. `LanScanViewModel` drops the model when constructing `LanDevice`; neither that model nor `KnownDeviceEntity` has distinct manufacturer/model fields. Vendor alone is retained.
2. **Manual classification corrections:** friendly name/tags/notes/location are editable; detected type/OS/model are not. Any future override design must preserve user corrections across scans and distinguish inferred values from explicit overrides.
3. **Recognition breadth:** current identification is a local heuristic rule set, not a broad model database. Confidence values are fixed rule weights, not calibrated probabilities.
4. **Confidence precision:** `PortFingerprint` still shares one confidence value between type and OS; a strong OS signal can overstate confidence in a weak inherited type. Port enrichment updates host-detail state, not persistent inventory. This documented deferral remains real.
5. **Identity/history:** MAC-first matching, with IP fallback for MAC-less rows; later MAC discovery upgrades an IP-only row. Queries lack network qualification. Rotating MACs can duplicate devices; reused private IPs can conflate MAC-less devices. `networkId` records only the last tagged watched network; timestamps/snapshots are not a per-device network/event timeline.
6. Per-device alert muting and alert-history screen were excluded from inventory v1.

**Prior explicit user decision:** automatic cross-subnet identity linking and MAC-randomization detection were deliberately out of scope. Do not silently add either to the next milestone.

## Suggested next milestone — recommendation, not an approved plan

Improve recognition and editable profiles: retain discovered manufacturer/model, support durable user corrections, expand evidence-based signatures, and validate against a known collection of phones/computers/TVs/IoT devices. Propose a bounded scope and acceptance cases first. No active GitHub milestone currently tracks this; old roadmap material is scattered and partly stale.

## Starting files and evidence

- `core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan/engine/DeviceFingerprinter.kt` — classification rules, fixed scores, `PortFingerprint`.
- `core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan/DeviceInventoryRepository.kt` — identity matching, persistence and confidence merge.
- `core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan/model/LanDevice.kt`; `core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/model/KnownDeviceEntity.kt`; corresponding `dao/KnownDeviceDao.kt`.
- `feature/lanscan/src/main/kotlin/com/ventouxlabs/netlens/feature/lanscan/LanScanViewModel.kt` — SSDP construction around 393–409; port enrichment around 703–711. `engine/FingerprintMerge.kt` and `HostDetailSheet.kt` are related.
- `feature/devices/src/main/kotlin/com/ventouxlabs/netlens/feature/devices/` — ViewModel, detail UI, watch runner and scheduler; tests cover tagging, export and synchronization.
- `CHANGELOG.md`; `docs/CODEMAPS/data.md`; `docs/superpowers/specs/2026-07-17-device-inventory-design.md`.
- `docs/archive/session-handoff-2026-09-06.md`: first section says released; later historical uncommitted/unreleased sections are stale. Its cross-network exclusion and confidence deferral were checked against current code.
- Fing reference: https://app.fing.com/business/devrecog — richer brand/model recognition is the relevant comparison, not an approved dependency or integration.

## Verification and environment

This device-roadmap investigation was read-only; no code changed or new tests ran. Findings were independently inspected in separate inventory and fingerprinting review lanes. Local checkout includes the closed widget changes; latest master differs mainly by the completed test/dependency PRs described above.

For future tests use JDK 17 and the repository's Gradle conventions. Full unit suite: `./gradlew testFossDebugUnitTest testGplayDebugUnitTest testDebugUnitTest`. Run relevant tests first when code changes justify them.

Physical Pixel was disconnected at last ADB check. Disposable emulator attempts crashed before boot; no device acceptance evidence was produced. Do not claim live-network identification accuracy from source inspection. Shell commands sometimes print a sysroot/system-bus warning while returning successfully; GitHub CLI network calls required sandbox escalation.
