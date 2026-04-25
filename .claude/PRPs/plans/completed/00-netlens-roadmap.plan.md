# NetLens Android — Master Roadmap

## Overview

This roadmap tracks 13 PRs that bring the NetLens scaffold to a full-featured network toolkit Android app. PRs are ordered by dependency; parallelism is noted below.

## Architecture Summary

- **Convention plugins**: `netlens.android.feature` (library + compose + hilt + lifecycle + nav), `netlens.android.library`, `netlens.hilt`
- **minSdk 29**, compileSdk 35, Kotlin 2.1, Compose BOM 2024.12.01
- **DI**: Hilt with `@Module @InstallIn(SingletonComponent)`, `@Binds`/`@Provides`
- **Persistence**: Room (`core/data`), DataStore (widget)
- **Networking**: Ktor 3.0.3 (CIO engine), kotlinx-serialization
- **Widget**: Glance 1.1.1 + WorkManager 2.10
- **Navigation**: Jetpack Navigation Compose, `TopLevelDestination` enum, `NetLensNavHost`

## PR Index

| PR | Feature | Branch | Module(s) Touched | Depends On | Complexity | Est. Files |
|----|---------|--------|-------------------|------------|------------|------------|
| PR-01 | IP Info + Widget | `feat/ipinfo-widget` | `feature/ipinfo`, `widget`, `app` nav | scaffold | Large | 14 |
| PR-02 | LAN Scanner + OUI | `feat/lan-scanner` | `feature/lanscan`, `core/network`, `core/data`, `core/oui` | scaffold | Large | 16 |
| PR-03 | Port Scanner | `feat/port-scanner` | `feature/portscan` | scaffold | Medium | 10 |
| PR-04 | DNS Lookup | `feat/dns-lookup` | `feature/dns` | scaffold | Medium | 12 |
| PR-05 | Ping | `feat/ping` | `feature/ping` | scaffold | Small | 8 |
| PR-06 | Wake-on-LAN | `feat/wol` | `feature/wol`, `core/data` | scaffold | Medium | 12 |
| PR-07 | TLS Inspector | `feat/tls-inspector` | `feature/tls` (new), `app` | scaffold | Medium | 10 |
| PR-08 | WHOIS + Reverse DNS | `feat/whois-rdns` | `feature/whois` (new), `app` | PR-01 (IP info models) | Medium | 11 |
| PR-09 | HTTP Request Builder | `feat/http-request` | `feature/httprequest` (new), `core/data`, `app` | scaffold | Large | 14 |
| PR-10 | mDNS / Bonjour Browser | `feat/mdns-browser` | `feature/mdns` (new), `app` | scaffold | Medium | 9 |
| PR-11 | Network Change Log | `feat/network-log` | `feature/netlog` (new), `core/data`, `app` | scaffold | Medium | 12 |
| PR-12 | Endpoint Monitor | `feat/endpoint-monitor` | `feature/uptime` (new), `core/data`, `app` | scaffold | Large | 15 |
| PR-13 | Navigation Redesign | `feat/nav-redesign` | `app` (nav + UI) | PR-01 through PR-12 | Medium | 8 |

## Parallelism
