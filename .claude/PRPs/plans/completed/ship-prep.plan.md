# Plan: Ship Prep

## Summary
Prepare NetLens for its first Play Store release: design and wire an adaptive app icon, configure release signing (secrets from local.properties / env vars), formalize versioning, create a README, and scaffold fastlane metadata for Play Store listing.

## User Story
As the developer, I want the app to have a professional icon, secure release signing, proper versioning, a complete README, and Play Store metadata templates so that I can publish to Google Play with confidence.

## Metadata
- **Complexity**: Medium
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: ~25 (mostly new resource files + metadata text files)

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `app/build.gradle.kts` | all | Current versionCode/versionName, no signing config |
| P0 | `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt` | all | compileSdk 35, minSdk 29, targetSdk 35, Java 17 |
| P1 | `app/src/main/AndroidManifest.xml` | all | No android:icon or android:roundIcon set |
| P1 | `app/src/main/kotlin/com.ventouxlabs.netlens/ui/theme/Color.kt` | all | Teal500 (#009688), Cyan500 (#00BCD4), DarkBackground (#0F1318) |
| P2 | `app/src/main/kotlin/com.ventouxlabs.netlens/navigation/ToolDestination.kt` | all | 13 tools across 5 categories for README |
| P2 | `LICENSE` | 1-10 | GNU AGPL v3 |
| P2 | `.gitignore` | all | Already ignores local.properties but not *.jks |

---

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | CREATE | Adaptive icon definition |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | CREATE | Round adaptive icon |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | CREATE | Vector foreground — network/lens motif in teal |
| `app/src/main/res/values/ic_launcher_background.xml` | CREATE | Color resource (#0F1318) |
| `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` | CREATE | Raster fallbacks |
| `app/src/main/AndroidManifest.xml` | UPDATE | Add android:icon and android:roundIcon |
| `app/build.gradle.kts` | UPDATE | Add signingConfigs, buildTypes.release, bump version |
| `app/proguard-rules.pro` | CREATE | Keep rules for kotlinx-serialization, Ktor |
| `.gitignore` | UPDATE | Add *.jks, *.keystore, keystore.properties |
| `README.md` | CREATE | App description, features, build instructions, license |
| `fastlane/metadata/android/en-US/title.txt` | CREATE | "NetLens - Network Toolkit" |
| `fastlane/metadata/android/en-US/short_description.txt` | CREATE | 80-char Play Store summary |
| `fastlane/metadata/android/en-US/full_description.txt` | CREATE | Full Play Store description |
| `fastlane/metadata/android/en-US/changelogs/1.txt` | CREATE | v1.0.0 changelog |

## NOT Building
- Google Play Console setup (manual)
- Actual keystore generation (manual: `keytool -genkey`)
- Real screenshots (need device/emulator)
- Feature graphic (512x1024 — design tool)

---

## Step-by-Step Tasks

### Task 1: Adaptive Icon Foreground Vector
- **ACTION**: Create `app/src/main/res/drawable/ic_launcher_foreground.xml`
- **IMPLEMENT**: 108dp x 108dp viewport, network-lens motif using Teal500 (#009688) and Cyan500 (#00BCD4), content within 66dp safe zone
- **GOTCHA**: Keep vector simple — no gradients requiring API 24+ features beyond basic vector support
- **VALIDATE**: Valid XML with `<vector>` root, viewportWidth/Height="108"

### Task 2: Icon Background Color
- **ACTION**: Create `app/src/main/res/values/ic_launcher_background.xml`
- **IMPLEMENT**: `<color name="ic_launcher_background">#0F1318</color>`
- **VALIDATE**: Valid XML color resource

### Task 3: Adaptive Icon XML Definitions
- **ACTION**: Create `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`
- **IMPLEMENT**: `<adaptive-icon>` pointing to background color + foreground drawable
- **VALIDATE**: Both files exist in mipmap-anydpi-v26/

### Task 4: Raster Fallback Mipmaps
- **ACTION**: Generate WebP raster fallbacks at 5 densities
- **IMPLEMENT**: ic_launcher.webp + ic_launcher_round.webp at mdpi(48), hdpi(72), xhdpi(96), xxhdpi(144), xxxhdpi(192)
- **GOTCHA**: Can be simple placeholder icons initially — real ones come from Image Asset Studio
- **VALIDATE**: 10 WebP files across 5 mipmap directories

### Task 5: Wire Icon in AndroidManifest
- **ACTION**: Update `<application>` tag with `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"`
- **VALIDATE**: `./gradlew :app:processDebugManifest` succeeds

### Task 6: Release Signing Configuration
- **ACTION**: Add signingConfigs block to app/build.gradle.kts
- **IMPLEMENT**: Read keystore path/passwords from local.properties (local) or env vars (CI). Never hardcode secrets.
- **GOTCHA**: Debug builds must still work without any keystore configured
- **VALIDATE**: `./gradlew :app:assembleDebug` still succeeds

### Task 7: ProGuard/R8 Rules
- **ACTION**: Create `app/proguard-rules.pro`
- **IMPLEMENT**: Keep rules for kotlinx-serialization models, Ktor client, Hilt
- **VALIDATE**: File exists, release build doesn't crash serialization

### Task 8: Update .gitignore
- **ACTION**: Add *.jks, *.keystore, keystore.properties
- **VALIDATE**: `git check-ignore release.keystore` returns path

### Task 9: Version Bump
- **ACTION**: Set versionName="1.0.0", keep versionCode=1
- **VALIDATE**: Version appears in merged manifest

### Task 10: Create README.md
- **ACTION**: Project root README with all 13 features grouped by category, build instructions (JDK 17, SDK 35), architecture overview, AGPL-3.0 license
- **VALIDATE**: All sections present

### Task 11: Fastlane Metadata
- **ACTION**: Create fastlane/metadata/android/en-US/ with title.txt (<=30 chars), short_description.txt (<=80 chars), full_description.txt (<=4000 chars), changelogs/1.txt (<=500 chars)
- **VALIDATE**: Character limits respected

---

## Validation Commands

```bash
# Build verification
./gradlew :app:assembleDebug

# Icon in manifest
grep -q 'android:icon' app/src/main/AndroidManifest.xml

# Signing config registered
./gradlew :app:signingReport

# Version check
grep 'versionName' app/build.gradle.kts

# README and fastlane exist
test -f README.md && echo "README exists"
test -f fastlane/metadata/android/en-US/title.txt && echo "Fastlane exists"
```

## Acceptance Criteria
- [ ] Adaptive icon renders on API 26+ with teal foreground on dark background
- [ ] Raster fallbacks exist for all 5 densities
- [ ] AndroidManifest references both icon variants
- [ ] Release signing reads from local.properties or env vars — zero hardcoded secrets
- [ ] ProGuard rules exist for serialization + Ktor
- [ ] .gitignore excludes keystore files
- [ ] versionName="1.0.0", versionCode=1
- [ ] README has features, build instructions, AGPL-3.0
- [ ] Fastlane metadata respects Play Store character limits
- [ ] `./gradlew assembleDebug` succeeds

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Vector icon too complex for small densities | Medium | Low | Keep vector simple, test at mdpi |
| R8 strips serialization classes | Medium | High | Test release build with --info flag |
| Signing config breaks debug builds | Low | High | Guard with if/else for local.properties existence |
