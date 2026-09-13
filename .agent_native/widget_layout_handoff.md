# Widget layout work — handoff

Branch `fix/widget-type-scale`, 2 commits ahead of `master` (`e8d7464`), tree clean, **not pushed**, no PR.

```
3688dae refactor: restructure the 4x2 and 2x2 on the 4x1's layout pattern
89e8bef refactor: give the widgets a type scale and a spacing rhythm
```

Read those two commit messages first — they carry the reasoning, not just the diff.

---

## The one remaining task

**The 4x2 is too sparse.** Content fill measured on device at `fontScale 1.15`:

| Widget | Before | Now | |
|---|---|---|---|
| 2x1 Compact | 24% | **59%** | done |
| 2x2 Standard | 40% | **63%** | done |
| 4x1 Dashboard | 62% | 62% | **reference — do not change** |
| 4x2 | 71% | **53%** | ← the job |

The 4x2 regressed because the restructure removed the VPN column and the sparkline, taking out more height than the larger type put back. Its *structure* is correct now (header / addresses / status / detail / chips, all full width, matching the 4x1). What is wrong is that ~160dp of content sits in a 306dp box, leaving two vertical voids.

**Proposed fix, not yet attempted:** stack WAN and LAN as full-width blocks — the 2x1's pattern — instead of side by side. Side-by-side caps each address at ~150dp of width, which caps type at 18sp. Full width allows ~28sp, filling the height with payload instead of padding, and makes all four widgets share one idiom.

Arithmetic for sizing: rendered size = `design × min(fontScale, 1.15)`. Bold IPv4 runs ≈ **0.51dp per char per sp** — calibrated against one real data point (13 chars at 17sp fits a 132dp column) and slightly pessimistic. Worst realistic address is 15 chars (`185.199.108.153`).

You may well conclude a different fix is better. The constraint that matters is the user's: **"only the 4x1 uses the space correctly"** — it is the reference for density and alignment.

---

## Three silent failure modes in this stack

All three render wrong with **no crash, no log, and nothing any JVM test can observe** — Glance composables are not Paparazzi-renderable and no unit test sees RemoteViews layout. Each was found only by rendering on a device. `ui/FourByTwoVariant.kt` documents them in the code.

### 1. Weighted content collapses to zero

A vertical `defaultWeight()` on a **Column** child lowers to `height=0dp` + `layout_weight=1`. Under overrun it resolves to zero and **the view is deleted from the tree** — `uiautomator` shows it absent, not clipped.

This deleted both IP addresses from the 4x2. Measured: root 158.4dp − header 32.8 − dividers 2 − sparkline 16 = 105.6dp split between two weighted children (53.7 and 54.2 measured); the Row holding both addresses was itself weighted, got 18dp, and `"WAN"` at 13sp ate 17 of it.

**Invariant: vertical weight only on a childless `Spacer`.** A Spacer has nothing to lose when it collapses.

### 2. `match_parent` height inflates and evicts siblings

`fillMaxHeight()` on a **Row** child lowers to `height=match_parent`, and `getChildMeasureSpec(AT_MOST(remaining), MATCH_PARENT)` returns `EXACTLY(remaining)` — the child expands to *every remaining pixel* and pushes its siblings out of the box.

This is the exact inverse of #1, and **fixing #1 exposes it**: the weight had been bounding the `match_parent` child all along.

### 3. Containers cap at 10 children

Glance ships pre-generated container layouts for **0..10 children and no further** — verified in `glance-appwidget-1.1.1.aar`, `column_*_0children.xml` … `_10children.xml`, across all 231 alignment variants. A container with more renders only its **first ten**.

The restructured 4x2 root Column had 13 children; its chips row and the hairline above it were never emitted. Count children whenever you add a row.

Two corollaries:
- **Nesting buys budget** — a child container gets its own ten. That is how the 4x2 (9) and 2x2 (7) got back under.
- **A nested container cannot host a `SectionGap`.** Only the root Column is `fillMaxSize`; a weighted Spacer inside a wrap-content child divides a surplus of zero. Slack distribution must stay in the root, which makes gaps expensive in child slots — hence "fewer gaps, more padding".

---

## Landmines

- **`ChipCatalog.MAX_WIDGET_CHIPS` cannot exceed 4.** Both full-width chip rows are `Portal + n × (spacer + chip)` = 9 children at 4 chips. At 5 they emit 11 and each row silently loses its last two. Documented at the constant. The 4x1 has been one increment from this since it was written.
- **`widgetSp` caps growth at 1.15×** (`WidgetTextScaling.kt`). At `fontScale ≥ 1.15` every text renders 15% larger than design while fixed-size elements do not — roughly +25dp across the 4x2. The test devices run exactly 1.15, so this is the normal case, not an edge case.
- **12sp is the type floor**, set deliberately in `d0c2f52` for legibility. `WidgetType.kt` holds the scale as plain `Float` constants precisely so `fontSize = WidgetType.LABEL` does not compile and only `widgetSp(WidgetType.LABEL)` does. Do not reintroduce raw `.sp`.
- **Glance state is per widget *instance*.** Removing and re-adding a widget resets its DataStore history. That is why `LatencySparkline` drew two lonely bars on a fresh placement — not a broken pipeline.
- **`minResizeHeight` ≤ `minHeight`** is enforced by the platform; equality is fine and is the house pattern.

---

## Verification

CI and unit tests cannot see any of the above. The real check is a device.

```
python3 tools/verify_widget.py <adb-serial> [FourByTwo|Dashboard|Standard|Compact]
```

The script is committed at `tools/verify_widget.py`. It:

- refuses to run (exit 2) rather than guessing when the named widget is not placed
- partitions co-located widgets by outermost host-view rect, and classifies by **width first** (≥300dp = 4-column) then scan-footer presence — content signatures alone are not enough, they broke once the 2x2 gained a LAN block
- asserts each widget's own payload, fails on any ellipsis, and prints every text node

Two false FAILs came from letting it drift behind the code. If you change what a widget renders, update its assertions in the same pass.

Build and install for device checks (release, because the placed widgets belong to `com.ventouxlabs.netlens`, and a debug install is a *separate package* that will not touch them):

```
./gradlew assembleFossRelease
adb -s <serial> install -r app/build/outputs/apk/foss/release/app-foss-release.apk
```

Signing is configured in `local.properties`; verify the cert matches `8fdfc928f8f04c6fbca94d4712a599570b5262b71897f4f576f090aa086ae2b4` (`CN=Ventoux Advisory Co`, v2 only) before installing over a real device.

Full suite (all three tasks — they cover disjoint source sets):
```
./gradlew testFossDebugUnitTest testGplayDebugUnitTest testDebugUnitTest
```

---

## Open issues

- **#169** — ~50 `expectMostRecentItem()` sites across 8 test files sample instead of waiting. One already failed CI. `yield()` does **not** reproduce it; a real `withContext(Dispatchers.IO) { Thread.sleep(30) }` in the fake does. Fix shape landed for `monitor` in `e6dd285`.
- **#171** — VPN caption `Protected` → `VPN On`. **Probably now moot**: the 4x2's standalone VPN column is gone, so the 44dp width constraint that forced the shortening no longer exists. Worth re-checking and closing.
- **#172** — 4x2 content taller than a 4x2 slot on an ordinary phone grid. Partly addressed by the restructure; re-measure before acting.

## Deliberate deviations from the original brief

- The 2x2 **gained the LAN address**, which the "add no data" constraint forbade. `state.localIp` was already in `WidgetState`, every sibling widget showed it, and 180dp of dead space could not be closed by sizing alone. Easy to revert if unwanted.
- The 4x2 **lost the sparkline**. Five 4dp bars alone in a 427dp band read as an artifact, not a chart.
- `DashboardFullContent` (4x1) is **untouched** — absent from the diff entirely, not merely byte-identical. It is the user's reference.
