# Google Play — `com.ventouxlabs.netlens`

NetLens was renamed from `com.ventoux.netlens` to `com.ventouxlabs.netlens` in
v1.1.3. Google Play **cannot change an existing app's applicationId**, so the
renamed build is a **brand-new app/listing**. The old `com.ventoux.netlens`
listing keeps its reviews and install base and will not receive the renamed
build — existing users must install the new app fresh.

This doc has two parts:
1. Ready-to-paste **store listing copy** for creating the new app.
2. The **fastlane supply** automation set up in this repo for future releases.

---

## 1. New listing copy (paste into Play Console)

The canonical source for all of this is `fastlane/metadata/android/en-US/` — the
text below is a snapshot. Keep them in sync.

- **App name** (`title.txt`): `NetLens`
- **Short description** (`short_description.txt`, max 80 chars):
  > Network diagnostics toolkit: ping, traceroute, DNS, LAN scan, port scan & more
- **Full description** (`full_description.txt`): see the file — feature-grouped,
  no package references, ready as-is.
- **What's new**: use the changelog for the versionCode you are uploading —
  `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. At bootstrap time
  that is the latest release's file, not `6.txt` (which was the v1.1.3 migration note
  this doc originally shipped with). `supply` reads the versionCode from the AAB and
  picks the matching file automatically.

**Assets** (from `fastlane/metadata/android/en-US/images/`):
- App icon — `icon.png` (Play requires 512×512 PNG; verify dimensions)
- Feature graphic — `featureGraphic.png` (1024×500)
- Phone screenshots — `phoneScreenshots/*.png` (min 2, max 8)

**Console fields not stored in the repo** (fill manually):
- Category, tags, contact email/website, privacy policy URL
- Content rating questionnaire
- Data safety form
- Countries / pricing (free)

### Old listing (`com.ventoux.netlens`)
You can still edit the old app. Recommended: add a line to its description
pointing users to the renamed app, and/or unpublish once the new app is live.

---

## 2. fastlane supply (future releases)

> ⚠️ **Bootstrap limitation:** `supply` (the Play Developer API) **cannot create
> a new app, and cannot perform the first-ever upload** for a package. You must
> manually create `com.ventouxlabs.netlens` in the Play Console and upload one
> AAB (e.g. `app-gplay-release.aab` from the v1.1.3 GitHub release) by hand. Only
> *after* that can the automation below take over.

### Files in this repo
- `Gemfile` — declares `fastlane`. Run `bundle install` (commit `Gemfile.lock`).
- `fastlane/Appfile` — `package_name` + service-account key path (via
  `PLAY_SERVICE_ACCOUNT_JSON`).
- `fastlane/Fastfile` — two lanes:
  - `deploy` — upload the signed gplay AAB + listing to a track.
    Options: `track:` (internal/alpha/beta/production), `release_status:`
    (draft/completed), `aab:`.
  - `listing` — upload only the store listing (no binary).
- `.github/workflows/play-publish.yml` — `workflow_dispatch` that builds the
  signed AAB and runs `fastlane deploy`.

### One-time service-account setup

> 🚧 **Status (re-verified 2026-08-06): STILL NOT done — nothing has moved since
> 2026-06-29.** Checked directly rather than inferred:
>
> ```
> gh secret list             → RELEASE_KEYSTORE_BASE64, RELEASE_KEY_ALIAS,
>                              RELEASE_KEY_PASSWORD, RELEASE_STORE_PASSWORD
>                              PLAY_SERVICE_ACCOUNT_JSON  ← absent
> Play Publish runs, ever    → 1 (2026-06-29, failure)
> ```
>
> So the **Play Publish** workflow still hard-fails at "Write Play service-account key".
> The four `RELEASE_*` signing secrets **are** set, so the signed-AAB build half does run —
> that half is proven. The bootstrap manual upload (app creation + first AAB) is still
> **unverified** and must happen before any automation can succeed.
>
> **All remaining blockers are human work in Google's web consoles.** No amount of repo
> change unblocks this; see the go-live checklist below.
>
> Listing inputs were verified ready on 2026-08-06: `title.txt`, `short_description.txt`
> (78/80 chars), `full_description.txt` (1439/4000), `icon.png` 512×512,
> `featureGraphic.png` 1024×500, and four 1080×2160 phone screenshots. The data-safety
> answers below are pre-written, including the paste-ready IP-address justification.

1. Google Cloud Console → create a **service account**; create a JSON key.
2. Play Console → **Users & permissions** → invite the service-account email →
   grant release permissions for this app.
3. Add the JSON key as the **`PLAY_SERVICE_ACCOUNT_JSON`** GitHub repository
   secret (paste the whole file contents).

### Run it
- **Local:**
  ```bash
  bundle install
  PLAY_SERVICE_ACCOUNT_JSON=/path/to/key.json \
    bundle exec fastlane deploy track:internal release_status:draft
  ```
- **CI:** Actions → **Play Publish** → Run workflow → pick track + release status.

### Go-live checklist (remaining manual steps)

The signed-AAB build half of the pipeline is already proven — workflow run
[28410296295](https://github.com/bearyjd/netlens-android/actions/runs/28410296295)
built the signed `app-gplay-release.aab` and failed only at the service-account
step. Completing the boxes below unblocks a fully-green **Play Publish** run.
All of these are done in Google consoles / GitHub settings, not in code.

**A. Bootstrap the listing** — Google's API cannot create the app or perform the
first upload for `com.ventouxlabs.netlens`:
- [ ] Play Console → **Create app** → name `NetLens`, package `com.ventouxlabs.netlens`
- [ ] Fill console-only fields: category, contact email, **privacy policy URL**
      (`https://github.com/bearyjd/netlens-android/blob/master/docs/PRIVACY_POLICY.md`
      — see `docs/PRIVACY_POLICY.md`), content rating questionnaire, data safety
      form (see "Data safety form answers" below), countries/pricing (free)
- [ ] Manually upload one AAB — `app-gplay-release.aab` from the
      [latest release](https://github.com/bearyjd/netlens-android/releases/latest)
      (listing copy + assets are ready in `fastlane/metadata/android/en-US/`; use the
      latest release at bootstrap time so the listing launches with current icon/UI,
      not whatever version this doc last mentioned)

### Capturing phone screenshots (proven 2026-08-06)

The shipped set is **1080x2160 (2:1), RGB with no alpha** — Play rejects alpha, and the
existing files set the ratio convention. Working recipe on the Pixel 9 Pro Fold:

```bash
D=4A111FDKD0000C; OUT=4619827677550801153   # OUTER display; the inner one captures black while folded
adb -s $D shell am start -a android.intent.action.VIEW -d "netlens://feature/<route>" -p com.ventouxlabs.netlens
sleep 2   # fire it TWICE - the first lands before nav is ready
adb -s $D shell am start -a android.intent.action.VIEW -d "netlens://feature/<route>" -p com.ventouxlabs.netlens
adb -s $D shell screencap -d $OUT -p > raw.png
```

Then crop with Pillow: `Image.open('raw.png').convert('RGB').crop((0,130,1080,130+2160))` —
`convert('RGB')` drops the alpha, and dropping the top 130px removes the status bar and lands
exactly on 2:1 with no padding.

Four things that cost time, so they are written down:

- **`screencap` needs an explicit `-d <displayId>`** on a foldable or it writes a warning into
  the PNG and corrupts it.
- **Crop the status bar; do not try to clean it.** SysUI demo mode
  (`settings put global sysui_demo_allowed 1`, then the `com.android.systemui.demo` broadcasts)
  does fix the clock, carrier, roaming and battery — but **persistent/ongoing notification icons
  survive it**, and those reveal which apps the device owner uses. Cropping removes the whole
  class of leak. Remember to `exit` demo mode and reset `sysui_demo_allowed` to 0 afterwards.
- **Not every `ToolDestination` route is a deep link.** `lanscan` and `devices` resolve;
  **`wifi` silently lands on Home**. Verify by hashing the crop — two screens that hash
  identically means the route did not resolve, and a wrong screenshot is easy to miss by eye.
- **Populate the device first.** A wiped phone yields empty forms. The current `4_ping.png`
  shows a real ping to `8.8.8.8` with latency bars and summary stats; an empty Ping form is a
  strictly worse listing image. **Before capturing, set custom names on inventoried devices**
  (`Living Room TV`, `Office NAS`): the tagging feature hides real hostnames *and* makes the
  screenshots look curated rather than accidental.

**What not to show:** the Wi-Fi analyzer channel graph lists *neighbours'* SSIDs, which are
often surnames or flat numbers — third-party data you cannot rename. Use the Coverage tab
instead; it shows your own APs by short name, and full BSSIDs never leave the device by design.

### Data safety form answers

Derived from a permissions/dependency audit (no analytics, crash reporting, or
ads SDKs anywhere in the dependency graph) and the F-Droid recipe's
`NonFreeNet` disclosure — see `docs/PRIVACY_POLICY.md` for the full reasoning.

Master toggle: **"Yes, collects some data"** (not "No collection") — see the
IP address item below for why a blanket "No" would understate what actually
happens on the wire.

| Category | Answer | Why |
|---|---|---|
| Location | Not collected | `ACCESS_FINE_LOCATION` is only requested because Android gates reading Wi-Fi SSID/BSSID (`feature/wifi`) and cell tower info (`feature/celltower`) behind it. Displayed on-device only, never transmitted. |
| Personal info | Not collected | No accounts, no forms |
| Financial info (purchase history) | Not collected *(defensible middle ground: declare "collected, not shared, purpose: app functionality" if you want zero ambiguity)* | gplay Pro entitlement is a local boolean flag in `EncryptedSharedPreferences`; Google Play Billing itself handles all payment processing under Google's own terms |
| Health & fitness / Messages / Photos-videos-audio / Files-docs / Calendar / Contacts | Not collected | App doesn't touch any of these |
| App activity | Not collected | Scan/history data (`core:data` Room DB) never leaves the device |
| Web browsing history | Not collected | HTTP Tester sends only requests you compose; doesn't log external browsing |
| App info & performance | Not collected | Confirmed no crash/analytics SDK in `libs.versions.toml` or anywhere in the dependency graph |
| **Device or other IDs (IP address)** | **Collected, not shared for advertising, purpose: App functionality, user-initiated only** | IP Info, Traceroute, IP reputation lookup, and Speed Test send direct HTTP requests to `ipinfo.io`, `ipwho.is`, `api.abuseipdb.com`, `speed.cloudflare.com` — any HTTP request inherently exposes the device's IP to that server. Mirrors the F-Droid `NonFreeNet` anti-feature disclosure exactly; keeping both listings consistent. |

Free-text justification for the IP-address item (paste as-is): *"IP address
is exposed as an inherent side effect of user-initiated HTTP requests to
third-party diagnostic services (IP lookup, traceroute geolocation, IP
reputation check, speed test). No IP address or other data is collected,
stored, or shared by the app's developer — requests go directly from the
user's device to the third-party service. Not used for advertising or
tracking."*

**B. Service account + the missing secret:**
- [ ] Google Cloud Console → create a **service account** → create a **JSON key**
- [ ] Play Console → **Users & permissions** → invite the service-account email →
      grant **release** permissions for this app
- [ ] GitHub → repo **Settings → Secrets and variables → Actions** → add
      **`PLAY_SERVICE_ACCOUNT_JSON`** = the entire JSON key contents
      (the four `RELEASE_*` signing secrets are already set)

**C. First automated upload:**
- [ ] Actions → **Play Publish** → Run workflow → `track: internal`,
      `release_status: draft`
- [ ] Confirm the run is fully green and a draft appears on the internal track

### Notes
- The workflow reuses the existing release-signing secrets
  (`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
  `RELEASE_KEY_PASSWORD`) to build the signed AAB.
- `supply` reads the versionCode from the AAB; bump `gradle.properties` as usual.
- `ruby/setup-ruby` is pinned to `95ef2b04` (v1.321.0) as of 2026-08-06, matching
  the SHA-pinning convention used by every other action in this repo.
