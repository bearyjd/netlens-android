<!-- Generated: 2026-07-20 | Files scanned: 37 | Token estimate: ~780 -->

# Data Layer

## Database

`NetLensDatabase` — Room, version 14, exportSchema = true

Migrations: additive throughout. v13 = device inventory (`WatchedNetworkEntity`,
`KnownDeviceEntity.customName/networkId`); v14 = `SpeedTestHistoryEntry.latencyMethod`
(distinguishes old full-HTTPS latency rows from new TCP-connect-RTT rows).

```
core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/
├── NetLensDatabase.kt              (RoomDatabase, 19 entities)
├── dao/                            (17 DAOs)
├── model/                          (entity + UI projection classes)
└── di/
    ├── DataModule.kt               (Room provider, SingletonComponent)
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
