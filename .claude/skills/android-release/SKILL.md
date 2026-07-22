---
name: android-release
description: NetLens release flow with pre-tag verification. Refuses to tag if CHANGELOG entry missing, F-Droid changelog file missing, signed local build fails, or the tag already exists at a different SHA. Use this skill when shipping a new versionName/versionCode.
---

# /android-release

Locks in the release ritual the hard way — the v1.1.0 → v1.1.1 incident is what this skill exists to prevent.

## When to use

The user wants to ship a new release: "tag and release", "ship v1.2.0", "cut a release", "release the hardening". Do NOT use for hotfixes that don't bump the version.

## Inputs the skill expects

- The intended `versionName` (e.g. `1.1.2`) and `versionCode` (e.g. `5`). If the user didn't provide them, ask: "Bump to which version? (current: <read from gradle.properties>)".
- Optional: a one-paragraph release summary for the tag annotation. If not provided, derive from the matching CHANGELOG block.

## Hard rules

- **Refuse to tag** if any pre-flight check fails. Print which check failed and stop. Don't auto-fix; tell the user what to do.
- **Never force-push or force-tag.** If `v<version>` already exists on origin (local or remote), STOP and surface it. The user must decide whether to bump again or force-overwrite (force-overwrite is a separate, explicit decision).
- **Never use `git add -A`.** Stage specific files only.
- **Never skip the local signed build.** CI will sign too, but a local pre-flight catches the "unsigned APK" footgun (see `app/build.gradle.kts:25-42` and the env-var fallback) before it costs a publish cycle.

## Workflow

### 1. Pre-flight (refuse on failure)

Run all of these and report a green/red per check before doing anything mutating:

1. **Branch + clean tree.** `git status` shows clean tree, on `master`, in sync with `origin/master`. Refuse if dirty or behind.
2. **Version sanity.**
   - Read `netlens.versionName` and `netlens.versionCode` from `gradle.properties`.
   - These must match the intended release version. If they don't, ask the user whether to bump them as part of this release flow (yes → continue; no → refuse).
3. **CHANGELOG entry exists.** `CHANGELOG.md` must contain a `## [<versionName>] - <date>` block (NOT just under `## [Unreleased]`). Refuse if missing.
4. **F-Droid changelog file exists.** `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` must exist and be non-empty. Refuse if missing.
5. **Tag does not already exist.**
   - `git rev-parse v<versionName>` should fail (tag absent locally).
   - `git ls-remote origin refs/tags/v<versionName>` should return empty (tag absent on origin).
   - If either exists, STOP. Surface where the tag points and what the current `HEAD` is. The user has to decide explicitly.
6. **Local signed release builds.**
   - Verify `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` are set in the env. If `local.properties` lacks `release.*` and env vars are unset, refuse with a one-line note pointing at `app/build.gradle.kts:25-42`.
   - Run `./gradlew clean assembleRelease bundleRelease`. Refuse on failure.
   - Confirm artifacts: `app/build/outputs/apk/foss/release/app-foss-release.apk`, `app/build/outputs/apk/gplay/release/app-gplay-release.apk`, `app/build/outputs/bundle/fossRelease/app-foss-release.aab`, `app/build/outputs/bundle/gplayRelease/app-gplay-release.aab` — all four exist, none ends in `-unsigned.apk`.
7. **Baseline profile freshness (warn, don't refuse).** Check the last commit touching `app/src/main/generated/baselineProfiles/` (`git log -1 --format=%cs -- app/src/main/generated/baselineProfiles/`). If it's more than one release old (predates the previous release tag), tell the user: "Baseline profile is stale — consider re-dispatching `baseline-profile.yml` from a branch and merging before tagging." Stale profiles degrade gracefully (unmatched rules are ignored), so this warns rather than blocks.
8. **Cert continuity.**
   - Run `apksigner verify --print-certs app/build/outputs/apk/gplay/release/app-gplay-release.apk` and capture the `Signer #1 certificate SHA-256 digest`.
   - Print the SHA-256 to the user with: "New cert SHA-256: `<digest>`. Confirm this matches the cert that signed the previous release before continuing." Wait for explicit user OK. Cert mismatch means the user is signing with a different keystore than the published release — that breaks in-place updates for every existing user.

### 2. Bump-commit (only if version sanity step required a bump)

Stage `gradle.properties`, `CHANGELOG.md`, and the new `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (and only those). Commit with:

```
chore: bump version to <versionName> for release

See CHANGELOG.md [<versionName>] for the full notes.
```

Push directly to `master` (matches existing pattern: `7825ab9`, `5360d1f`).

### 3. Tag

Create an annotated tag using the matching CHANGELOG block as the message body:

```
git tag -a v<versionName> -m "$(cat <<'EOF'
v<versionName> — <one-line summary>

<copy the body of the matching CHANGELOG.md [<versionName>] block here>
EOF
)"
```

Then `git push origin v<versionName>`.

### 4. Surface CI

`gh run list --workflow=release.yml --limit 1 --json databaseId,status,url` and report the run URL to the user. Optionally start a background poll that emits one notification when the workflow finishes. When it does:
- Print final status / conclusion.
- Print `gh release view v<versionName> --json name,tagName,publishedAt,assets` for the artifact list.

### 5. Done

Output the published release URL and remind the user:
- F-Droid auto-tracker will pick up the tag on its next sync (typically 24–48h).
- The new APK on a connected device requires `adb install -r` (no uninstall) ONLY if the cert SHA-256 matches the prior release. If it doesn't, the user will need to uninstall first — losing app data. The cert continuity check in step 1.7 surfaces this in advance.

## What this skill explicitly does NOT do

- Does not bump versions on its own beyond what the user asked for. No "auto-determine semver" magic.
- Does not edit CHANGELOG.md. The user owns the narrative; this skill verifies it's present.
- Does not roll back a bad release. If something breaks post-tag, that's a separate `chore: revert vX.Y.Z` flow, not this skill.
- Does not push to F-Droid directly. F-Droid syncs from the GitHub tag automatically.

## Failure recovery

If a pre-flight check fails, exit with status `BLOCKED` and a single sentence on what to fix. Common cases:

| Failure | Fix |
|---|---|
| CHANGELOG entry missing | Add `## [<versionName>] - <ISO date>` block; rerun. |
| F-Droid changelog missing | Create `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`; rerun. |
| Tag already exists at a different SHA | This is the v1.1.0 mistake. Bump to next version (`v<X>.<Y>.<Z+1>`). |
| Cert SHA-256 differs from prior release | Wrong keystore in env. Source the right one before retrying. |
| Unsigned APK produced | `local.properties` shadowing env vars. Either delete `local.properties.release.*` keys or move `local.properties` aside for the build. |
