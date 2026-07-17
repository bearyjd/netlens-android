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

**First-time submission is in flight** (as of 2026-07-16): [fdroiddata MR #42628](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/42628) is open and CI-green (`fdroid build` passes on the real buildserver image), awaiting maintainer merge. The staging branch is `add-com.ventouxlabs.netlens` on the `selector4560/fdroiddata` fork. See `docs/HANDOFF.md` for the submission history. `fdroid/com.ventouxlabs.netlens.yml` in this repo is an informational copy — the recipe F-Droid actually builds from lives in fdroiddata. Once the MR merges, F-Droid's build server takes over; allow 1-2 weeks for the first build to appear, then switch to the routine-update flow below.

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
