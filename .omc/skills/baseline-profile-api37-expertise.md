---
name: baseline-profile-api37-expertise
description: Why baseline profile generation fails on this repo's Pixel/API 37 device and how to make it succeed
triggers:
  - Unable to confirm activity launch completion
  - generateFossReleaseBaselineProfile
  - connectedNonMinifiedReleaseAndroidTest
  - startActivityAndWait
  - baseline profile
  - macrobenchmark
  - keyguard
---

# Baseline Profile Generation on Pixel/API 37

## The Insight
A macrobenchmark "Unable to confirm activity launch completion []" failure is almost never a real app-launch problem. On API 37 (verified on Pixel 10 Pro Fold), `androidx.benchmark 1.3.4`'s `startActivityAndWait()` confirms launch by matching a window `uniqueName` extracted with a regex that captures a per-launch `ViewRootImpl@<hash>`. That hash changes every cold start, so the post-launch window can never match the pre-launch snapshot and the confirmation times out ~28s — even though `am start -W` itself reported `Status: ok` / `Displayed +NNNms`. Second gotcha: a **secure PIN keyguard** produces the SAME timeout because the app never reaches the foreground; adb cannot dismiss a secure keyguard.

## Why This Matters
You will burn multiple 10-20 minute device runs chasing a phantom "launch is broken" bug, when the app launches fine (`am start` logs `Status: ok`). The two real causes are the benchmark-library hash-matching bug and a locked device — neither is in your journey or app code.

## Recognition Pattern
- Exception at `MacrobenchmarkScope.amStartAndWait`, attributed to `BaselineProfileGenerator.kt` `startActivityAndWait()`.
- The generate logcat (`baselineprofile/build/outputs/androidTest-results/connected/.../logcat-*generate.txt`) shows `Benchmark: Status: ok`, `LaunchState: COLD`, `Displayed ... +NNNms`, then a ~28s gap, then `Killing process`.
- Check `adb shell dumpsys window | grep isKeyguardShowing` — if `true`, that's the blocker (adb `wm dismiss-keyguard` cannot clear a secure PIN).

## The Approach (verified conclusion: DON'T generate on the Android 17 device)
This device runs **Android 17 (API 37)** — too new for `androidx.baselineprofile`. Generation
hits a CASCADE of tooling-vs-OS failures, each masking the next; clearing three still leaves a
hard fourth. Generate on a supported-API device/emulator (API 34/35) instead. Cascade observed:
1. **benchmark 1.3.4, `startActivityAndWait`**: "Unable to confirm activity launch completion" —
   launch-confirm matches a window name with a per-launch `ViewRootImpl@<hash>` that never
   matches. (Workaround: manual launcher-Intent launch + `Until.hasObject(By.pkg(...).depth(0))`.)
2. **benchmark 1.3.4, `extractProfile` (BaselineProfiles.kt:219)**: throws because API 37's
   `pm dump-profiles` prepends "Waiting for app processes to flush profiles… / flushed in Nms"
   before "Profile saved to…", which its strict parser rejects. FIXED by bumping to 1.4.0.
3. **The manual-launch workaround from (1) becomes counterproductive on 1.4.0** (which fixes the
   launch bug): `context.startActivity` doesn't integrate with the profiling capture window →
   "Generated Profile is empty". Revert to standard `startActivityAndWait()` on 1.4.0+.
4. **Hard wall — empty ART profile on Android 17.** Even with 1.4.0 + standard launch + a valid
   cold-start+scroll journey, `/data/misc/profman/<pkg>-primary.prof.txt` comes back EMPTY
   (0 lines). ART is not populating a profile for the app on Android 17 during the benchmark run.
   No journey change fixes this — the profiler itself produced nothing.

## Operational notes (still true)
- Device MUST be physically unlocked (secure keyguard blocks the run; adb cannot dismiss it, and
  it manifests as a launch-completion timeout, not an obvious "locked" error). `svc power stayon
  true` + zero the 3 animation scales before a run; restore after.
- The committable `app/src/foss/generated/baselineProfiles/baseline-prof.txt` is written by
  `copyFossReleaseBaselineProfileIntoSrc`, the LAST task. A failed run leaves `build/`
  intermediates (merged_art_profile) populated but `src/` empty — don't mistake that for success.
- Verdict: keep the (correct) `:baselineprofile` scaffolding + 1.4.0 pin; run generation on an
  API 34/35 emulator or an older-API physical device, not the Android 17 Pixel.
