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
- **What's new** (`changelogs/6.txt`, versionCode 6): the v1.1.3 migration note.

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

> 🚧 **Status (as of 2026-06-29): NOT yet done.** The `PLAY_SERVICE_ACCOUNT_JSON`
> repository secret is **not set**, so the **Play Publish** workflow will hard-fail
> at the "Write Play service-account key" step until the three steps below are
> completed. The signing secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
> `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) **are** set, so the signed-AAB build
> portion of the workflow does run. The bootstrap manual upload (app creation + first
> AAB) has **not been verified** and must also be done before automation can succeed.

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
- [ ] Fill console-only fields: category, contact email, **privacy policy URL**,
      content rating questionnaire, data safety form, countries/pricing (free)
- [ ] Manually upload one AAB — `app-gplay-release.aab` from the
      [v1.2.4 release](https://github.com/bearyjd/netlens-android/releases/tag/v1.2.4)
      (listing copy + assets are ready in `fastlane/metadata/android/en-US/`; use the
      latest release at bootstrap time so the listing launches with current icon/UI,
      not whatever version this doc last mentioned)

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
- `ruby/setup-ruby` in the workflow is not yet pinned to a commit SHA (see the
  TODO) — pin it to match the rest of the workflows before relying on it.
