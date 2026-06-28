# Design System — NetLens

Source-of-truth reference for visual decisions in the NetLens Android app. Read this
before changing UI in any feature module. Conventions described here are derived
from the existing code (`app/.../ui/theme/`) and observed usage across the 22
feature modules; the design system is intentionally compact and Material 3 native.

## Product Context

- **What:** Android network diagnostics toolkit (`com.ventouxlabs.netlens`) — 20 tools, each in its own feature module.
- **Surface type:** Information-dense utility app. Lots of tabular and code-like data (IPs, MACs, ports, TTLs, hex), short-lived task screens, and a few persistent monitors.
- **Users:** Network admins, security engineers, hobbyists. Expect data legibility over decoration.
- **Platform:** Material 3, compileSdk 35, minSdk 29. Dark theme is a first-class peer of light.

## Foundations

### Typography

Two families, both bundled as TTF in `app/src/main/res/font/`:

- **Inter** — UI text. Regular, Medium, SemiBold, Bold.
- **JetBrains Mono** — technical data only (IPs, ports, MACs, hex, timestamps). Regular, Medium.

Defined in `app/src/main/kotlin/com/ventouxlabs/netlens/ui/theme/Type.kt` as
`InterFontFamily` and `MonoFontFamily`. The Material 3 `Typography` object overrides
all 12 roles; **`labelSmall` is the only role that uses `MonoFontFamily`** — it is
the canonical vector for monospace text in this app.

| Role            | Family | Size | Weight   | Line height | Letter spacing | When to use |
|-----------------|--------|------|----------|-------------|----------------|-------------|
| `displayLarge`  | Inter  | 34   | Bold     | 40          | -0.25          | Unused. Reserved for marketing or empty-state hero. |
| `headlineLarge` | Inter  | 28   | SemiBold | 34          | 0              | Rarely used (1 site). |
| `headlineMedium`| Inter  | 24   | SemiBold | 30          | 0              | Rare (2 sites). |
| `titleLarge`    | Inter  | 20   | SemiBold | 26          | 0              | Screen-level titles (rare; TopAppBar handles most). |
| `titleMedium`   | Inter  | 15   | Medium   | 22          | 0.1            | Section headers within a screen. |
| `titleSmall`    | Inter  | 13   | SemiBold | 18          | 0.5            | Subsection / card headers. |
| `bodyLarge`     | Inter  | 16   | Normal   | 24          | 0.15           | Rare; reserve for primary body when emphasis matters. |
| `bodyMedium`    | Inter  | 14   | Normal   | 20          | 0.25           | Default body text. |
| `bodySmall`     | Inter  | 12   | Normal   | 16          | 0.4            | Dense secondary text, helper copy. |
| `labelLarge`    | Inter  | 14   | Medium   | 20          | 0.1            | Primary button text. |
| `labelMedium`   | Inter  | 12   | Medium   | 16          | 0.5            | Chip / dense label text. |
| `labelSmall`    | **Mono** | 11 | Normal   | 16          | 0.5            | **All technical data**: IPs, MACs, ports, TTLs, hex, latency ms. |

**Rules:**
- Reach for `MaterialTheme.typography.<role>` — do not construct `TextStyle(...)` inline. Exceptions: chart/graph labels that need sizes outside the scale (e.g. 9sp axis ticks in `feature/wifi/ui/ChannelGraph.kt`).
- Any IP, port, MAC, hex, or other machine-readable value renders in `labelSmall` (Mono). Don't pull `MonoFontFamily` in directly.
- The five high-end roles (`displayLarge`, `headlineLarge/Medium`, `titleLarge`, `bodyLarge`) are nearly unused. Don't introduce them without a reason.

### Color

Defined in `app/.../ui/theme/Color.kt`, assembled into `LightColorScheme` and
`DarkColorScheme` in `Theme.kt`. Dynamic color (Material You) is enabled on API 31+
when the device opts in; otherwise the static palette below applies.

**Brand palette:** Teal + Cyan. Network/diagnostics-coded; reads as "infrastructure"
rather than "consumer."

| Material role           | Light hex | Dark hex  | Token name             |
|-------------------------|-----------|-----------|------------------------|
| `primary`               | `#00796B` | `#80CBC4` | `Teal700` / `Teal200`  |
| `primaryContainer` etc. | `#00695C` | `#009688` | `Teal800` / `Teal500`  |
| `secondary`             | `#006064` | `#80DEEA` | `Cyan900` / `Cyan200`  |
| `tertiary`              | —         | `#00BCD4` | `Cyan500`              |
| `background`            | `#F8FAFA` | `#0F1318` | `LightBackground` / `DarkBackground` |
| `surface`               | `#FFFFFF` | `#1A1F27` | `LightSurface` / `DarkSurface` |
| `surfaceVariant`        | `#DAE5E3` | `#252B35` | `LightSurfaceVariant` / `DarkSurfaceVariant` |
| `onSurface`             | `#191C1C` | `#E1E3E6` | `OnLightSurface` / `OnDarkSurface` |
| `onSurfaceVariant`      | `#3F4948` | `#9BA1AB` | `OnLightSurfaceVariant` / `OnDarkSurfaceVariant` |

**Status colors:** Defined in `:core:ui` as a `StatusColors` palette, provided
via `LocalStatusColors` from `NetLensTheme`. Read in any composable as
`LocalStatusColors.current.<token>`:

| Token   | Hex          | Use                                                     |
|---------|--------------|---------------------------------------------------------|
| `pass`  | `#FF4CAF50`  | Open ports, clean reputation, safe posture, healthy VPN |
| `warn`  | `#FFF59E0B`  | Medium-risk findings, advisory warnings                 |
| `fail`  | `#FFEF4444`  | Critical findings, leaks, failures                      |
| `info`  | `#FF3B82F6`  | Informational, low-risk findings                        |
| `muted` | `#FF9E9E9E`  | Closed / inactive / disabled state                      |

The Posture and VpnStatus screens use a deliberately deeper variant of these
colors (`#388E3C` / `#F57C00` / `#D32F2F`) for the VPN lock indicator — the
darker palette reads as more "serious" on security-coded UI. Those values stay
inline with a comment explaining the exception. Don't replace them with the
brighter `LocalStatusColors` defaults without a design conversation first.

### Spacing

Defined in `:core:ui` as `com.ventouxlabs.netlens.core.ui.Spacing`. Import and use
directly: `Spacing.lg`, `Spacing.sm`, etc.

| Token   | Value | Use |
|---------|-------|-----|
| `xs`    | 4dp   | Tight gaps inside chips, dense rows |
| `sm`    | 8dp   | Default padding step |
| `md`    | 12dp  | Medium gaps, card inner padding |
| `lg`    | 16dp  | Card padding, section gaps |
| `xl`    | 24dp  | Between major sections |
| `xxl`   | 32dp  | Screen-edge padding when generous |

**Rule:** Stick to the scale. Outliers (2, 6, 18, 64, 80, 100dp) may stay inline
when the layout has a documented reason — leave a one-line comment at the call
site if you have to break the scale. The scale is small on purpose; don't grow
it without removing something else.

The existing ~300 inline `.dp` call sites have not been mass-migrated; adopt
the scale in new code and convert opportunistically.

### Shape

No custom `Shapes` object. The Material 3 default shape scale is in effect:
- `extraSmall` 4dp, `small` 8dp, `medium` 12dp, `large` 16dp, `extraLarge` 28dp.

Most `Card` and `ElevatedCard` usages take the default `medium` rounding.

### Motion

No custom motion tokens. Use Material 3 defaults (Compose `MotionTokens` /
`AnimationSpec`). Keep transitions short on data screens — these are utility
flows, not marketing.

### Icons

Material Icons (`androidx.compose.material.icons.*`). Default size (24dp implicit)
is used throughout — no explicit `Modifier.size()` drift observed. Don't introduce
custom sizes without reason.

## Component Patterns

### Cards and surfaces

Three surface patterns are in use today:

1. **`ElevatedCard`** — primary content containers on the home screen and inside features. Default elevation.
2. **`Card`** — chips, detail rows, and inline groupings. Lower visual weight than `ElevatedCard`.
3. **`Modifier.background(...)`** — status indicators and color-coded inline pills, usually with a custom hex from the Status colors table above.

Reach for `ElevatedCard` for primary content blocks; `Card` for inline groupings;
`background` only for status pills.

### Pro-gating (UI affordance)

Pro-only affordances (Share/Export buttons, certain visualisations) are gated via
`LocalProStatus.current.isPro`. Three patterns coexist (see CLAUDE.md → "Pro-gating
patterns" for the full breakdown):

1. Direct `if (isPro) { IconButton(...) }` in the screen — most common.
2. Nullable callback (`onShareResults: (() -> Unit)?`) — for screens with extracted `Content` composables.
3. Boolean parameter passed to inner composables — for non-button affordances (e.g. the `ChannelGraph` Pro-only overlay).

Choose the pattern that matches the screen's existing architecture. New features
should default to pattern 1.

### TopAppBar

Every feature screen wraps its content in a `Scaffold` with a Material 3
`TopAppBar`. Share/Copy actions (calling `ResultExporter.shareAsText` /
`ResultExporter.copyToClipboard` from `core:network`) are the standard
top-right `IconButton`s on result-producing tools.

## Resources

### Fonts

Bundled TTFs in `app/src/main/res/font/`. Loaded once into `InterFontFamily` and
`MonoFontFamily` in `Type.kt`. No network fonts, no Google Fonts CDN.

### Strings

User-facing strings live in `res/values/strings.xml` per feature module. **Do not
hardcode user-visible text in composables.** Internal labels (debug/log/inline
constants) may be inline at author discretion.

## Decision Log

| Date       | Decision                                                                                             | Source / Rationale |
|------------|------------------------------------------------------------------------------------------------------|--------------------|
| 2026-05-18 | Extracted `StatusColors` (+ `LocalStatusColors`) and `Spacing` scale into new `:core:ui` module.       | Audit found 50 inline color literals and ~300 inline `.dp` values; consolidation gives one source of truth and theme-aware tokens. Posture/VpnStatus deep-palette deliberately kept inline. |
| 2026-05-17 | Documented existing design system at v1.1.1 baseline.                                                | This file. Captures Theme.kt, Type.kt, Color.kt as currently shipped. |
| pre-2026-05| Inter + JetBrains Mono adopted; `labelSmall` redefined to use Mono for technical data.               | "Billing and typography shipped" milestone; CLAUDE.md → Typography. |
| pre-2026-05| Teal/Cyan brand palette chosen; Material You dynamic color enabled API 31+.                          | `Theme.kt`. |
| pre-2026-05| No central spacing/shape/status tokens — features use inline dp and hex.                             | Observed across 22 feature modules. Candidate for consolidation (see audit). |
