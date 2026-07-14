# Changelog

All notable changes to NetLens will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.2.4] - 2026-07-13

### Fixed
- LAN Scan's Inventory tab now correctly accumulates devices that don't have a resolvable MAC address, instead of silently dropping them every scan. Devices found only via mDNS/SSDP, or absent from the on-device ARP table, previously never persisted to inventory no matter how many times they were seen; they're now tracked by IP and upgraded automatically once a MAC address resolves.

## [1.2.3] - 2026-07-12

### Fixed
- Build no longer requires an exact JDK 17 installation. A pinned Gradle Java toolchain caused F-Droid's buildserver (which only has JDK 21 available and disables toolchain auto-provisioning) to fail with "Cannot find a Java installation... matching languageVersion=17"; the app still compiles to Java 17 bytecode, but Gradle now uses whichever JDK is already running it instead of requiring an exact match. No user-facing changes.

## [1.2.2] - 2026-07-11

### Changed
- Launcher icon and Play Store feature graphic redesigned to match the app's paper & ink theme — both were still on the pre-redesign dark navy/teal-cyan branding. The launcher icon keeps its original magnifying-glass mark, recolored onto the app's accent-teal/card-white button styling; the feature graphic is rebuilt on the current palette and typography, including the app's signature stamp-chip motif.
- Play Store screenshots refreshed to show the redesigned dashboard (light and dark), the new Settings screen, and a Ping results screen; also corrected to Play's required aspect ratio and format (previous captures would have failed upload validation).

## [1.2.1] - 2026-07-11

### Fixed
- Release build would fail under F-Droid's reproducible-build tooling, which strips signing configuration before building from source; the release build type now tolerates a missing signing config instead of throwing

## [1.2.0] - 2026-07-09

### Added
- App Settings screen with a manual light/dark theme override (follows the system setting by default)
- Redesigned dashboard home: a security-posture hero card with a plain-language status line, live metric tiles (latency with sparkline, VPN detection, local IP/gateway), and an expandable latency detail card
- Per-endpoint latency threshold in Endpoint Monitor with a new amber "Slow" state; endpoint cards now show real reachability (Up/Slow/Down) from the latest check instead of only whether monitoring is enabled
- 4×2 home-screen widget shows a latency sparkline built from the last 12 refresh samples

### Changed
- Complete visual redesign on a new "paper & ink" palette with Space Grotesk display typography and one consistent three-state status language (teal = normal, amber = attention, red = alert) across every screen and widget
- All four home-screen widgets follow the system light/dark theme and share the app's palette; decorative emoji replaced with text labels
- Wallpaper-based dynamic color removed — it silently overrode the app palette on Android 12+ and broke status-color consistency
- Home tool grid is adaptive and shows more columns on wide screens
- Widget settings simplified: the appearance options (color, opacity, text size, corner radius) had no effect on rendered widgets and were removed; the screen now offers the public-IP consent toggle and a manual refresh action
- Dense technical screens reorganized: section titles on IP Info and Cell Tower cards, Network Log toolbar reduced to one primary action with an overflow menu, and tabular figures stop live numbers from jittering in Ping and Speed Test
- Widget deep links to Wi-Fi Audit, Speed Test, and network scan open their real screens instead of falling back to home

### Fixed
- Wi-Fi Audit finding-dismiss button was below the minimum touch-target size
- Endpoint Monitor status is no longer conveyed by color alone (visible Up/Slow/Down/Paused labels for screen readers and color-blind users)
- Widgets render rounded corners on Android 10 and 11

### Internal
- Design tokens centralized in `core:ui` (single palette source, theme-aware status colors, shared stat/chip components); zero hardcoded colors outside the token layer
- Room database v11 migration adding `monitored_endpoints.latencyThresholdMs`
- New unit tests for widget color mapping and latency history, monitor status logic, widget settings, and theme preferences

## [1.1.3] - 2026-06-29

### Changed
- Renamed the application ID and package from `com.ventoux.netlens` to `com.ventouxlabs.netlens`. **This is a new app identity:** existing installs of `com.ventoux.netlens` will not update in place — F-Droid and Google Play treat the renamed build as a separate app, so moving to it requires a fresh install.

### Internal
- Repaired stale unit tests in the IP Info, Security Posture, and Traceroute modules that had drifted from their current ViewModel constructors and no longer compiled
- CI now runs the library modules' `testDebugUnitTest` alongside the app's `testFossDebugUnitTest`; ~460 previously-unexecuted unit tests now gate every change
- Refreshed the architecture and feature codemaps

## [1.1.2] - 2026-05-18

### Fixed
- 4×1 widget Ping and DNS chips appeared inert because their callbacks refreshed only the 4×2 widget after writing results — replaced inline measurement with deep-links to the full Ping and DNS feature screens
- Restore the missing `androidx.compose.ui.graphics.Color` import in LAN scan host detail (regression from the design-system refactor)

### Changed
- Widget Portal chip opens Wi-Fi Settings (where Android surfaces the native "Sign in to network" affordance) instead of launching Chrome on Apple's captive-portal probe URL
- Widget Ping chip pre-fills `8.8.8.8` so a single tap measures latency without typing
- Widget DNS chip opens DNS Leak Test instead of DNS Lookup

### Internal
- Extracted shared design tokens (`StatusColors` palette + `Spacing` scale) into a new `:core:ui` module, removing 50+ inline color literals across port-scan, IP info, and LAN host detail screens
- Captured the design system in a new `DESIGN.md` at the repo root

## [1.1.1] - 2026-05-10

### Security
- Encrypt the user-supplied AbuseIPDB API key with EncryptedSharedPreferences; one-shot migration from prior plaintext DataStore storage (#79)
- Disable Android Auto Backup (`allowBackup="false"`, `fullBackupContent="false"`) so `adb backup` cannot exfiltrate DataStore or SharedPreferences (#79)
- Fail closed when EncryptedSharedPreferences cannot initialize for billing; Pro state no longer silently downgrades to plaintext (#79)
- Drop `BROWSABLE` from the `netlens://feature/*` deep-link intent filter; first-party widgets and the QS tile keep working, browsers and other apps can no longer auto-launch feature screens (#80)
- Defense-in-depth `--` argv separator before the host argument in ping and traceroute (#79)
- Pin Gradle wrapper distribution by SHA-256 to verify the binary, not just the URL origin (#80)

### Changed
- Explicit `isDebuggable=false` / `isJniDebuggable=false` on the release build type (#80)
- R8 keep rules for Tink errorprone annotations and dnsjava JDK SPI declarations to silence release-build warnings (#80)

## [1.1.0] - 2026-05-04

### Added
- Cell Tower module — serving cell metrics, neighbors, and signal strength
- Wi-Fi Audit module — security audit on join with WPS / weak-cipher detection
- DNS Leak Test module — detect DNS queries escaping the configured resolver
- Visual traceroute with hop geolocation
- Connected devices inventory with new-device alerts
- Persistent latency monitor with rolling chart on home screen
- Host detail screen with risk-classified port scan and JSON export
- IP reputation check via AbuseIPDB
- Comprehensive network change log with timeline view and filters
- Security posture score with persistence, factor navigation, and widget integration
- Quick Settings tile showing security posture score
- Speed test sparkline history chart
- Port scanner risk severity badges and descriptions
- 4×1 widget variant (WAN/LAN/VPN/flag/RSSI/link-speed/ping/captive-portal/IPv6)
- 4×2 widget variant with DNS/routing status row and quick-launch tool chips
- Refresh button and adaptive fill for 4×1 and 4×2 widgets

### Changed
- Replaced ip-api.com with IPinfo API for public IP intelligence
- Equalized home screen tool grid card heights within each row

### Fixed
- Port scan stale ref, O(n²) allocation, risk classifier, and dedup
- Widget consent bypass, country name mapping, and cache safety
- Race conditions and error handling in latency monitor
- Widget concurrency and cancellation issues

## [1.0.1] - 2026-04-25

### Changed
- Renamed application package from `us.beary.netlens` to `com.ventouxlabs.netlens`
- Added Play Store phone screenshots

## [1.0.0] - 2026-04-25

### Added
- 13 network diagnostic tools: Ping, Traceroute, DNS Lookup, LAN Scan, Port Scanner, WHOIS, TLS Inspector, HTTP Tester, mDNS Browser, Wake-on-LAN, IP Info, Endpoint Monitor, and Network Log
- Continuous ping mode with foreground service and live statistics
- Custom IP range support for LAN scan
- LAN device discovery via mDNS (replacing ARP/MAC)
- Scan history with search, filtering, and unified timeline
- Categorized dashboard home screen
- Home screen widget with configurable pages and tap deeplinks
- Material 3 light and dark theme support
- SSRF protection on all network operations
- Input validation hardening across all tools
- F-Droid build recipe and reproducible build configuration
- GitHub Actions CI/CD pipeline with automated releases
- Unit tests with JUnit 5, Turbine, and kotlinx-coroutines-test

### Fixed
- DNS lookup crash and network error handling on Android
- IP Info serialization error (switched to HTTP API)
- AGP 8.7.3 NPE resolved by upgrading to Gradle 8.14 / AGP 8.9.0
- Widget security vulnerabilities closed

[Unreleased]: https://github.com/bearyjd/netlens-android/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/bearyjd/netlens-android/releases/tag/v1.1.0
[1.0.1]: https://github.com/bearyjd/netlens-android/releases/tag/v1.0.1
[1.0.0]: https://github.com/bearyjd/netlens-android/releases/tag/v1.0.0
