## New app: NetLens (com.ventouxlabs.netlens)

NetLens is a network diagnostics toolkit for Android — ping, traceroute, DNS lookup, LAN/port scanning, WHOIS, TLS inspection, HTTP testing, mDNS browsing, Wi-Fi analysis, Wake-on-LAN, IP info, subnet calculator, endpoint uptime monitoring, connection log, speed test, Wi-Fi/security posture audit, cell tower info, and DNS leak testing. It is **not** a VPN and does not claim to provide network protection — the "posture"/"security" language in the app refers to passive diagnostics (e.g. detecting whether a VPN is active, checking Wi-Fi encryption), not active protection.

- Source: https://github.com/bearyjd/netlens-android
- License: AGPL-3.0-only
- Latest tag: `v1.2.1` (versionCode 8)

### Application ID history (please read before asking why `Builds:` starts at 1.2.1)

The app has changed its Android application ID twice as it moved between publishers:

| App ID | Tags |
|---|---|
| `us.beary.netlens` | pre-`v1.0.0` (never tagged/released under this ID) |
| `com.ventoux.netlens` | `v1.0.0` – `v1.1.2` |
| `com.ventouxlabs.netlens` (current) | `v1.1.3` – present |

Each rename is a genuinely different Android package as far as installs/updates are concerned — F-Droid, Play, and existing devices all treat them as separate apps. This recipe is filed under the current ID (`com.ventouxlabs.netlens`). The two earlier IDs were never submitted to F-Droid (verified — no listing exists for any of the three IDs at time of writing), so there's no prior recipe to merge history with, and no existing F-Droid users to strand by not backfilling older versions.

`Builds:` starts at `v1.2.1`, not `v1.1.3` (the first tag under this application ID): `v1.1.3` and `v1.2.0` fail F-Droid's build — the release build type looked up its signing config with `signingConfigs.getByName("release")`, which throws once F-Droid's reproducible-build prep strips the `signingConfigs` block entirely (`del_files`/`build.gradle.kts` cleaning). Fixed in `v1.2.1` by switching to `findByName` (null-safe). Confirmed via a local `fdroid build` run: the pre-fix source failed at exactly this step; post-fix, F-Droid's stripped `build.gradle.kts` builds cleanly through the `clean` task.

### Anti-features

`NonFreeNet` — the app makes opt-in, user-initiated HTTPS calls to a small set of third-party services, never automatically:
- `ipinfo.io` — IP Info screen, and the home-screen widget's public-IP display (gated behind a separate consent toggle in Settings, off by default for the widget)
- `ipwho.is` — Traceroute hop geolocation
- `api.abuseipdb.com` — IP reputation lookup, requires the user to supply their own API key
- `speed.cloudflare.com` — Speed Test tool

All other tools (ping, DNS, port scan, WHOIS, etc.) either operate on user-supplied hosts directly or talk to the local network only.

### Build

- Single module of interest: `:app`, `subdir: app`
- Two product flavors on the `distribution` dimension: `foss` (no billing dependency, Pro tier always unlocked) and `gplay` (Google Play Billing). This recipe builds only `foss`.
- No proprietary SDKs, no Google Play Services, no Firebase, no ads. Full FOSS dependency audit is in `.claude/PRPs/plans/completed/fdroid-packaging.plan.md` in the source repo if useful context.

### Local verification performed

- `fdroid lint com.ventouxlabs.netlens` — passes clean.
- `fdroid build com.ventouxlabs.netlens:8` (v1.2.1) — verified as far as a bare `pip install fdroidserver` can go:
  - Recipe resolves and clones `v1.2.1` correctly.
  - F-Droid's reproducibility prep applies cleanly (debuggable-flag strip, `signingConfigs` strip).
  - The stripped `app/build.gradle.kts` builds successfully (`clean` task) — confirming the `getByName`→`findByName` fix actually resolves the failure mode above, not just in theory.
  - Source scanner passes clean (no prebuilt binaries in the tracked tree).
  - Could not complete the full `assembleFossRelease` step locally: `fdroidserver.build` deliberately deletes `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` from the checkout so only F-Droid's own vetted `gradlew-fdroid` launcher ever executes in the build sandbox (by design — a compromised developer-committed wrapper script must never run). `gradlew-fdroid` isn't distributed via PyPI; it ships with F-Droid's buildserver image. This is an architectural property of local pip-based testing for *any* Gradle Android app, not something specific to this recipe.

### Checklist

- [x] `fdroid lint com.ventouxlabs.netlens` passes
- [x] `fdroid build` verified as far as possible without F-Droid's buildserver image (see above) — the actual `signingConfigs` failure mode is confirmed fixed
- [ ] Full build (`assembleFossRelease` end-to-end) — will only be confirmed by F-Droid's own build server; happy to iterate if it surfaces anything the local check couldn't reach
- [ ] Screenshots — `fastlane/metadata/android/en-US/images/phoneScreenshots/` has 4 screenshots + feature graphic + icons, but they predate the `v1.2.0`/`v1.2.1` UI redesign (new theme, dashboard, settings screen). Will refresh before/shortly after merge.
