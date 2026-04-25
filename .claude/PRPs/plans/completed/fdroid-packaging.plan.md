# Plan: F-Droid Packaging

## Summary
Prepare NetLens for F-Droid inclusion: create Fastlane-compatible metadata, ensure reproducible builds, verify FOSS compliance of all dependencies, document anti-features, and draft the fdroiddata YAML recipe.

## User Story
As an open-source user, I want NetLens available on F-Droid, so that I can install it from a trusted FOSS app store without Google Play.

## Problem → Solution
No F-Droid metadata, no reproducibility config, no FOSS audit → Ready for fdroiddata submission.

## Metadata
- **Complexity**: Medium
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: ~10

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `gradle/libs.versions.toml` | all | Dependency audit for FOSS compliance |
| P0 | `app/build.gradle.kts` | all | Build config for reproducibility |
| P1 | `app/src/main/res/xml/network_security_config.xml` | all | Cleartext exception = anti-feature |
| P1 | `LICENSE` | 1-10 | AGPL-3.0 confirmation |
| P2 | `gradle.properties` | all | Build flags |

---

## FOSS Compliance Audit

All dependencies verified FOSS-compatible:
- Compose BOM, Hilt/Dagger, Room, Ktor, Glance, WorkManager, Navigation — Apache 2.0
- kotlinx-coroutines, kotlinx-serialization, DataStore — Apache 2.0
- dnsjava — BSD 3-Clause
- Material Icons Extended — Apache 2.0
- JUnit 5 — EPL 2.0 (test only)
- Turbine — Apache 2.0 (test only)

**No proprietary SDKs detected.** No Google Play Services, Firebase, or ads.

## Anti-Features
- `NonFreeNet` — connects to `ip-api.com` over cleartext HTTP for IP geolocation. Documented in network_security_config.xml cleartext exception.

---

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `fastlane/metadata/android/en-US/title.txt` | CREATE | "NetLens" |
| `fastlane/metadata/android/en-US/short_description.txt` | CREATE | 80-char summary |
| `fastlane/metadata/android/en-US/full_description.txt` | CREATE | Full listing |
| `fastlane/metadata/android/en-US/changelogs/1.txt` | CREATE | v1 changelog |
| `fastlane/metadata/android/en-US/images/phoneScreenshots/.gitkeep` | CREATE | Placeholder |
| `fdroid/com.ventoux.netlens.yml` | CREATE | fdroiddata recipe |
| `app/build.gradle.kts` | UPDATE | Reproducible build config |
| `gradle.properties` | UPDATE | Reproducibility flags |

## NOT Building
- Actual F-Droid submission (manual process)
- Screenshot generation (separate task)
- Migration from ip-api.com to HTTPS provider

---

## Step-by-Step Tasks

### Task 1: Create Fastlane Metadata
- **ACTION**: Create `fastlane/metadata/android/en-US/` with title.txt, short_description.txt, full_description.txt, changelogs/1.txt
- **IMPLEMENT**: title <= 30 chars, short_description <= 80 chars, full_description <= 4000 chars, changelog <= 500 chars
- **VALIDATE**: Character limits respected

### Task 2: Configure Reproducible Builds
- **ACTION**: Add packaging excludes to app/build.gradle.kts, verify build flag pins
- **IMPLEMENT**: `packaging { resources { excludes += setOf("META-INF/*.kotlin_module") } }`
- **GOTCHA**: R8 can introduce non-determinism; F-Droid builds with their own signing
- **VALIDATE**: Two consecutive `assembleRelease` produce identical APKs

### Task 3: Draft fdroiddata Recipe
- **ACTION**: Create `fdroid/com.ventoux.netlens.yml`
- **IMPLEMENT**: Categories, License, Repo, Builds section, AntiFeatures: [NonFreeNet], AutoUpdateMode
- **GOTCHA**: Recipe may need iteration after fdroiddata review
- **VALIDATE**: `fdroid lint` passes (if fdroidserver available)

### Task 4: Add Build Reproducibility Flags
- **ACTION**: Add `android.enableSourceSetPathsMap=true` to gradle.properties if missing
- **VALIDATE**: Property present in gradle.properties

---

## Validation Commands

```bash
# Build verification
./gradlew assembleDebug

# FOSS audit — no proprietary deps
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep -i "play-services\|firebase\|ads"
# EXPECT: no output

# Metadata exists
test -f fastlane/metadata/android/en-US/title.txt && echo "OK"

# No proprietary code
grep -r "com.google.android.gms\|com.google.firebase" --include="*.kt" --include="*.xml" app/ feature/
# EXPECT: no output
```

## Acceptance Criteria
- [ ] Fastlane metadata directory complete with all required files
- [ ] All dependencies verified FOSS-compatible
- [ ] Anti-features (NonFreeNet) documented
- [ ] fdroiddata YAML recipe created
- [ ] Reproducible build configuration applied
- [ ] `./gradlew assembleDebug` succeeds

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| APK not reproducible due to R8 | Medium | High | Disable R8 for F-Droid builds; they rebuild anyway |
| ip-api.com flagged as NonFreeNet | Certain | Low | Declare upfront; plan HTTPS migration |
| fdroiddata recipe rejected | Medium | Medium | Test with `fdroid build` locally first |
