# Plan: UI Polish

## Summary
Add light theme support, consistent empty/loading/error states, and basic tablet/landscape handling across all 13 feature screens. The navigation overhaul (dashboard home replacing 13-item bottom nav) is handled separately.

## User Story
As a NetLens user, I want a polished, consistent UI with light and dark themes, so that the app feels professional and is comfortable to use in any lighting condition.

## Problem → Solution
Dark-only theme with inconsistent empty/loading/error states → Full light+dark Material 3 theming with consistent UX patterns across all screens.

## Metadata
- **Complexity**: Large
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: ~20

---

## UX Design

### Before
- Dark theme only (hardcoded `DarkColorScheme` fallback)
- No light color scheme defined
- Inconsistent empty states (some screens show nothing, some show text)
- Loading/error patterns vary per screen

### After
- Light + dark theme with system-following default
- Dynamic color on Android 12+, curated fallback on older
- Consistent empty state component with icon + message + optional action
- Consistent loading/error components reused across screens

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `app/src/main/kotlin/us/beary/netlens/ui/theme/Theme.kt` | all | Current theme setup |
| P0 | `app/src/main/kotlin/us/beary/netlens/ui/theme/Color.kt` | all | Current color palette |
| P1 | `feature/ipinfo/src/main/kotlin/us/beary/netlens/feature/ipinfo/IpInfoScreen.kt` | 96-119 | Loading/error/success pattern |
| P1 | `feature/lanscan/src/main/kotlin/us/beary/netlens/feature/lanscan/LanScanScreen.kt` | 150-200 | List with empty state gap |
| P2 | `feature/ping/src/main/kotlin/us/beary/netlens/feature/ping/PingScreen.kt` | all | Screen without explicit empty state |

---

## Patterns to Mirror

### THEME_SETUP
// SOURCE: app/src/main/kotlin/us/beary/netlens/ui/theme/Theme.kt:24-38
```kotlin
@Composable
fun NetLensTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicDarkColorScheme(LocalContext.current)
        else -> DarkColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

### ERROR_STATE
// SOURCE: feature/ipinfo/src/main/kotlin/us/beary/netlens/feature/ipinfo/IpInfoScreen.kt:121-149
```kotlin
@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Retry")
            }
        }
    }
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `app/.../ui/theme/Color.kt` | UPDATE | Add light color palette |
| `app/.../ui/theme/Theme.kt` | UPDATE | Add LightColorScheme, support isSystemInDarkTheme |
| `app/.../ui/theme/Type.kt` | CREATE | Custom typography scale |
| `app/.../ui/components/EmptyState.kt` | CREATE | Reusable empty state composable |
| `app/.../ui/components/LoadingState.kt` | CREATE | Reusable loading composable |
| `app/.../ui/components/ErrorState.kt` | CREATE | Reusable error composable |
| 13 feature Screen.kt files | UPDATE | Use shared empty/loading/error components |

## NOT Building

- Custom animations or transitions
- Tablet-specific multi-pane layouts (just ensure no breakage)
- Custom font files (use Material 3 defaults with configured scale)
- Settings screen for theme toggle (follow system only)

---

## Step-by-Step Tasks

### Task 1: Add Light Color Scheme
- **ACTION**: Update Color.kt with light palette colors, update Theme.kt to support light+dark
- **IMPLEMENT**: Add `LightColorScheme` using `lightColorScheme()`, add `isSystemInDarkTheme()` to theme selector
- **MIRROR**: THEME_SETUP pattern
- **IMPORTS**: `lightColorScheme`, `dynamicLightColorScheme`, `isSystemInDarkTheme`
- **GOTCHA**: Keep existing dark colors unchanged; dynamic color already handles light on 12+
- **VALIDATE**: App follows system theme setting

### Task 2: Create Shared UI Components
- **ACTION**: Create EmptyState, LoadingState, ErrorState composables in app module
- **IMPLEMENT**: EmptyState(icon, title, subtitle, action), LoadingState(), ErrorState(message, onRetry)
- **MIRROR**: ERROR_STATE pattern from IpInfoScreen
- **GOTCHA**: These live in app module since feature modules can't depend on each other. Consider core:ui module if needed.
- **VALIDATE**: Components render correctly in both themes

### Task 3: Apply Consistent States to Feature Screens
- **ACTION**: Replace ad-hoc empty/loading/error UI in each screen with shared components
- **IMPLEMENT**: Audit each screen, replace inline implementations
- **MIRROR**: IpInfoScreen pattern
- **GOTCHA**: Some screens (Ping, Traceroute) don't have loading states — only add where it makes sense
- **VALIDATE**: Each screen shows appropriate empty/loading/error state

### Task 4: Tablet/Landscape Safety
- **ACTION**: Test all screens in landscape, fix any overflow or clipping
- **IMPLEMENT**: Add `horizontalScroll` or adjust layouts where needed
- **GOTCHA**: Don't add complexity — just prevent breakage
- **VALIDATE**: No clipping or overflow in landscape on a tablet-size emulator

---

## Validation Commands

### Build
```bash
./gradlew :app:compileDebugKotlin
```
EXPECT: BUILD SUCCESSFUL, zero warnings from our code

### Manual Validation
- [ ] App follows system light/dark theme
- [ ] Dynamic color works on Android 12+ emulator
- [ ] Fallback light theme looks good on older API
- [ ] Fallback dark theme unchanged
- [ ] Empty states show in: LAN Scan, mDNS, Port Scan, Monitor, Net Log
- [ ] Loading states consistent across screens
- [ ] Error states consistent with retry button
- [ ] No landscape overflow on any screen

---

## Acceptance Criteria
- [ ] Light + dark theme with system following
- [ ] Shared empty/loading/error components created
- [ ] At least 5 screens updated to use shared components
- [ ] No visual regressions in dark theme
- [ ] Landscape doesn't break

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Shared components in app module creates dependency issue | Medium | Medium | Move to core:ui if feature modules need direct access |
| Light theme colors look bad with existing card styles | Low | Medium | Test with dynamic color first, curate fallback carefully |
