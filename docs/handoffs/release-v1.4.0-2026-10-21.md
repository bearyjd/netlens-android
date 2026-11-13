# Handoff: v1.4.0 release and device-inventory work

Date: 2026-10-21. Repo: `/var/home/user/Documents/vibe-code/netlens-android`. Supersedes the open items of [device-identification-2026-10-19](device-identification-2026-10-19.md).

## State (verify before relying on it)

- `master` and tag `v1.4.0` both point at `36fddc7` (`chore: bump version to 1.4.0 for release`), pushed. versionName `1.4.0`, versionCode `23`. `fastlane/.../changelogs/23.txt` and the `CHANGELOG.md` `[1.4.0] - 2026-10-21` block are in.
- **Release workflow completed successfully.** Run 39711349392 (`release.yml`, tag `v1.4.0`): `release` job succeeded at 03:07Z (GitHub release `v1.4.0` published, no binaries attached by design) and `publish-production` (`play-publish.yml`, track `production`, `release_status: completed`) succeeded at 03:47Z. Both jobs waited a long time for a runner (about 40 min, then about 35 min), which looked like a stuck run but was queueing, not a failure. Not verified by me: that the Play Console shows 1.4.0 (code 23) as live; check it there. The run only reports that the publish job passed.
- Local checkout: clean, on `master`.

## Shipped in 1.4.0 (all merged via PR)

- #186 SSDP model kept in the inventory, plus user overrides of device type/OS/model (Room v18, additive migration). #187 separate type/OS confidence in the host-detail port scan. #188 device-supplied text flattened in LAN Scan/Inventory UI. #189 host detail lists only open ports with one summary line. #192 Inventory tab groups unidentified devices under one collapsed row.
- Device-checked on a Pixel 10 Pro Fold: confidence lines, open-ports list, inventory grouping, override save/clear and rescan survival. Not checked: TalkBack announcement of the group row; an SSDP-reported model (no device on the test network sent one; unit tests only).

## Release facts

- Upload-key certificate SHA-256 (user confirmed before tagging): `8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4`, CN=Ventoux Advisory Co. The phone's Play-installed app is signed by Play's own key, so it cannot be compared with this.
- F-Droid: `https://f-droid.org/api/v1/packages/com.ventouxlabs.netlens` returned 404, so the app is not listed and this tag reaches no F-Droid user.
- Baseline profile is stale (last updated 2026-09-06, before v1.3.8); the release skill only warns.

## Findings not acted on

- No MAC addresses: Android denies reading the ARP table, so every inventory row is keyed by IP and shows "MAC unknown". Identity relies on mDNS/SSDP names.
- After one real scan, 30 of 35 inventory rows had no name, vendor or type. #192 only hides them behind a collapsed row; stale rows are never aged out and rescans add rows for new IPs.
- Port enrichment still does not reach the persistent inventory; `LanDevice` has one confidence shared by type and OS.
- Still deferred and unscoped: broader signature catalogue, per-device alert muting, alert-history screen. Cross-subnet linking and MAC-randomization detection stay out of scope by prior decision.
- `docs/HANDOFF.md` still calls the widget 4x2 handoff "the active task"; widget work was closed by the user.

## Environment notes

- `~/.gitconfig` points the GitHub credential helper at `/home/linuxbrew/.linuxbrew/bin/gh`, which does not exist. Plain `git push/fetch/pull` over HTTPS fails; use `git -c 'credential.https://github.com.helper=' -c 'credential.https://github.com.helper=!/usr/bin/gh auth git-credential' <cmd>`.
- Local builds ran on JDK 21 (CI uses 17). Full gate: `./gradlew testFossDebugUnitTest testGplayDebugUnitTest testDebugUnitTest assembleFossDebug assembleGplayDebug`.
- Device: serial `57211FDCG0023C`. It has two displays; `adb exec-out screencap` needs `-d 4619827677550801152` (inner) or `-d 4619827677550801153` (outer). Debug builds install as `com.ventouxlabs.netlens.debug` and never touch the release package, which still has 1.3.8 from Play. The debug app holds about 40 test inventory rows; its data is local to that package.
