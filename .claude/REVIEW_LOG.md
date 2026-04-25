# Review Log

## Plan 3: feature/widget-redesign

### Round 1 Findings

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| 1 | HIGH | `NavArrow` dispatches on raw string value — fragile API contract | FIXED — replaced with `NavDirection` enum |
| 2 | HIGH | `provideGlance` reads preferences exactly once — undocumented snapshot-and-refresh contract | FIXED — added contract comment |
| 3 | HIGH | `SmallWidgetContent` fires `CarouselNextAction` when disconnected | FIXED — conditional click: carousel only when connected AND pages > 1 |
| 4 | MEDIUM | Hardcoded strings in Glance composables | NOTED — Glance limitation; would require context threading |
| 5 | MEDIUM | `textSizeSp` double `.sp` naming confusion | VERIFIED — `WidgetTextSize.sp` is Int property, `.sp` extension converts to TextUnit; correct |
| 6 | MEDIUM | Non-exhaustive `when` for `WidgetSize` will fail at Plan 4 | BY DESIGN — compile-time safety net |
| 7 | MEDIUM | `MediumWidgetContent` nav arrows shown for single-page widget | FIXED — conditional `showArrows = pages.size > 1` |
| 8 | MEDIUM | Disconnected state circle has no accessibility label | NOTED — Glance 1.1 lacks semantics modifier |
| 9 | MEDIUM | Color names derived from enum `.name` — not translatable | FIXED — extracted to string resources with exhaustive `when` |
| 10 | MEDIUM | `togglePage` uses mutable list — violates immutability convention | FIXED — uses `current.pages - page` / `+ page` |
| 11 | LOW | `SliderRow` label has hardcoded 100dp width | NOTED |
| 13 | LOW | Preview always highlights page 0 | NOTED — preview behavior, not a bug |
| 14 | LOW | `AutoAdvanceSelector` options list rebuilt on recomposition | NOTED — negligible cost |

### Round 2 Findings

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| 1 | HIGH | Carousel index not clamped after page removal — stale DataStore index | MITIGATED — `coerceIn` at render time guards against this; full cross-store reset deferred |
| 2 | HIGH | `CarouselAction` uses hardcoded DataStore key and `updateAll` instead of per-widget `update` | FIXED — uses `updateAppWidgetState(context, definition, glanceId)` and `update(context, glanceId)` |
| 3 | MEDIUM | `SettingsCard`/`ColorPickerRow`/`SliderRow` `titleRes`/`labelRes` lacks `@StringRes` | FIXED — added `@StringRes` annotations |

### MEDIUM Findings Fixed (post-review)

| # | Source | Finding | Status |
|---|--------|---------|--------|
| 1 | R1-4/R2-3 | Hardcoded strings in Glance composables not translatable | FIXED — added strings to widget strings.xml, use `LocalContext.current.getString()` |
| 2 | R1-11 | SliderRow label hardcoded 100dp width clips long translations | FIXED — changed to `Modifier.widthIn(min = 80.dp, max = 120.dp)` |
| 3 | R1-14 | AutoAdvanceSelector options list rebuilt on every recomposition | FIXED — extracted to top-level `AUTO_ADVANCE_OPTIONS` val |
| 4 | R2-1 | Stale carousel index after page removal | FIXED — `applyToWidget()` now calls `resetCarouselAndRefreshWidgets()` which resets index to 0 |
| 5 | R1-8 | DisconnectedContent circle has no accessibility label | NOT FIXABLE — Glance 1.1 lacks `semantics` modifier |
