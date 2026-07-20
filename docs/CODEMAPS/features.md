<!-- Generated: 2026-07-20 | Files scanned: 24 feature modules | Token estimate: ~1000 -->

# Feature Modules

Each feature follows: `Screen.kt` + `ViewModel.kt` + `di/Module.kt` + `engine/` + `model/`

UI state: `MutableStateFlow<UiState>` exposed as `StateFlow`, updated via `.copy()`.
Pro-gating: share-export buttons gated via `LocalProStatus.current`.

## Module Index

| Module | Screen | Category | Cross-tool nav | History DAO |
|--------|--------|----------|---------------|-------------|
| feature:ipinfo | IpInfoScreen | NetworkInfo | yes | IpInfoHistoryDao |
| feature:whois | WhoisScreen | NetworkInfo | yes | WhoisHistoryDao |
| feature:celltower | CellTowerScreen | NetworkInfo | no | — |
| feature:posture | PostureScreen | NetworkInfo | no | — |
| feature:ping | PingScreen | Connectivity | no | PingHistoryDao |
| feature:dns | DnsLookupScreen | Connectivity | yes | DnsHistoryDao |
| feature:traceroute | TracerouteScreen | Connectivity | yes | TracerouteHistoryDao |
| feature:speedtest | SpeedTestScreen | Connectivity | no | SpeedTestHistoryDao |
| feature:lanscan | LanScanScreen | Discovery | yes | LanScanHistoryDao |
| feature:devices | DevicesScreen | Discovery | no | KnownDeviceDao, WatchedNetworkDao |
| feature:mdns | MdnsScreen | Discovery | no | MdnsHistoryDao |
| feature:wifi | WifiScreen | Discovery | no | — |
| feature:portscan | PortScanScreen | Discovery | yes | PortScanHistoryDao |
| feature:wifiaudit | WifiAuditScreen | Security | no | — |
| feature:vpnstatus | VpnStatusScreen | Security | no | — |
| feature:dnsleak | DnsLeakScreen | Security | no | — |
| feature:tls | TlsScreen | Security | yes | TlsHistoryDao |
| feature:httptester | HttpTesterScreen | Security | no | HttpTesterHistoryDao |
| feature:wol | WolScreen | Tools | no | WolHistoryDao |
| feature:monitor | MonitorScreen | Tools | no | EndpointDao |
| feature:ipcalc | IpCalcScreen | Tools | no | — |
| feature:netlog | NetLogScreen | Tools | no | NetworkEventDao |
| feature:history | HistoryScreen | Tools | yes | (reads all) |
| feature:widgetsettings | WidgetSettingsScreen | Tools | no | — |

## Pro-Gating Patterns

1. **Direct `if (isPro)`** — 11 screens: wrap share IconButton
2. **Nullable lambda** — LanScan, mDNS: `onShareResults: (() -> Unit)? = null`
3. **Boolean parameter** — WiFi: `isPro` param to `WifiContent` for channel graph

## Result Export

13 tool ViewModels expose `buildExportText(): String`.
Export via `ResultExporter.shareAsText()` / `copyToClipboard()` from `core:network/export/`.
