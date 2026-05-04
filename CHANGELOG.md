# Changelog

All notable changes to NetLens will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

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
- Renamed application package from `us.beary.netlens` to `com.ventoux.netlens`
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

[Unreleased]: https://github.com/bearyjd/netatlas-android/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/bearyjd/netatlas-android/releases/tag/v1.0.1
[1.0.0]: https://github.com/bearyjd/netatlas-android/releases/tag/v1.0.0
