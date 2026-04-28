# Plan: LAN Scan Device Card Cleanup

## Summary
Redesign the DeviceCard composable to handle long hostnames, organize services on the right, and remove visual clutter (always-shown "MAC unavailable" text, redundant chips).

## User Story
As a user scanning my LAN, I want device cards to be compact and readable even when hostnames are long UUIDs and multiple services are discovered, so that I can quickly identify devices without scrolling through bloated cards.

## Problem -> Solution
Cards are bloated and disorganized (long hostnames wrap 4+ lines, IP wraps, MAC always shows "unavailable", services disconnected at bottom) -> Compact two-column layout with truncated hostname, services on the right, conditional MAC display.

## Metadata
- **Complexity**: Small-Medium
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: 2

---

## UX Design

### Before
```
+---------------------------------------------+
| check  192.168.1.2                          |
|        11                                    |
|        onn.-Streaming-                       |
|        Devic-10e164d5                        |
|        8de7c09256650    [Chromecast] [Multi] |
|        a369c4f9acf      copy  >              |
|        MAC: unavailable                      |
|        (Android restricts                    |
|        ARP access)                           |
|        Android                               |
|                                              |
|        [googlecast] [spotify-connect]        |
+---------------------------------------------+
```

### After
```
+---------------------------------------------+
| check  192.168.1.211        Chromecast    >  |
|        onn.-Streaming-De..  googlecast       |
|        Android | Multi      spotify-connect  |
+---------------------------------------------+
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Hostname | Full text, wraps 4+ lines | maxLines=1, ellipsis | Full hostname in detail sheet |
| MAC | Always "MAC: unavailable" | Show only when macAddress != null | Removes noise |
| Device type | SuggestionChip in main Row | Text label, right-aligned | Less horizontal pressure |
| Discovery method | SuggestionChip in main Row | Appended to OS line as text | "Android . Multi" |
| Copy button | In card Row | Removed from card | Available in detail sheet |
| Services | FlowRow chips below card | Column of text labels, right-aligned | Compact, organized |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `feature/lanscan/.../LanScanScreen.kt` | 479-619 | Current DeviceCard composable |
| P0 | `feature/lanscan/.../model/LanDevice.kt` | 1-14 | Data model fields |
| P1 | `feature/lanscan/src/main/res/values/strings.xml` | 1-52 | String resources |

---

## Patterns to Mirror

### COMPOSABLE_CARD
// SOURCE: LanScanScreen.kt:421-465 (HistoryCard)
```kotlin
Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ),
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) { ... }
        Icon(chevron)
    }
}
```

### TEXT_STYLES
// SOURCE: LanScanScreen.kt:512-538
- IP: `titleMedium`, `FontWeight.Bold`
- Hostname: `bodySmall`, `onSurfaceVariant`
- OS: `labelSmall`, `tertiary`

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `feature/lanscan/.../LanScanScreen.kt` | UPDATE | Redesign DeviceCard composable |
| `feature/lanscan/src/main/res/values/strings.xml` | UPDATE | Add discovery separator string, update MAC strings |

## NOT Building

- New composables or files
- Changes to data model
- Changes to ViewModel logic
- Changes to HostDetailSheet
- Changes to HistoryCard

---

## Step-by-Step Tasks

### Task 1: Redesign DeviceCard layout
- **ACTION**: Replace the current single-Row layout with a two-column Row
- **IMPLEMENT**:
  - Left Column (weight 1f): IP (titleMedium bold), hostname (bodySmall, maxLines=1, ellipsis), OS + discovery method as single line
  - Right Column (width = IntrinsicSize.Min): device type label, services as text labels, aligned end
  - Keep: status icon (left), chevron (right end)
  - Remove: copy IconButton, discovery method SuggestionChip, device type SuggestionChip
  - Remove: separate FlowRow for services at bottom
- **MIRROR**: COMPOSABLE_CARD pattern
- **IMPORTS**: `import androidx.compose.ui.text.style.TextOverflow`
- **GOTCHA**: Must keep `maxLines = 1` on IP text too to prevent the wrap seen in screenshot. Use `TextOverflow.Ellipsis` on hostname.
- **VALIDATE**: Build succeeds, card renders compactly

### Task 2: Make MAC display conditional
- **ACTION**: Replace always-shown "MAC: unavailable" with conditional display
- **IMPLEMENT**: Only show MAC text when `device.macAddress != null`, using `lanscan_mac_label` format string. Show nothing when null.
- **GOTCHA**: The existing `lanscan_mac_unavailable` string can remain in strings.xml for now (no dead code removal needed)
- **VALIDATE**: Cards without MAC show no MAC line; cards with MAC show "MAC: AA:BB:CC"

### Task 3: Format OS + discovery as single line
- **ACTION**: Combine osGuess and discoveryMethod into one secondary text line
- **IMPLEMENT**: Build string like "Android . Multi" or just "PING" if no OS. Use middle dot (\\u00B7) as separator.
- **VALIDATE**: Secondary line shows combined info

### Task 4: Update strings.xml
- **ACTION**: Add separator string if needed
- **VALIDATE**: No hardcoded strings in composable

---

## Testing Strategy

### Manual Validation
- [ ] Device with long hostname shows truncated with ellipsis
- [ ] Device with no hostname shows IP only (no empty line)
- [ ] Device with services shows them right-aligned
- [ ] Device with no services shows clean compact card
- [ ] Device with MAC shows it; without MAC shows nothing
- [ ] Device with OS + discovery method shows combined line
- [ ] IP addresses never wrap across lines
- [ ] Cards are significantly shorter than before
- [ ] Tapping card still opens detail sheet

---

## Validation Commands

### Build
```bash
./gradlew :feature:lanscan:assembleDebug
```
EXPECT: Zero errors

### Install and test
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
EXPECT: Run LAN scan, verify card layout on device

---

## Acceptance Criteria
- [ ] No hostname wraps more than 1 line
- [ ] IP never wraps
- [ ] MAC line only shown when MAC is available
- [ ] Services displayed on the right side of the card
- [ ] Discovery method shown as text, not chip
- [ ] Copy button removed from card (exists in detail sheet)
- [ ] Cards are visually compact

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Right column too wide with long service names | Medium | Low | Use maxLines=1 + ellipsis on service text too |
| IntrinsicSize measurement performance on large lists | Low | Low | LazyColumn already handles this well |

## Notes
- The copy button exists in HostDetailSheet, so removing it from the card doesn't lose functionality
- Full hostname is visible in the detail sheet when tapping the card
- The "MAC: unavailable" text was always shown unconditionally (line 527) regardless of device.macAddress — this was a display bug
