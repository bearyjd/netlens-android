# Code Review: Widget Redesign + Devil's Advocate Fixes

**Reviewed**: 2026-04-25
**Branch**: master (uncommitted)
**Decision**: APPROVE with comments

## Summary

Three-size widget redesign with 10 post-review fixes correctly applied. One remaining behavioral issue: footer "rescan" tap opens app home instead of triggering a background refresh.

## Findings

### CRITICAL

None

### HIGH

**1. Footer rescan tap uses wrong action — StandardWidgetContent:206, DashboardWidgetContent:327**

Both `FooterRow` and `DashboardFooter` fire `OpenDeeplinkAction` with `Deeplink.TRIGGER_SCAN` URI. This opens the app and resolves to "home" via DeepLinkRouter. The intended behavior is a background widget refresh with no Activity launch.

`TriggerScanAction` (which correctly calls `enqueueWidgetRefresh`) exists but is never wired into any widget composable.

Fix: Replace `actionRunCallback<OpenDeeplinkAction>(actionParametersOf(DeeplinkUriKey to Deeplink.TRIGGER_SCAN))` with `actionRunCallback<TriggerScanAction>()` in both footer composables.

### MEDIUM

**2. WidgetState.isStale uses System.currentTimeMillis() directly — WidgetState.kt:33**

Same testability issue that was fixed for `relativeTime()`. Not blocking since `isStale` is a UI hint, but worth aligning with the same `now` parameter pattern for consistency if WidgetState tests are added later.

### LOW

**3. IpRow composable duplicated between StandardWidgetContent and DashboardWidgetContent**

Near-identical IP row logic in both files. Acceptable for Glance widgets where shared composables across widget sizes can be fragile, but worth noting.

## Validation Results

| Check | Result |
|---|---|
| Build (assembleDebug) | Pass |
| Widget tests (:widget:testDebugUnitTest) | Pass — 7 tests |
| App tests (:app:testDebugUnitTest) | Pass — 9 tests |
| CI modules (core:network, lanscan, whois, monitor) | Pass |

## Files Reviewed

### Modified (13)
- `app/src/main/AndroidManifest.xml` — BROWSABLE comment, receiver fixes
- `app/src/main/kotlin/.../MainActivity.kt` — pendingRoute state + onNewIntent
- `app/src/main/kotlin/.../ui/NetLensApp.kt` — LaunchedEffect(pendingRoute)
- `feature/widgetsettings/.../WidgetSettingsViewModel.kt` — refreshAllWidgets call
- `widget/build.gradle.kts` — JUnit 5 test deps
- `widget/.../BannerWidgetReceiver.kt` — shared callback
- `widget/.../NetLensWidget.kt` — Deeplink.DEVICES reference
- `widget/.../NetLensWidgetReceiver.kt` — shared callback
- `widget/.../WideWidgetReceiver.kt` — shared callback
- `widget/.../WidgetRefresh.kt` — extracted callback helpers, removed dead code
- `widget/.../WidgetRefreshWorker.kt` — CancellationException rethrow
- `widget/.../action/TriggerScanAction.kt` — enqueueWidgetRefresh directly
- `widget/.../util/Deeplink.kt` — extended constants

### New (18)
- `app/.../navigation/DeepLinkRouter.kt` — deep link routing with fallbacks
- `app/src/test/.../DeepLinkRouterTest.kt` — 9 tests
- `widget/.../WidgetState.kt` — widget state data class
- `widget/.../WidgetStateDefinition.kt` — DataStore GlanceStateDefinition
- `widget/.../ui/WidgetTheme.kt` — score colors, relative time
- `widget/.../ui/CompactWidgetContent.kt` — 2x1 widget
- `widget/.../ui/StandardWidgetContent.kt` — 2x2 widget
- `widget/.../ui/DashboardWidgetContent.kt` — 4x2 widget
- `widget/.../CompactWidget.kt` — GlanceAppWidget
- `widget/.../StandardWidget.kt` — GlanceAppWidget
- `widget/.../DashboardWidget.kt` — GlanceAppWidget
- `widget/.../CompactWidgetReceiver.kt` — receiver with callback
- `widget/.../StandardWidgetReceiver.kt` — receiver with callback
- `widget/.../DashboardWidgetReceiver.kt` — receiver with callback
- `widget/src/main/res/xml/widget_compact_info.xml`
- `widget/src/main/res/xml/widget_standard_info.xml`
- `widget/src/main/res/xml/widget_dashboard_info.xml`
- `widget/src/test/.../ui/WidgetThemeTest.kt` — 7 tests

### Deleted (1)
- `.claude/PRPs/plans/lanscan-host-detail.plan.md` — archived plan
