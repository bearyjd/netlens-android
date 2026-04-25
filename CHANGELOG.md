# Changelog

All notable changes to NetLens will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

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

[1.0.0]: https://github.com/bearyjd/netatlas-android/releases/tag/v1.0.0
