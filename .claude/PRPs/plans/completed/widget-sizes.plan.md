# Plan: Add 4x2 and 5x1 Widget Size Options

## Summary
Add two new widget sizes — WIDE (4×2) and BANNER (5×1) — to the existing Glance-based home screen widget. WIDE renders both Connection and Network pages side by side with a vertical divider (no carousel). BANNER renders a single dense row with all key data and forces text size SMALL. New appwidget-provider XMLs and receiver classes let users pick these sizes from the launcher's widget picker. The WidgetSettingsScreen size toggle expands from 2 options to 4 with selectable shape cards.

## User Story
As a NetLens user,
I want wider widget sizes that show more information at a glance,
So that I can see all my network details without swiping through carousel pages.

## Problem → Solution
Only 2×1 and 2×2 widget sizes exist — users with large screens or minimal home screens can't see all network info at once → Add 4×2 (both pages side by side) and 5×1 (single-line dense banner) options.

## Metadata
- **Complexity**: Medium
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: ~10 modified/created
- **Branch**: `feature/widget-sizes`
- **PR Title**: `feat: add 4x2 and 5x1 widget size options`

---

## UX Design

### Before
```
Widget picker shows 1 widget:
┌──────────────────┐
│ NetLens (2×1/2×2)│
└──────────────────┘

Settings → Layout → Size toggle:
  [2×1 Compact] [2×2 Full]

Widget renders:
┌──────────────────────┐
│ 🇺🇸 ●vpn  1.2.3.4  >│
│ Home-WiFi · 192.…    │
│        ● ○            │
└──────────────────────┘
```

### After
```
Widget picker shows 3 widgets:
┌──────────────────┐  ┌──────────────────┐  ┌─────────────────────────────┐
│ NetLens (2×1/2×2)│  │ NetLens Wide 4×2 │  │ NetLens Banner 5×1          │
└──────────────────┘  └──────────────────┘  └─────────────────────────────┘

Settings → Layout → 2×2 grid of size cards:
  ┌─────────────┐  ┌─────────────┐
  │ ■■  2×1     │  │ ■■  2×2     │
  │ Compact     │  │ ■■  Full    │
  ├─────────────┤  ├─────────────┤
  │ ■■■■  4×2   │  │ ■■■■■  5×1  │
  │ ■■■■  Wide  │  │       Banner│
  └─────────────┘  └─────────────┘

WIDE widget (4×2):
┌───────────────────────────┬───────────────────────────┐
│ 🇺🇸 ●vpn  1.2.3.4        │  Gateway  192.168.1.1     │
│    Home-WiFi · 192.168…   │  DNS  1.1.1.1, 1.0.0.1   │
└───────────────────────────┴───────────────────────────┘

BANNER widget (5×1):
┌──────────────────────────────────────────────────────────┐
│ 🇺🇸 ●vpn  1.2.3.4  ·  Home-WiFi  ·  GW 192.168.1.1    │
└──────────────────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Widget picker | 1 NetLens widget | 3 widgets (default, wide, banner) | Each with own appwidget-provider |
| Settings size toggle | 2 FilterChips in FlowRow | 2×2 grid of selectable shape cards | Visual shape representation |
| WIDE widget content | N/A | Both pages side by side, no carousel | Divider between pages |
| BANNER widget content | N/A | Single dense row, everything inline | Text forced SMALL, tap opens app |
| Settings preview | Shows Connection page only | Adapts shape to selected size | WIDE shows side-by-side preview |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `widget/src/main/kotlin/.../NetLensWidget.kt` | 74-118 | `WidgetRoot` — refactor for size branching |
| P0 | `widget/src/main/kotlin/.../NetLensWidget.kt` | 120-275 | `ConnectionPage` + `NetworkPage` — reuse in new layouts |
| P0 | `widget/src/main/kotlin/.../model/WidgetPreferences.kt` | 28-31 | `WidgetSize` enum to extend |
| P0 | `feature/widgetsettings/.../WidgetSettingsScreen.kt` | 259-275 | `WidgetSizeToggle` to replace |
| P1 | `widget/src/main/kotlin/.../NetLensWidgetReceiver.kt` | all | Receiver pattern to duplicate for new sizes |
| P1 | `widget/src/main/kotlin/.../data/WidgetPreferencesRepository.kt` | 56-69 | `toWidgetPreferences` — enum deserialization (handles new values automatically) |
| P1 | `app/src/main/AndroidManifest.xml` | 34-43 | Receiver registration pattern |
| P2 | `widget/src/main/res/xml/widget_small.xml` | all | XML template for new provider files |
| P2 | `widget/src/main/res/values/strings.xml` | all | Widget description strings |
| P2 | `feature/widgetsettings/.../WidgetSettingsScreen.kt` | 142-189 | `WidgetPreview` — adapt for new sizes |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| `targetCellWidth/Height` | Android AppWidgetProviderInfo | Requires API 31+ (Android 12); pre-31 devices use `minWidth/minHeight` dp values only |
| Glance `SizeMode` | AndroidX Glance docs | `SizeMode.Single` is default; `SizeMode.Responsive` adapts to available size but adds complexity — unnecessary here since size is preferences-driven |
| Multiple widget receivers | Android widget docs | Each receiver = separate entry in launcher's widget picker; all can share the same `GlanceAppWidget` class |

---

## Patterns to Mirror

### WIDGET_ROOT_COMPOSABLE
```kotlin
// SOURCE: widget/src/main/kotlin/.../NetLensWidget.kt:74-118
@Composable
private fun WidgetRoot(
    state: IpWidgetState,
    widgetPrefs: WidgetPreferences,
    pageIndex: Int,
) {
    val bgColor = Color(widgetPrefs.backgroundColor.argb).copy(alpha = widgetPrefs.backgroundAlpha)
    val accentColor = Color(widgetPrefs.accentColor.argb)
    val textColor = if (widgetPrefs.backgroundColor == WidgetColor.WHITE) Color.Black else Color.White
    val textSizeSp = widgetPrefs.textSize.sp.sp
    val isSmall = widgetPrefs.widgetSize == WidgetSize.SMALL
    // ... Column with page content + optional PageIndicator
}
```

### CONNECTION_PAGE
```kotlin
// SOURCE: widget/src/main/kotlin/.../NetLensWidget.kt:120-199
@Composable
private fun ConnectionPage(
    state: IpWidgetState,
    textColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
    accentColor: Color,
    isSmall: Boolean,
) {
    // Row: flag emoji, VPN dot, public IP, optional nav arrow
    // Row: SSID · localIP
}
```

### NETWORK_PAGE
```kotlin
// SOURCE: widget/src/main/kotlin/.../NetLensWidget.kt:201-275
@Composable
private fun NetworkPage(
    state: IpWidgetState,
    textColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
    accentColor: Color,
    isSmall: Boolean,
) {
    // Row: optional nav arrow, Gateway label, gateway IP
    // Row: DNS label, dns servers
}
```

### GLANCE_RECEIVER
```kotlin
// SOURCE: widget/src/main/kotlin/.../NetLensWidgetReceiver.kt:16-18
class NetLensWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NetLensWidget()
}
```

### APPWIDGET_PROVIDER_XML
```xml
<!-- SOURCE: widget/src/main/res/xml/widget_small.xml -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="60dp"
    android:updatePeriodMillis="1800000"
    android:resizeMode="horizontal|vertical"
    android:initialLayout="@layout/widget_initial"
    android:widgetCategory="home_screen"
    android:description="@string/widget_small_description" />
```

### MANIFEST_RECEIVER
```xml
<!-- SOURCE: app/src/main/AndroidManifest.xml:34-43 -->
<receiver
    android:name="com.ventouxlabs.netlens.widget.NetLensWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/netlens_widget_info" />
</receiver>
```

### WIDGET_SIZE_TOGGLE
```kotlin
// SOURCE: feature/widgetsettings/.../WidgetSettingsScreen.kt:259-275
@Composable
private fun WidgetSizeToggle(selected: WidgetSize, onSelect: (WidgetSize) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetSize.entries.forEach { size ->
            val label = when (size) {
                WidgetSize.SMALL -> "2×1 Compact"
                WidgetSize.MEDIUM -> "2×2 Full"
            }
            FilterChip(
                selected = selected == size,
                onClick = { onSelect(size) },
                label = { Text(label) },
            )
        }
    }
}
```

### WIDGET_PREVIEW
```kotlin
// SOURCE: feature/widgetsettings/.../WidgetSettingsScreen.kt:142-189
@Composable
private fun WidgetPreview(prefs: WidgetPreferences) {
    // Box with background color, corner radius, border
    // Column with mock Connection page content + page dots
}
```

### DATASTORE_ENUM_DESERIALIZATION
```kotlin
// SOURCE: widget/src/main/kotlin/.../data/WidgetPreferencesRepository.kt:64
widgetSize = this[WIDGET_SIZE]?.let { runCatching { WidgetSize.valueOf(it) }.getOrNull() }
    ?: defaults.widgetSize,
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `widget/src/main/kotlin/.../model/WidgetPreferences.kt` | UPDATE | Add WIDE, BANNER to WidgetSize enum |
| `widget/src/main/kotlin/.../NetLensWidget.kt` | UPDATE | Add WideWidgetContent and BannerWidgetContent composables, refactor WidgetRoot |
| `widget/src/main/kotlin/.../WideWidgetReceiver.kt` | CREATE | New receiver for 4×2 widget |
| `widget/src/main/kotlin/.../BannerWidgetReceiver.kt` | CREATE | New receiver for 5×1 widget |
| `widget/src/main/res/xml/widget_wide.xml` | CREATE | appwidget-provider for 4×2 |
| `widget/src/main/res/xml/widget_banner.xml` | CREATE | appwidget-provider for 5×1 |
| `widget/src/main/res/values/strings.xml` | UPDATE | Add description strings for new sizes |
| `app/src/main/AndroidManifest.xml` | UPDATE | Register new receiver entries |
| `feature/widgetsettings/.../WidgetSettingsScreen.kt` | UPDATE | Replace WidgetSizeToggle with 2×2 card grid, update WidgetPreview |
| `widget/src/main/kotlin/.../WidgetRefresh.kt` | UPDATE | Refresh all widget types |

## NOT Building

- Per-instance widget preferences (remains global — pre-existing limitation)
- Widget configuration activity (configure-on-add flow)
- Responsive sizing via `SizeMode.Responsive` (size is preferences-driven)
- New color or accent options
- Changes to existing SMALL/MEDIUM behavior
- Widget tests (no test infrastructure exists for Glance widgets)
- API level gating for `targetCellWidth/Height` in XML (XML attributes are ignored on older APIs; `minWidth/minHeight` provides fallback)

---

## Step-by-Step Tasks

### Task 1: Add WIDE and BANNER to WidgetSize enum
- **ACTION**: Edit `widget/src/main/kotlin/.../model/WidgetPreferences.kt` lines 28-31
- **IMPLEMENT**:
  ```kotlin
  enum class WidgetSize {
      SMALL,
      MEDIUM,
      WIDE,
      BANNER,
  }
  ```
- **MIRROR**: Existing enum style (no constructor params)
- **IMPORTS**: None
- **GOTCHA**: `WidgetPreferencesRepository.toWidgetPreferences()` already uses `runCatching { WidgetSize.valueOf(it) }` — new values are deserialized automatically. No migration needed.
- **VALIDATE**: Module compiles. Existing widget code still works (new values aren't referenced yet).

### Task 2: Create widget_wide.xml appwidget-provider
- **ACTION**: Create `widget/src/main/res/xml/widget_wide.xml`
- **IMPLEMENT**:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
      android:minWidth="250dp"
      android:minHeight="110dp"
      android:targetCellWidth="4"
      android:targetCellHeight="2"
      android:updatePeriodMillis="1800000"
      android:resizeMode="horizontal|vertical"
      android:initialLayout="@layout/widget_initial"
      android:widgetCategory="home_screen"
      android:description="@string/widget_wide_description" />
  ```
- **MIRROR**: APPWIDGET_PROVIDER_XML — same structure as `widget_small.xml`
- **IMPORTS**: N/A
- **GOTCHA**: `targetCellWidth/targetCellHeight` are API 31+ attributes but are simply ignored on older APIs — no runtime guard needed in XML. The `minWidth/minHeight` dp values provide the fallback. 250dp ≈ 4 cells, 110dp ≈ 2 cells.
- **VALIDATE**: XML parses without errors

### Task 3: Create widget_banner.xml appwidget-provider
- **ACTION**: Create `widget/src/main/res/xml/widget_banner.xml`
- **IMPLEMENT**:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
      android:minWidth="320dp"
      android:minHeight="50dp"
      android:targetCellWidth="5"
      android:targetCellHeight="1"
      android:updatePeriodMillis="1800000"
      android:resizeMode="horizontal"
      android:initialLayout="@layout/widget_initial"
      android:widgetCategory="home_screen"
      android:description="@string/widget_banner_description" />
  ```
- **MIRROR**: APPWIDGET_PROVIDER_XML
- **IMPORTS**: N/A
- **GOTCHA**: `resizeMode` is `horizontal` only for banner — vertical resize doesn't make sense for a 1-row widget. 320dp ≈ 5 cells.
- **VALIDATE**: XML parses without errors

### Task 4: Add widget description strings
- **ACTION**: Edit `widget/src/main/res/values/strings.xml` to add descriptions for new sizes
- **IMPLEMENT**:
  ```xml
  <string name="widget_wide_description">Wide network info showing all data side by side (4×2)</string>
  <string name="widget_banner_description">Compact banner with network info in a single row (5×1)</string>
  ```
- **MIRROR**: Existing `widget_small_description` / `widget_medium_description` pattern
- **IMPORTS**: N/A
- **GOTCHA**: None
- **VALIDATE**: No unresolved string references

### Task 5: Create WideWidgetReceiver
- **ACTION**: Create `widget/src/main/kotlin/com.ventouxlabs.netlens/widget/WideWidgetReceiver.kt`
- **IMPLEMENT**:
  ```kotlin
  package com.ventouxlabs.netlens.widget

  import androidx.glance.appwidget.GlanceAppWidget
  import androidx.glance.appwidget.GlanceAppWidgetReceiver

  class WideWidgetReceiver : GlanceAppWidgetReceiver() {
      override val glanceAppWidget: GlanceAppWidget = NetLensWidget()
  }
  ```
- **MIRROR**: GLANCE_RECEIVER — exact same pattern as `NetLensWidgetReceiver` (but without the periodic work scheduling, which is already handled by the primary receiver)
- **IMPORTS**: GlanceAppWidget, GlanceAppWidgetReceiver
- **GOTCHA**: No `onEnabled`/`onDisabled` overrides needed — the primary `NetLensWidgetReceiver` already schedules periodic refresh and network callbacks. Additional receivers just display the same widget content.
- **VALIDATE**: Compiles

### Task 6: Create BannerWidgetReceiver
- **ACTION**: Create `widget/src/main/kotlin/com.ventouxlabs.netlens/widget/BannerWidgetReceiver.kt`
- **IMPLEMENT**:
  ```kotlin
  package com.ventouxlabs.netlens.widget

  import androidx.glance.appwidget.GlanceAppWidget
  import androidx.glance.appwidget.GlanceAppWidgetReceiver

  class BannerWidgetReceiver : GlanceAppWidgetReceiver() {
      override val glanceAppWidget: GlanceAppWidget = NetLensWidget()
  }
  ```
- **MIRROR**: GLANCE_RECEIVER
- **IMPORTS**: GlanceAppWidget, GlanceAppWidgetReceiver
- **GOTCHA**: Same as Task 5
- **VALIDATE**: Compiles

### Task 7: Register new receivers in AndroidManifest.xml
- **ACTION**: Add two new `<receiver>` entries in `app/src/main/AndroidManifest.xml` after the existing one (line 43)
- **IMPLEMENT**:
  ```xml
  <receiver
      android:name="com.ventouxlabs.netlens.widget.WideWidgetReceiver"
      android:exported="true">
      <intent-filter>
          <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
      </intent-filter>
      <meta-data
          android:name="android.appwidget.provider"
          android:resource="@xml/widget_wide" />
  </receiver>

  <receiver
      android:name="com.ventouxlabs.netlens.widget.BannerWidgetReceiver"
      android:exported="true">
      <intent-filter>
          <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
      </intent-filter>
      <meta-data
          android:name="android.appwidget.provider"
          android:resource="@xml/widget_banner" />
  </receiver>
  ```
- **MIRROR**: MANIFEST_RECEIVER — exact same structure as existing NetLensWidgetReceiver entry
- **IMPORTS**: N/A
- **GOTCHA**: Must be inside `<application>` tag. Each receiver uses its own appwidget-provider XML.
- **VALIDATE**: Manifest parses. `./gradlew assembleDebug` succeeds. Three widgets appear in launcher's widget picker.

### Task 8: Implement WideWidgetContent composable
- **ACTION**: Add `WideWidgetContent` composable in `NetLensWidget.kt` — renders both pages side by side with a vertical divider
- **IMPLEMENT**:
  ```kotlin
  @Composable
  private fun WideWidgetContent(
      state: IpWidgetState,
      textColor: Color,
      textSizeSp: androidx.compose.ui.unit.TextUnit,
      accentColor: Color,
  ) {
      Row(
          modifier = GlanceModifier.fillMaxSize(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
          Box(modifier = GlanceModifier.defaultWeight()) {
              Column(
                  modifier = GlanceModifier.fillMaxSize(),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                  ConnectionPage(state, textColor, textSizeSp, accentColor, isSmall = false)
              }
          }

          // Vertical divider
          Box(
              modifier = GlanceModifier
                  .width(1.dp)
                  .height(48.dp)
                  .background(ColorProvider(textColor.copy(alpha = 0.2f))),
          ) {}

          Box(modifier = GlanceModifier.defaultWeight()) {
              Column(
                  modifier = GlanceModifier.fillMaxSize().padding(start = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                  NetworkPage(state, textColor, textSizeSp, accentColor, isSmall = false)
              }
          }
      }
  }
  ```
- **MIRROR**: CONNECTION_PAGE, NETWORK_PAGE — reuse existing page composables with `isSmall = false` to show nav arrows disabled. Actually nav arrows should be hidden in wide mode — see Task 10 for refactoring `isSmall` parameter.
- **IMPORTS**: All already imported in NetLensWidget.kt
- **GOTCHA**: Glance doesn't have a `VerticalDivider` composable — use `Box` with 1dp width and background. `isSmall = false` currently shows nav arrows `<` and `>` — need to suppress those for WIDE. See Task 10.
- **VALIDATE**: Renders two pages side by side in preview.

### Task 9: Implement BannerWidgetContent composable
- **ACTION**: Add `BannerWidgetContent` composable in `NetLensWidget.kt` — single dense row with all info
- **IMPLEMENT**:
  ```kotlin
  @Composable
  private fun BannerWidgetContent(
      state: IpWidgetState,
      textColor: Color,
      accentColor: Color,
  ) {
      val textSizeSp = WidgetTextSize.SMALL.sp.sp  // forced SMALL regardless of preference

      Row(
          modifier = GlanceModifier.fillMaxSize()
              .clickable(actionRunCallback<OpenAppAction>()),
          verticalAlignment = Alignment.CenterVertically,
      ) {
          if (state.countryCode.isNotEmpty()) {
              Text(
                  text = state.countryCode.toFlagEmoji(),
                  style = TextStyle(fontSize = (textSizeSp.value + FLAG_EMOJI_SIZE_OFFSET).sp),
              )
              Spacer(modifier = GlanceModifier.width(4.dp))
          }

          val vpnDot = if (state.isVpn) "●" else "○"
          Text(
              text = "${vpnDot}vpn",
              style = TextStyle(
                  color = ColorProvider(if (state.isVpn) accentColor else textColor.copy(alpha = 0.5f)),
                  fontSize = (textSizeSp.value + LABEL_SIZE_OFFSET).sp,
              ),
          )

          Spacer(modifier = GlanceModifier.width(6.dp))

          Text(
              text = state.publicIp.ifEmpty { "—" },
              style = TextStyle(
                  color = ColorProvider(textColor),
                  fontWeight = FontWeight.Bold,
                  fontSize = textSizeSp,
              ),
              maxLines = 1,
          )

          DotSeparator(textColor)

          Text(
              text = state.ssid ?: "—",
              style = TextStyle(
                  color = ColorProvider(textColor.copy(alpha = 0.7f)),
                  fontSize = textSizeSp,
              ),
              maxLines = 1,
          )

          DotSeparator(textColor)

          Text(
              text = "GW ${state.gateway ?: "—"}",
              style = TextStyle(
                  color = ColorProvider(textColor.copy(alpha = 0.7f)),
                  fontSize = textSizeSp,
              ),
              maxLines = 1,
          )
      }
  }

  @Composable
  private fun DotSeparator(textColor: Color) {
      Text(
          text = " · ",
          style = TextStyle(
              color = ColorProvider(textColor.copy(alpha = 0.4f)),
              fontSize = 11.sp,
          ),
      )
  }
  ```
- **MIRROR**: CONNECTION_PAGE — same text style constants, color logic, flag emoji pattern
- **IMPORTS**: All already imported
- **GOTCHA**: Banner forces `WidgetTextSize.SMALL.sp.sp` regardless of the user's text size preference. The entire Row is clickable via `OpenAppAction` (not per-element copy actions — too small in 1-row). Truncation happens naturally via `maxLines = 1`.
- **VALIDATE**: Renders dense single row. Tap opens app.

### Task 10: Refactor WidgetRoot to dispatch by size
- **ACTION**: Refactor `WidgetRoot` in `NetLensWidget.kt` to use `when (widgetPrefs.widgetSize)` instead of `isSmall` boolean
- **IMPLEMENT**: Replace the current content body (lines 87-117) with:
  ```kotlin
  @Composable
  private fun WidgetRoot(
      state: IpWidgetState,
      widgetPrefs: WidgetPreferences,
      pageIndex: Int,
  ) {
      val bgColor = Color(widgetPrefs.backgroundColor.argb).copy(alpha = widgetPrefs.backgroundAlpha)
      val accentColor = Color(widgetPrefs.accentColor.argb)
      val textColor = if (widgetPrefs.backgroundColor == WidgetColor.WHITE) Color.Black else Color.White
      val textSizeSp = widgetPrefs.textSize.sp.sp
      val pages = widgetPrefs.pages
      val safeIndex = if (pages.isEmpty()) 0 else pageIndex.coerceIn(0, pages.lastIndex)
      val currentPage = pages.getOrNull(safeIndex) ?: WidgetPage.CONNECTION

      val baseModifier = GlanceModifier
          .fillMaxSize()
          .cornerRadius(widgetPrefs.cornerRadius.dp)
          .background(ColorProvider(bgColor))

      when (widgetPrefs.widgetSize) {
          WidgetSize.WIDE -> {
              Column(
                  modifier = baseModifier.clickable(actionRunCallback<OpenAppAction>()).padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                  WideWidgetContent(state, textColor, textSizeSp, accentColor)
              }
          }
          WidgetSize.BANNER -> {
              Column(
                  modifier = baseModifier.clickable(actionRunCallback<OpenAppAction>()).padding(horizontal = 12.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                  BannerWidgetContent(state, textColor, accentColor)
              }
          }
          else -> {
              // SMALL and MEDIUM — existing carousel behavior
              val isSmall = widgetPrefs.widgetSize == WidgetSize.SMALL
              val rootModifier = if (isSmall) {
                  baseModifier.clickable(actionRunCallback<CarouselNextAction>())
              } else {
                  baseModifier.clickable(actionRunCallback<OpenAppAction>())
              }
              Column(
                  modifier = rootModifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                  when (currentPage) {
                      WidgetPage.CONNECTION -> ConnectionPage(state, textColor, textSizeSp, accentColor, isSmall)
                      WidgetPage.NETWORK -> NetworkPage(state, textColor, textSizeSp, accentColor, isSmall)
                  }
                  if (pages.size > 1) {
                      Spacer(modifier = GlanceModifier.height(4.dp))
                      PageIndicator(
                          pageCount = pages.size,
                          currentIndex = safeIndex,
                          accentColor = accentColor,
                          dimColor = textColor.copy(alpha = 0.3f),
                      )
                  }
              }
          }
      }
  }
  ```
- **MIRROR**: WIDGET_ROOT_COMPOSABLE — preserves existing color/text logic exactly
- **IMPORTS**: None new
- **GOTCHA**: The `else` branch handles both SMALL and MEDIUM with the original carousel behavior — no behavioral change for existing sizes. WIDE and BANNER skip carousel, page indicators, and nav arrows entirely. The `ConnectionPage` and `NetworkPage` composables are reused for WIDE with `isSmall = false` — nav arrows (`<` / `>`) currently show when `!isSmall`, but they need to be hidden for WIDE. The simplest fix: change the `isSmall` parameter in these pages to a more general `showNavArrows` parameter, and pass `false` from WideWidgetContent.
- **VALIDATE**: Existing SMALL/MEDIUM behavior unchanged. WIDE shows both pages. BANNER shows dense row.

### Task 11: Refactor ConnectionPage/NetworkPage nav arrow parameter
- **ACTION**: Rename `isSmall: Boolean` parameter to `showNavArrows: Boolean` (inverted logic) in `ConnectionPage` and `NetworkPage`
- **IMPLEMENT**:
  - `ConnectionPage` signature: `isSmall: Boolean` → keep as is, but the WIDE caller passes `isSmall = true` to hide arrows (since arrows show when `!isSmall`). Actually, this is confusing. Better: just suppress the arrows in WideWidgetContent by not passing through to the pages.
  
  Simpler approach: add a `showNavArrows` parameter defaulting to `!isSmall`:
  ```kotlin
  @Composable
  private fun ConnectionPage(
      state: IpWidgetState,
      textColor: Color,
      textSizeSp: androidx.compose.ui.unit.TextUnit,
      accentColor: Color,
      isSmall: Boolean,
      showNavArrows: Boolean = !isSmall,
  )
  ```
  Replace `if (!isSmall)` arrow conditionals with `if (showNavArrows)`. Same for `NetworkPage`.
  In `WideWidgetContent`, call with `showNavArrows = false`.
- **MIRROR**: CONNECTION_PAGE, NETWORK_PAGE
- **IMPORTS**: None
- **GOTCHA**: Default `showNavArrows = !isSmall` preserves existing behavior for all current callers (SMALL and MEDIUM branches) without changing call sites.
- **VALIDATE**: Existing behavior unchanged. WIDE layout has no arrows.

### Task 12: Update WidgetRefresh to refresh all receiver types
- **ACTION**: Update `WidgetRefresh.kt` to also update widget instances from new receivers
- **IMPLEMENT**:
  ```kotlin
  suspend fun refreshAllWidgets(context: Context) {
      NetLensWidget().updateAll(context)
  }
  ```
  Actually, `GlanceAppWidget.updateAll()` updates all instances of that widget class regardless of which receiver created them. Since `WideWidgetReceiver` and `BannerWidgetReceiver` both use `NetLensWidget()`, calling `NetLensWidget().updateAll(context)` already refreshes all three receiver types. **No change needed.**
- **MIRROR**: N/A
- **IMPORTS**: N/A
- **GOTCHA**: `updateAll()` works on the `GlanceAppWidget` class, not the receiver. All receivers share `NetLensWidget`, so one `updateAll()` covers everything.
- **VALIDATE**: Confirm by adding a wide widget and verifying it refreshes when data changes.

### Task 13: Replace WidgetSizeToggle with 2×2 card grid
- **ACTION**: Replace `WidgetSizeToggle` composable in `WidgetSettingsScreen.kt` (lines 259-275) with a grid of selectable size cards
- **IMPLEMENT**:
  ```kotlin
  @Composable
  private fun WidgetSizeSelector(selected: WidgetSize, onSelect: (WidgetSize) -> Unit) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
              SizeCard(
                  size = WidgetSize.SMALL,
                  label = "2×1",
                  subtitle = "Compact",
                  aspectRatio = 2f / 1f,
                  selected = selected == WidgetSize.SMALL,
                  onSelect = { onSelect(WidgetSize.SMALL) },
                  modifier = Modifier.weight(1f),
              )
              SizeCard(
                  size = WidgetSize.MEDIUM,
                  label = "2×2",
                  subtitle = "Full",
                  aspectRatio = 2f / 2f,
                  selected = selected == WidgetSize.MEDIUM,
                  onSelect = { onSelect(WidgetSize.MEDIUM) },
                  modifier = Modifier.weight(1f),
              )
          }
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
              SizeCard(
                  size = WidgetSize.WIDE,
                  label = "4×2",
                  subtitle = "Wide",
                  aspectRatio = 4f / 2f,
                  selected = selected == WidgetSize.WIDE,
                  onSelect = { onSelect(WidgetSize.WIDE) },
                  modifier = Modifier.weight(1f),
              )
              SizeCard(
                  size = WidgetSize.BANNER,
                  label = "5×1",
                  subtitle = "Banner",
                  aspectRatio = 5f / 1f,
                  selected = selected == WidgetSize.BANNER,
                  onSelect = { onSelect(WidgetSize.BANNER) },
                  modifier = Modifier.weight(1f),
              )
          }
      }
  }

  @Composable
  private fun SizeCard(
      size: WidgetSize,
      label: String,
      subtitle: String,
      aspectRatio: Float,
      selected: Boolean,
      onSelect: () -> Unit,
      modifier: Modifier = Modifier,
  ) {
      val borderColor = if (selected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
      val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
          else Color.Transparent

      Column(
          modifier = modifier
              .clip(RoundedCornerShape(12.dp))
              .border(2.dp, borderColor, RoundedCornerShape(12.dp))
              .background(bgColor)
              .clickable { onSelect() }
              .padding(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
          // Shape representation
          Box(
              modifier = Modifier
                  .fillMaxWidth(0.8f)
                  .height((40 / aspectRatio).dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(
                      if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                  ),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
  }
  ```
  Update the call site (line 101):
  ```kotlin
  WidgetSizeSelector(prefs.widgetSize) { viewModel.setWidgetSize(it) }
  ```
- **MIRROR**: WIDGET_SIZE_TOGGLE — same callback pattern, same location in layout
- **IMPORTS**: Add `RoundedCornerShape` (already imported as `RoundedCornerShape`), `Color` (already imported)
- **GOTCHA**: The `aspectRatio` is used to visually represent the shape ratio — a 5×1 banner shape is very wide and short, 2×2 is square. `height((40 / aspectRatio).dp)` makes tall shapes shorter and wide shapes even shorter, creating a visual distinction.
- **VALIDATE**: Four cards visible in 2×2 grid. Selection updates preference. Visual shapes differ per size.

### Task 14: Update WidgetPreview for new sizes
- **ACTION**: Update `WidgetPreview` composable (lines 142-189) to adapt its preview to the selected widget size
- **IMPLEMENT**:
  ```kotlin
  @Composable
  private fun WidgetPreview(prefs: WidgetPreferences) {
      val bgColor = Color(prefs.backgroundColor.argb).copy(alpha = prefs.backgroundAlpha)
      val textColor = if (prefs.backgroundColor.argb == 0xFFFFFFFF.toLong()) Color.Black else Color.White
      val accentColor = Color(prefs.accentColor.argb)
      val textSizeSp = if (prefs.widgetSize == WidgetSize.BANNER) {
          WidgetTextSize.SMALL.sp.sp
      } else {
          prefs.textSize.sp.sp
      }

      Box(
          modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(prefs.cornerRadius.dp))
              .background(bgColor)
              .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(prefs.cornerRadius.dp))
              .padding(12.dp),
      ) {
          when (prefs.widgetSize) {
              WidgetSize.WIDE -> WidePreviewContent(textColor, accentColor, textSizeSp)
              WidgetSize.BANNER -> BannerPreviewContent(textColor, accentColor, textSizeSp)
              else -> DefaultPreviewContent(prefs, textColor, accentColor, textSizeSp)
          }
      }
  }
  ```
  Extract existing preview content into `DefaultPreviewContent`, add `WidePreviewContent` (two columns side by side) and `BannerPreviewContent` (single row).
- **MIRROR**: WIDGET_PREVIEW — same mock data approach
- **IMPORTS**: None new
- **GOTCHA**: BANNER preview forces SMALL text size regardless of preference (matching actual widget behavior). Keep mock data consistent across all previews.
- **VALIDATE**: Preview adapts shape and content to selected size.

### Task 15: Disable carousel settings for WIDE and BANNER
- **ACTION**: In `WidgetSettingsScreen`, conditionally disable or gray out the Carousel section when WIDE or BANNER is selected
- **IMPLEMENT**: Wrap the carousel section (lines 110-128) with a visibility check:
  ```kotlin
  val showCarousel = prefs.widgetSize == WidgetSize.SMALL || prefs.widgetSize == WidgetSize.MEDIUM

  if (showCarousel) {
      Spacer(modifier = Modifier.height(24.dp))
      SectionHeader("Carousel")
      WidgetPage.entries.forEach { page ->
          // ... existing page toggle checkboxes
      }
      Spacer(modifier = Modifier.height(8.dp))
      AutoAdvanceSelector(prefs.autoAdvanceSeconds) { viewModel.setAutoAdvance(it) }
  }
  ```
  Also disable text size selector for BANNER (forced to SMALL):
  ```kotlin
  if (prefs.widgetSize != WidgetSize.BANNER) {
      TextSizeSelector(prefs.textSize) { viewModel.setTextSize(it) }
      Spacer(modifier = Modifier.height(12.dp))
  }
  ```
- **MIRROR**: Existing conditional UI patterns in the settings screen
- **IMPORTS**: None
- **GOTCHA**: When switching from WIDE/BANNER back to SMALL/MEDIUM, carousel settings reappear with their previous values (persisted in DataStore — not reset). Text size also retains its value, just becomes visible again.
- **VALIDATE**: Carousel section hidden for WIDE/BANNER. Text size hidden for BANNER. Both reappear when switching back to SMALL/MEDIUM.

---

## Testing Strategy

### Unit Tests

No dedicated Glance widget unit test infrastructure exists in this project. Validation relies on build verification and manual testing.

| Test | Method | Expected Output |
|---|---|---|
| New enum values serialize/deserialize | Manual: set WIDE in settings, kill app, reopen | Size preference persists as WIDE |
| Old DataStore values still work | No migration needed | `runCatching { WidgetSize.valueOf("SMALL") }` still returns SMALL |
| Unknown DataStore value falls back to default | Clear app data, set invalid string | Falls back to SMALL (existing `getOrNull() ?: defaults` handles this) |

### Edge Cases Checklist
- [ ] Existing SMALL/MEDIUM widgets unaffected by code changes
- [ ] BANNER forces SMALL text even when preference is LARGE
- [ ] WIDE shows both pages even when only CONNECTION page is enabled in carousel settings
- [ ] Widget picker shows three distinct widget entries with correct descriptions
- [ ] Switching between sizes in settings and tapping Apply updates the widget
- [ ] Very long SSID truncated with ellipsis in BANNER
- [ ] VPN off state shows dimmed indicator in all sizes
- [ ] No network connection gracefully shows "—" in all sizes

---

## Validation Commands

### Static Analysis
```bash
./gradlew :widget:compileDebugKotlin
```
EXPECT: Zero compilation errors

### Feature Module
```bash
./gradlew :feature:widgetsettings:compileDebugKotlin
```
EXPECT: Zero compilation errors

### Full Build
```bash
./gradlew assembleDebug
```
EXPECT: APK builds successfully

### Manifest Validation
```bash
./gradlew :app:processDebugManifest
```
EXPECT: No manifest merge errors

### Manual Validation
- [ ] Install debug APK on device
- [ ] Open widget picker — verify three NetLens widgets appear (default, wide, banner)
- [ ] Add WIDE widget to home screen — verify 4×2 initial size
- [ ] Verify WIDE shows Connection and Network pages side by side with vertical divider
- [ ] Verify WIDE has no carousel arrows or page dots
- [ ] Add BANNER widget to home screen — verify 5×1 initial size
- [ ] Verify BANNER shows single row with flag, VPN, IP, SSID, gateway
- [ ] Verify BANNER text is SMALL regardless of settings
- [ ] Verify BANNER tap opens the app
- [ ] Open Widget Settings screen
- [ ] Verify 2×2 grid of size cards (Compact, Full, Wide, Banner)
- [ ] Select each size — verify preview updates to match
- [ ] Verify Carousel section hides for WIDE and BANNER
- [ ] Verify Text size hides for BANNER
- [ ] Tap "Apply to Widget" — verify widget updates
- [ ] Verify existing SMALL widget still works with carousel
- [ ] Verify existing MEDIUM widget still works with nav arrows
- [ ] Rotate device — verify WIDE/BANNER layout stable

---

## Acceptance Criteria
- [ ] All tasks completed
- [ ] All validation commands pass
- [ ] WIDE widget renders both pages side by side with divider
- [ ] BANNER widget renders single dense row
- [ ] BANNER forces SMALL text size
- [ ] WIDE and BANNER skip carousel/page indicators
- [ ] Widget picker shows 3 widget entries
- [ ] Settings screen has 2×2 card grid for size selection
- [ ] Settings preview adapts to selected size
- [ ] Carousel settings hidden for WIDE/BANNER
- [ ] Existing SMALL/MEDIUM behavior unchanged

## Completion Checklist
- [ ] Code follows Glance composable patterns (GlanceModifier, ColorProvider, TextStyle)
- [ ] New receivers match existing receiver structure
- [ ] XML files match existing appwidget-provider format
- [ ] Strings follow existing naming convention (`widget_*_description`)
- [ ] No hardcoded text in composables (use string resources where appropriate)
- [ ] DataStore compatibility maintained (no migration, runCatching handles new values)
- [ ] No unnecessary scope additions
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Glance `Box` divider doesn't render on all launchers | Low | Visual — no divider between pages | Use `Text("|")` as fallback separator |
| Some launchers don't show multiple widgets from same app | Low | User can't access WIDE/BANNER from picker | Settings screen still allows switching size |
| Global preferences affect all widget instances | Medium | User with Small + Wide widgets sees wrong layout on one | Document as known limitation; per-instance prefs are out of scope |
| `targetCellWidth/Height` ignored on API < 31 | Expected | Widget initially wrong size on older devices | `minWidth/minHeight` dp values provide reasonable fallback; user can resize |
| Glance Row `defaultWeight` doesn't distribute space evenly | Low | WIDE layout unbalanced | Test on multiple screen sizes; use explicit `fillMaxWidth(0.5f)` if needed |

## Notes
- The existing `widget_small.xml` and `widget_medium.xml` exist in `res/xml/` but are NOT referenced in the manifest. Only `netlens_widget_info.xml` is used by the current receiver. The new `widget_wide.xml` and `widget_banner.xml` will be the first size-specific XMLs actually registered. Consider updating the primary receiver to use `widget_small.xml` or `widget_medium.xml` in a follow-up cleanup.
- `refreshAllWidgets()` calls `NetLensWidget().updateAll(context)`, which updates ALL instances of `NetLensWidget` regardless of which receiver created them. No changes needed for refresh.
- The `WidgetSettingsViewModel.setWidgetSize()` (line 34) already handles any `WidgetSize` value — no ViewModel changes needed.
- `WidgetPreferencesRepository.toWidgetPreferences()` uses `runCatching { WidgetSize.valueOf(it) }.getOrNull() ?: defaults.widgetSize` — new enum values are automatically deserialized. No migration.
