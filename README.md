# NetLens

A comprehensive Android network diagnostics toolkit. Inspect, diagnose, and monitor your network from a single app — no ads, no tracking, fully open-source.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/com.ventoux.netlens)
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=com.ventoux.netlens)

Download the latest APK from [GitHub Releases](https://github.com/bearyjd/netatlas-android/releases).

## Features

### Network Info
- **IP Info** — Public IP address and geolocation lookup (IPinfo API)
- **WHOIS** — Domain and IP ownership information with reverse DNS
- **Cell Tower** — Serving cell metrics, neighboring cells, and signal strength
- **Security Posture** — Aggregate network security score with factor breakdown

### Connectivity
- **Ping** — Latency and reachability testing with continuous mode
- **DNS Lookup** — Query A, AAAA, MX, TXT, and other DNS record types
- **Traceroute** — Trace the network path with hop geolocation visualization
- **Speed Test** — Measure download/upload speed and latency with sparkline history

### Discovery
- **LAN Scan** — Discover devices on your local network with MAC vendor lookup
- **mDNS Browser** — Find Bonjour / .local services on the network
- **Port Scanner** — Scan for open TCP ports with risk severity badges
- **WiFi Analyzer** — View nearby networks, signal strength, and channel usage graph

### Security & Web
- **Wi-Fi Audit** — Security audit on join with WPS / weak-cipher detection
- **DNS Leak Test** — Detect DNS queries escaping the configured resolver
- **TLS Inspector** — Inspect SSL/TLS certificates and cipher suites
- **HTTP Tester** — Send custom HTTP requests and inspect responses

### Tools
- **IP Calculator** — Subnet calculator with CIDR notation, host ranges, and network class
- **Wake-on-LAN** — Send magic packets to wake network devices
- **Endpoint Monitor** — Track uptime and latency of HTTP endpoints
- **Network Log** — View connection history and network events
- **History** — Browse past scan results with search and filtering
- **Home Screen Widget** — Configurable Glance widget (4×1 / 4×2) with refresh and quick-launch chips

## Building

### Requirements

- JDK 17
- Android SDK with compileSdk 35
- Android Studio Ladybug or later (recommended)

### Build

```bash
# FOSS debug APK (no billing, Pro always on)
./gradlew assembleFossDebug

# Google Play debug APK (with billing)
./gradlew assembleGplayDebug

# Run all unit tests
./gradlew testFossDebugUnitTest

# Run tests for a specific module
./gradlew :feature:ping:testFossDebugUnitTest
```

### Release Signing

Create a keystore and add these properties to `local.properties`:

```properties
release.storeFile=path/to/release.keystore
release.storePassword=your-store-password
release.keyAlias=your-key-alias
release.keyPassword=your-key-password
```

Or set equivalent environment variables for CI: `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

```bash
./gradlew assembleRelease
```

## Architecture

Multi-module Gradle project with convention plugins:

```
app                  — Single Activity, navigation host, theme
feature/*            — 22 self-contained feature modules
core/network         — Connectivity monitoring, SSRF guard, result export
core/data            — Room database, DAOs, Hilt data module
core/billing         — ProStatus interface (FOSS / Google Play flavors)
core/oui             — MAC address vendor lookup
widget               — Glance home screen widget
build-logic/         — Convention plugins (Android, Compose, Hilt)
```

Each feature module follows: `Screen.kt` (Compose UI) + `ViewModel.kt` (StateFlow) + `di/Module.kt` (Hilt) + `engine/` (domain logic).

**Stack**: Jetpack Compose, Material 3, Hilt, Room, Ktor (CIO), Navigation Compose, Glance, dnsjava, kotlinx-serialization.

## Privacy

NetLens does not collect, store, or transmit any personal data. See [PRIVACY.md](PRIVACY.md) for the full privacy policy.

## License

Copyright (C) 2026 beary.us

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, version 3.

See [LICENSE](LICENSE) for the full text.
