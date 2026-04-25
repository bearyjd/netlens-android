# NetLens

A comprehensive Android network diagnostics toolkit. Inspect, diagnose, and monitor your network from a single app.

## Features

### Network Info
- **IP Info** — Public IP address and geolocation lookup
- **WHOIS** — Domain and IP ownership information

### Connectivity
- **Ping** — Latency and reachability testing with continuous mode
- **DNS Lookup** — Query A, AAAA, MX, TXT, and other DNS record types
- **Traceroute** — Trace the network path to any host

### Discovery
- **LAN Scan** — Discover devices on your local network with MAC vendor lookup
- **mDNS Browser** — Find Bonjour / .local services on the network
- **Port Scanner** — Scan for open TCP ports on a target host

### Security & Web
- **TLS Inspector** — Inspect SSL/TLS certificates and cipher suites
- **HTTP Tester** — Send custom HTTP requests and inspect responses

### Tools
- **Wake-on-LAN** — Send magic packets to wake network devices
- **Endpoint Monitor** — Track uptime and latency of HTTP endpoints
- **Network Log** — View connection history and network events
- **History** — Browse past scan results with search and filtering
- **Home Screen Widget** — Configurable Glance widget for at-a-glance network status

## Building

### Requirements

- JDK 17
- Android SDK with compileSdk 35
- Android Studio Ladybug or later (recommended)

### Build

```bash
# Debug APK
./gradlew assembleDebug

# Run all unit tests
./gradlew testDebugUnitTest

# Run tests for a specific module
./gradlew :feature:ping:testDebugUnitTest
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
feature/*            — 13 self-contained feature modules
core/network         — Connectivity monitoring, SSRF guard
core/data            — Room database, DAOs, Hilt data module
core/oui             — MAC address vendor lookup
widget               — Glance home screen widget
build-logic/         — Convention plugins (Android, Compose, Hilt)
```

Each feature module follows: `Screen.kt` (Compose UI) + `ViewModel.kt` (StateFlow) + `di/Module.kt` (Hilt) + `engine/` (domain logic).

**Stack**: Jetpack Compose, Material 3, Hilt, Room, Ktor (CIO), Navigation Compose, Glance, dnsjava, kotlinx-serialization.

## License

Copyright (C) 2026 beary.us

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, version 3.

See [LICENSE](LICENSE) for the full text.
