<!-- Generated: 2026-08-17 | Files scanned: core:data (22 entities, 19 DAOs, schema v16) | Token estimate: ~1080 -->

# Data Layer

## Database

`NetLensDatabase` — Room, version 16, exportSchema = true

Migrations: additive throughout, `MIGRATION_4_5` … `MIGRATION_15_16` in `DataModule`.
Recent: v13 = device inventory (`WatchedNetworkEntity`, `KnownDeviceEntity.customName/
networkId`); v14 = `SpeedTestHistoryEntry.latencyMethod` (distinguishes old full-HTTPS
latency rows from new TCP-connect-RTT rows); v15 = Wi-Fi coverage survey
(`WifiSurveySessionEntity`, `WifiSurveyPointEntity`); v16 = `LanScanInventoryEntry`
(saved LAN scan collections).

**Every schema change needs a `Migration`** — the builder falls back destructively only on
*downgrade*. Schemas are committed under `core/data/schemas/` (`1.json` … `16.json`).

Migrations and real `@Query` SQL are covered by Robolectric tests (`MigrationTest`,
`KnownDeviceDaoTest` — via the opt-in `netlens.android.robolectric` plugin, PR #155).
Room stays pinned to 2.6.1 deliberately: the tests use the classic `MigrationTestHelper`
constructor that Room 2.7's KMP release removed.

```
core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/
├── NetLensDatabase.kt              (RoomDatabase, 22 entities)
├── dao/                            (19 DAOs)
├── model/                          (entity + UI projection classes)
├── repository/                     (HistoryRepository interface + Impl over 11 DAOs)
├── preferences/                    (UserPreferencesRepository — injectable DataStore)
├── secure/                         (KeyValueStore, EncryptedKeyValueStore)
└── di/
    ├── DataModule.kt               (Room provider + migrations, SingletonComponent)
    ├── RepositoryModule.kt         (@Binds HistoryRepositoryImpl → HistoryRepository)
    └── PreferencesModule.kt        (DataStore / SharedPreferences providers)
```

## Entities & DAOs

| Entity | DAO | Used By |
|--------|-----|---------|
| SavedHost | — | LanScan (inline) |
| WolTarget | WolTargetDao | WoL |
| NetworkEvent | NetworkEventDao | NetLog |
| MonitoredEndpoint | EndpointDao | Monitor |
| EndpointCheck | EndpointDao | Monitor |
| PingHistoryEntry | PingHistoryDao | Ping, History |
| LanScanHistoryEntry | LanScanHistoryDao | LanScan, History |
| PortScanHistoryEntry | PortScanHistoryDao | PortScan, History |
| DnsHistoryEntry | DnsHistoryDao | DNS, History |
| WhoisHistoryEntry | WhoisHistoryDao | WHOIS, History |
| IpInfoHistoryEntry | IpInfoHistoryDao | IpInfo, History |
| TracerouteHistoryEntry | TracerouteHistoryDao | Traceroute, History |
| TlsHistoryEntry | TlsHistoryDao | TLS, History |
| HttpTesterHistoryEntry | HttpTesterHistoryDao | HttpTester, History |
| MdnsHistoryEntry | MdnsHistoryDao | mDNS, History |
| WolHistoryEntry | WolHistoryDao | WoL, History |
| SpeedTestHistoryEntry | SpeedTestHistoryDao | SpeedTest, History (+ latencyMethod tag) |
| KnownDeviceEntity | KnownDeviceDao | LanScan + Devices (inventory, custom names, new-device alerts) |
| WatchedNetworkEntity | WatchedNetworkDao | Devices (Pro background watch; identity = gateway MAC) |
| LanScanInventoryEntry | LanScanInventoryDao | LanScan (saved scan collections, v16) |
| WifiSurveySessionEntity | WifiSurveyDao | WiFi coverage survey (v15) |
| WifiSurveyPointEntity | WifiSurveyDao | WiFi coverage survey — aggregated capture spots (v15) |

**Write-path ownership (`known_devices`):** scan-derived columns (`hostname`, `ip`,
`vendor`, `deviceType`, `osGuess`) belong to `DeviceInventoryRepository.persistScan` via
`KnownDeviceDao.updateLastSeen`; user-authored columns (`customName`, `tags`, `notes`,
`location`) belong to the Devices detail sheet via `KnownDeviceDao.updateUserDetails`.
Keep them disjoint — a re-scan must never clobber what the user typed. Tags are a
normalised comma-separated column; always go through `DeviceTags` and `KnownDeviceSearch`.
The disjointness invariant is pinned against the real SQL by `KnownDeviceDaoTest`.

## Data Flow

```
Feature Screen
  └─ ViewModel (StateFlow<UiState>)
       ├─ engine/* (network ops, parsers)
       └─ *HistoryDao (Room, via Hilt)
            └─ NetLensDatabase (singleton)
```

## Other Storage

- `EncryptedSharedPreferences` — billing Pro status cache (`netlens_billing`, gplay flavor)
- `DataStore` (`user_preferences`) via `UserPreferencesRepository` — favorite/recent tool routes, IPinfo consent, posture score snapshot, latency-monitor settings (host / threshold / enabled), AbuseIPDB API key
- `DataStore` (widget) — Glance widget configuration
