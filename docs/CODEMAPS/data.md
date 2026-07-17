<!-- Generated: 2026-07-16 | Files scanned: 36 | Token estimate: ~750 -->

# Data Layer

## Database

`NetLensDatabase` — Room, version 12, exportSchema = true

```
core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/
├── NetLensDatabase.kt              (RoomDatabase, 18 entities)
├── dao/                            (16 DAOs)
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
| SpeedTestHistoryEntry | SpeedTestHistoryDao | SpeedTest, History |
| KnownDeviceEntity | KnownDeviceDao | LanScan (devices inventory + new-device alerts) |

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
