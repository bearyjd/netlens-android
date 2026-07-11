# Releasing NetLens

## Prerequisites

- Release keystore configured in `local.properties` or CI environment variables
- `gh` CLI authenticated for GitHub Releases
- Google Play Console access (for Play Store)

## Version Bump

Use the GitHub Actions workflow **or** bump manually:

### Automated (recommended)
1. Go to Actions > "Bump Version & Release" > Run workflow
2. Select bump type: `patch`, `minor`, or `major`
3. The workflow updates `gradle.properties`, commits, tags, and pushes
4. The `v*` tag triggers the Release workflow automatically

### Manual
```bash
# Edit gradle.properties
netlens.versionName=1.1.0
netlens.versionCode=2

# Commit and tag
git add gradle.properties
git commit -m "chore: bump version to 1.1.0"
git tag v1.1.0
git push && git push --tags
```

## Channel Checklist

### GitHub Releases

Fully automated via CI:
1. Push a `v*` tag (or use the bump workflow)
2. `.github/workflows/release.yml` builds the release APK
3. APK is uploaded to GitHub Releases with auto-generated notes
4. Edit the release on GitHub to add highlights if needed

### F-Droid

**NetLens has not yet been submitted to F-Droid.** `fdroid/com.ventouxlabs.netlens.yml` in this repo is a local draft only — F-Droid does not watch this repository or its tags; nothing is published until someone opens a merge request against the separate [fdroiddata](https://gitlab.com/fdroid/fdroiddata) repo and it's reviewed. This has never been done (confirmed 2026-07-11: no `com.ventouxlabs.netlens`, `com.ventoux.netlens`, or `us.beary.netlens` recipe exists in fdroiddata, and no listing exists on f-droid.org under any of the app's three historical package IDs).

First-time submission:
1. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata) on GitLab
2. Copy `fdroid/com.ventouxlabs.netlens.yml` from this repo to `metadata/com.ventouxlabs.netlens.yml` in the fork
3. Optionally run `fdroid lint com.ventouxlabs.netlens` and `fdroid build com.ventouxlabs.netlens` locally (via `fdroidserver`) to catch build/recipe issues before submitting
4. Open a merge request against fdroiddata; expect review iteration (the note in the recipe about starting the `Builds:` list at 1.1.3, since the app renamed its application ID at that version, is the kind of thing reviewers will likely ask about)
5. Once merged, F-Droid's build server takes over — allow 1-2 weeks for the first build to appear

Routine updates (once the recipe is merged and the app has a real F-Droid listing):
1. Ensure the `v*` tag exists on GitHub (F-Droid's `UpdateCheckMode: Tags ...` picks it up automatically — this repo's `fdroid/` copy does NOT need to change for F-Droid itself to notice)
2. Update `fdroid/com.ventouxlabs.netlens.yml` in this repo to stay in sync (informational — the copy F-Droid actually builds from lives in fdroiddata):
   - Add a new entry under `Builds:` with the new versionName/versionCode
   - Update `CurrentVersion` and `CurrentVersionCode`
3. Submit a pull request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) with the same change
4. F-Droid maintainers review and build — typically 1-2 weeks

### Google Play Store

1. Build a signed release APK locally:
   ```bash
   ./gradlew assembleRelease
   ```
2. Locate the APK at `app/build/outputs/apk/release/app-release.apk`
3. Upload to Play Console > Production > Create new release
4. Update "What's new" text from `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
5. Submit for review (typically 5-24 hours)

**Required Play Store assets** (one-time setup):
- App icon: 512x512 PNG
- Feature graphic: 1024x500 PNG
- Screenshots: 2-8 phone screenshots (1080x1920)
- Privacy policy URL pointing to `PRIVACY.md` on GitHub or hosted page
- Content rating questionnaire completed in Play Console

## Post-Release

1. Update `CHANGELOG.md` with the new version section
2. Create the fastlane changelog file: `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
3. Verify the GitHub Release page has the APK attached
4. Verify F-Droid build status after submission (check the merge request)
