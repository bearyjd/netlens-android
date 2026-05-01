<!-- Generated: 2026-05-01 | Files scanned: 411 | Token estimate: ~700 -->

# Data Layer

## Database

`NetLensDatabase` — Room, version 9, exportSchema = true

```
core/data/src/main/kotlin/com/ventoux/netlens/core/data/
├── NetLensDatabase.kt        (RoomDatabase, 17 entities)
├── dao/                      (15 DAOs)
├── model/                    (entity classes)
└── di/DataModule.kt          (Hilt SingletonComponent)
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

## Data Flow

```
Feature Screen
  └─ ViewModel (StateFlow<UiState>)
       ├─ engine/* (network ops, parsers)
       └─ *HistoryDao (Room, via Hilt)
            └─ NetLensDatabase (singleton)
```

## Other Storage

- `SharedPreferences` — billing Pro status cache (`netlens_billing`)
- `DataStore` — widget preferences
