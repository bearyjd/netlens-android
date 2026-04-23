# Plan: Feature Phase 4 — SSH Banner, WiFi QR, Speed Test

## Summary
Three post-launch features: SSH Banner Check (port 22 banner grab), WiFi QR Code generator (share WiFi via QR), and Speed Test (download/upload measurement via Cloudflare endpoints). Each is an independent feature module following the established convention plugin pattern.

## User Story
As a home lab user, I want SSH banner detection, WiFi sharing via QR, and speed testing, so that NetLens covers my most common network tasks without needing multiple apps.

## Problem → Solution
Missing utility features that competing apps offer → Three focused modules completing the toolkit.

## Metadata
- **Complexity**: Large (3 independent medium features)
- **Source PRD**: N/A
- **PRD Phase**: N/A
- **Estimated Files**: ~45 (15 per feature)

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `build-logic/.../AndroidFeatureConventionPlugin.kt` | all | Convention plugin pattern |
| P0 | `feature/portscan/build.gradle.kts` | all | Feature module build template |
| P0 | `feature/portscan/engine/PortScanner.kt` | all | Engine interface pattern |
| P0 | `feature/portscan/engine/PortScannerImpl.kt` | all | Socket-based engine impl |
| P1 | `feature/portscan/di/PortScanModule.kt` | all | Hilt DI @Binds pattern |
| P1 | `feature/portscan/PortScanViewModel.kt` | all | ViewModel with cancellable Job |
| P2 | `app/.../navigation/ToolDestination.kt` | all | Navigation enum registration |
| P2 | `gradle/libs.versions.toml` | all | Dependency management |

---

## Feature A: SSH Banner Check

### What
Connect to port 22, read the first line (SSH banner per RFC 4253), parse protocol/software version, display results. No SSH handshake, no key exchange, no authentication.

### Files (12)
| File | Action |
|------|--------|
| `feature/sshbanner/build.gradle.kts` | CREATE |
| `feature/sshbanner/src/main/AndroidManifest.xml` | CREATE |
| `feature/sshbanner/src/main/res/values/strings.xml` | CREATE |
| `feature/sshbanner/.../model/SshBannerResult.kt` | CREATE |
| `feature/sshbanner/.../model/SshBannerUiState.kt` | CREATE |
| `feature/sshbanner/.../engine/SshBannerChecker.kt` | CREATE |
| `feature/sshbanner/.../engine/SshBannerCheckerImpl.kt` | CREATE |
| `feature/sshbanner/.../di/SshBannerModule.kt` | CREATE |
| `feature/sshbanner/.../SshBannerViewModel.kt` | CREATE |
| `feature/sshbanner/.../SshBannerScreen.kt` | CREATE |
| `settings.gradle.kts` | UPDATE |
| `app/build.gradle.kts` | UPDATE |

### Key Implementation Details
- `SshBannerResult`: host, port, banner (raw), protocolVersion, softwareVersion, latencyMs, error
- Engine: `Socket` connect with timeout → `BufferedReader.readLine()` → parse `SSH-protoversion-softwareversion`
- **Do NOT send any data** — SSH servers send banner immediately on TCP connect
- Parse banner: split on `-` per RFC 4253 format
- Timeout: 5 seconds

### Tests
- Banner parsing for various formats: OpenSSH, Dropbear, old SSH-1.99, malformed
- ViewModel state transitions: idle → checking → result/error

---

## Feature B: WiFi QR Code

### What
Generate QR code encoding WiFi credentials (SSID + password + security type) in standard `WIFI:T:...;S:...;P:...;;` format. Manual input — no location permission required.

### New Dependency
- ZXing Core 3.5.3 (Apache 2.0, F-Droid compatible) — add to libs.versions.toml

### Files (12)
| File | Action |
|------|--------|
| `feature/wifiqr/build.gradle.kts` | CREATE |
| `feature/wifiqr/src/main/AndroidManifest.xml` | CREATE |
| `feature/wifiqr/src/main/res/values/strings.xml` | CREATE |
| `feature/wifiqr/.../model/WifiQrData.kt` | CREATE |
| `feature/wifiqr/.../model/WifiSecurity.kt` | CREATE |
| `feature/wifiqr/.../model/WifiQrUiState.kt` | CREATE |
| `feature/wifiqr/.../engine/QrCodeGenerator.kt` | CREATE |
| `feature/wifiqr/.../engine/QrCodeGeneratorImpl.kt` | CREATE |
| `feature/wifiqr/.../di/WifiQrModule.kt` | CREATE |
| `feature/wifiqr/.../WifiQrViewModel.kt` | CREATE |
| `feature/wifiqr/.../WifiQrScreen.kt` | CREATE |
| `gradle/libs.versions.toml` | UPDATE |

### Key Implementation Details
- `WifiQrData.toWifiString()`: format as `WIFI:T:<security>;S:<ssid>;P:<password>;H:<hidden>;;`
- Escape special chars in SSID/password: `\`, `;`, `:`, `"`
- QR generation: `MultiFormatWriter` → `BitMatrix` → `Bitmap`
- Screen: form inputs + generated QR image + share button (saves bitmap, shares via Intent)
- No location permission — manual SSID input only

### Tests
- WiFi string formatting for WPA/WEP/Open, special characters, hidden networks
- QR generation produces non-null bitmap of expected size

---

## Feature C: Speed Test

### What
Measure download/upload speed using Cloudflare's public speed test endpoints. Live progress gauge during test.

### Approach
Cloudflare endpoints (no library dependency, no licensing concern):
- Download: `GET https://speed.cloudflare.com/__down?bytes=25000000`
- Upload: `POST https://speed.cloudflare.com/__up`
- Ping: `HEAD https://speed.cloudflare.com/`

### Files (12)
| File | Action |
|------|--------|
| `feature/speedtest/build.gradle.kts` | CREATE |
| `feature/speedtest/src/main/AndroidManifest.xml` | CREATE |
| `feature/speedtest/src/main/res/values/strings.xml` | CREATE |
| `feature/speedtest/.../model/SpeedTestResult.kt` | CREATE |
| `feature/speedtest/.../model/TestPhase.kt` | CREATE |
| `feature/speedtest/.../model/SpeedTestUiState.kt` | CREATE |
| `feature/speedtest/.../engine/SpeedTester.kt` | CREATE |
| `feature/speedtest/.../engine/SpeedTesterImpl.kt` | CREATE |
| `feature/speedtest/.../di/SpeedTestModule.kt` | CREATE |
| `feature/speedtest/.../SpeedTestViewModel.kt` | CREATE |
| `feature/speedtest/.../SpeedTestScreen.kt` | CREATE |
| `settings.gradle.kts` | UPDATE |

### Key Implementation Details
- Download: 25MB GET, read in 8KB chunks, measure bytes/time, emit progress via Flow
- Upload: 10MB POST of random bytes, measure completion time
- Run 3 parallel download streams, take average
- Calculate Mbps: `(bytes * 8) / (elapsedSeconds * 1_000_000)`
- UI: Canvas-based arc gauge showing current speed, phase labels
- Timeout: 30 seconds per phase

### Tests
- Mbps calculation with known values
- ViewModel state machine: idle → ping → download → upload → complete
- Cancellation handling

---

## Navigation Registration (all 3 features)

Add to `ToolDestination.kt`:
```kotlin
SshBanner(route = "sshbanner", icon = Icons.Default.Code, label = "SSH Banner",
    description = "Check SSH server version", category = ToolCategory.Security),
WifiQr(route = "wifiqr", icon = Icons.Default.QrCode2, label = "WiFi QR",
    description = "Share WiFi via QR code", category = ToolCategory.Tools),
SpeedTest(route = "speedtest", icon = Icons.Default.Speed, label = "Speed Test",
    description = "Test connection speed", category = ToolCategory.Connectivity),
```

## NOT Building
- Full SSH client
- Auto-detection of current WiFi SSID (requires location permission)
- Self-hosted speed test server
- Speed test history/logging

---

## Validation Commands

```bash
# Build all three modules
./gradlew :feature:sshbanner:compileDebugKotlin :feature:wifiqr:compileDebugKotlin :feature:speedtest:compileDebugKotlin

# Run tests
./gradlew :feature:sshbanner:testDebugUnitTest :feature:wifiqr:testDebugUnitTest :feature:speedtest:testDebugUnitTest

# Full app build
./gradlew :app:assembleDebug
```

## Acceptance Criteria
- [ ] SSH Banner: connects to port 22, displays parsed banner, handles timeouts
- [ ] WiFi QR: generates scannable QR from manual input
- [ ] Speed Test: measures download/upload with live progress
- [ ] All three modules build successfully
- [ ] Unit tests pass for all three
- [ ] No regressions in existing features
- [ ] Navigation entries appear on home dashboard

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Cloudflare endpoints change | Medium | High | Abstract behind interface; add fallback servers |
| SSH server delayed banner | Low | Low | 5-second timeout with clear error |
| ZXing APK size (~550KB) | Low | Low | Acceptable for utility app |
| Android Bitmap in unit tests | Certain | Low | Test BitMatrix only, skip Bitmap in unit tests |
| Speed test accuracy on mobile | Medium | Medium | Multiple parallel streams, median not average |

## Implementation Priority
1. SSH Banner (simplest, immediate value, no new dependencies)
2. WiFi QR (one new dependency, straightforward)
3. Speed Test (most complex, network-dependent, Cloudflare risk)
