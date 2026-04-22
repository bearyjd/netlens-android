# Plan: Navigation Redesign

## Summary

Replace the 6-item bottom navigation bar with a dashboard grid home screen that groups all features into categories. Features navigate to their individual screens from the dashboard, eliminating the bottom bar limitation.

## User Story

As a user, I want a dashboard home screen that organizes all network tools into logical categories, so that I can quickly find and access any tool without being limited to a small bottom navigation bar.

## Metadata

- **Complexity**: Medium
- **Branch**: feat/nav-redesign
- **PR**: PR-13
- **Depends On**: PR-01 through PR-12
- **Estimated Files**: 8
- **New Modules**: none

## Patterns to Mirror

### NAVIGATION
// SOURCE: app/src/main/kotlin/us/beary/netlens/navigation/NetLensNavHost.kt
- Replace PlaceholderScreen loop with explicit composable() blocks

### SCAFFOLD
// SOURCE: app/src/main/kotlin/us/beary/netlens/ui/NetLensApp.kt
- Remove NavigationBar from Scaffold, add TopAppBar + back navigation

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `app/src/main/kotlin/us/beary/netlens/navigation/TopLevelDestination.kt` | DELETE/REPLACE | Remove enum. Replace with FeatureRoute sealed interface or object routes |
| `app/src/main/kotlin/us/beary/netlens/navigation/FeatureCategory.kt` | CREATE | Enum: NetworkInfo, Scan, Tools, Monitor — each with label, icon, list of feature routes |
| `app/src/main/kotlin/us/beary/netlens/navigation/FeatureRoute.kt` | CREATE | Sealed interface / object per feature with route string, icon, label, category |
| `app/src/main/kotlin/us/beary/netlens/navigation/NetLensNavHost.kt` | UPDATE | Replace enum iteration with explicit composable() per feature route. Add "dashboard" as startDestination. Add back-navigation support. |
| `app/src/main/kotlin/us/beary/netlens/ui/NetLensApp.kt` | UPDATE | Remove bottom NavigationBar. Add TopAppBar with title + back arrow (when not on dashboard). Pass navController for back navigation. |
| `app/src/main/kotlin/us/beary/netlens/ui/DashboardScreen.kt` | CREATE | Grid of category cards. Each category is an expandable section or card containing feature icons. Categories: Network Info (IP Info, WHOIS, DNS, TLS), Scan (LAN, Port, mDNS, Ping), Tools (WoL, HTTP Request), Monitor (Network Log, Uptime, Widget Config). |
| `app/src/main/kotlin/us/beary/netlens/ui/CategoryCard.kt` | CREATE | Composable card for a category: title, icon, grid of feature chips inside |
| `app/src/main/kotlin/us/beary/netlens/ui/FeatureChip.kt` | CREATE | Composable chip/button for a single feature: icon + label, onClick navigates to route |

## Step-by-Step Tasks

### Task 1: Create FeatureRoute definitions
- **ACTION**: Create `FeatureRoute` as sealed interface with object per feature: `object IpInfo : FeatureRoute`, `object LanScan`, `object PortScan`, `object Dns`, `object Ping`, `object Wol`, `object TlsInspect`, `object Whois`, `object HttpRequest`, `object Mdns`, `object NetworkLog`, `object Uptime`. Each has `val route: String`, `val icon: ImageVector`, `val label: String`.
- **VALIDATE**: Compiles

### Task 2: Create FeatureCategory
- **ACTION**: `enum class FeatureCategory(val label: String, val icon: ImageVector, val features: List<FeatureRoute>)` with: `NetworkInfo("Network Info", Icons.Default.Language, listOf(IpInfo, Whois, Dns, TlsInspect))`, `Scan("Scan", Icons.Default.Router, listOf(LanScan, PortScan, Mdns, Ping))`, `Tools("Tools", Icons.Default.Build, listOf(Wol, HttpRequest))`, `Monitor("Monitor", Icons.Default.Monitor, listOf(NetworkLog, Uptime))`.
- **VALIDATE**: Compiles

### Task 3: Remove TopLevelDestination enum
- **ACTION**: Delete `TopLevelDestination.kt` (or repurpose as FeatureRoute)
- **VALIDATE**: Fix all compile errors

### Task 4: Create DashboardScreen
- **ACTION**: LazyVerticalGrid or Column of CategoryCards. Each CategoryCard is a Material3 ElevatedCard with category title + icon, containing a FlowRow of FeatureChips. Tapping a chip navigates to `navController.navigate(feature.route)`. Use intentional spacing and hierarchy — not a uniform grid.
- **VALIDATE**: Preview renders

### Task 5: Create CategoryCard + FeatureChip
- **ACTION**: `CategoryCard` shows category label, icon, contains FlowRow of chips. `FeatureChip` is a FilledTonalButton or AssistChip with feature icon + label.
- **VALIDATE**: Preview renders

### Task 6: Update NetLensApp
- **ACTION**: Remove `NavigationBar` from Scaffold bottomBar. Add `TopAppBar` with dynamic title (dashboard title or feature name) and back arrow when not on dashboard route. Use `navController.previousBackStackEntry` to detect back availability.
- **VALIDATE**: Compiles

### Task 7: Update NetLensNavHost
- **ACTION**: Set `startDestination = "dashboard"`. Add `composable("dashboard") { DashboardScreen(onFeatureClick = { navController.navigate(it.route) }) }`. Add explicit `composable(FeatureRoute.IpInfo.route) { IpInfoScreen() }` for each feature. Remove old PlaceholderScreen loop.
- **VALIDATE**: `./gradlew assembleDebug`

### Task 8: Clean up unused imports
- **ACTION**: Remove references to TopLevelDestination across the codebase
- **VALIDATE**: `./gradlew assembleDebug` — zero warnings from removed references

## Testing Strategy

- **Unit tests for**:
  - FeatureCategory → features mapping correctness
  - All features accounted for (no orphan routes)
- **Integration tests for**:
  - Navigation from dashboard to each feature and back
- **E2E tests for**:
  - Full navigation flow: launch → dashboard → each category → feature → back

## Validation
