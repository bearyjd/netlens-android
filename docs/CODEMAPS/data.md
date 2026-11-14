<!-- Generated: 2026-11-13 | Files scanned: core:data (27 entities, 24 DAOs, schema v19) | Token estimate: ~1080 -->

# Data Layer

## Database

`NetLensDatabase` — Room, version 19, exportSchema = true

Migrations: additive throughout, `MIGRATION_4_5` … `MIGRATION_18_19` in `DataModule`.
Recent: v13 = device inventory (`WatchedNetworkEntity`, `KnownDeviceEntity.customName/
networkId`); v14 = `SpeedTestHistoryEntry.latencyMethod` (distinguishes old full-HTTPS
latency rows from new TCP-connect-RTT rows); v15 = Wi-Fi coverage survey
(`WifiSurveySessionEntity`, `WifiSurveyPointEntity`); v16 = `LanScanInventoryEntry`
(saved LAN scan collections); v17 = fingerprint confidence/evidence; v18 = `model` + user
override columns; v19 = device identity (see below).

**Every schema change needs a `Migration`** — the builder falls back destructively only on
*downgrade*. Schemas are committed under `core/data/schemas/` (`1.json` … `19.json`).

Migrations and real `@Query` SQL are covered by Robolectric tests (`MigrationTest`,
`KnownDeviceDaoTest` — via the opt-in `netlens.android.robolectric` plugin, PR #155).
Room stays pinned to 2.6.1 deliberately: the tests use the classic `MigrationTestHelper`
constructor that Room 2.7's KMP release removed.

```
core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/
├── NetLensDatabase.kt              (RoomDatabase, 27 entities)
├── dao/                            (24 DAOs)
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
| DeviceIdentifierEntity | DeviceIdentifierDao | Identity (v19): MAC/UDN/key identifiers per device; UNIQUE (kind, value, scopeKey), scopeKey '' = global |
| DeviceObservationEntity | DeviceObservationDao | Identity (v19): fingerprint evidence rows, capped per device |
| DeviceSightingEntity | DeviceSightingDao | Identity (v19): which network a device was seen on |
| DeviceMergeDecisionEntity | DeviceMergeDecisionDao | Identity (v19): persisted merge suggestions / 'distinct' decisions |
| NetworkKeyAliasEntity | NetworkKeyAliasDao | Identity (v19): alias network key -> canonical key |
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

**Identity schema (v19):** `known_devices.macAddress` is no longer unique (one device can own many
MACs); `MIGRATION_18_19` backfills one `device_identifiers` row per stored MAC (kind from the U/L
bit, `INSERT OR IGNORE` so differently spelled duplicates collapse onto the lowest device id) and
adds `typeConfidence`/`osConfidence`. It never writes user-authored columns.
