<!-- Generated: 2026-08-04 | Files scanned: 24 feature modules | Token estimate: ~840 -->

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

1. **`ResultActions`** (`core:ui`) — 14 screens. The shared TopAppBar copy/share row.
   `isPro` is a **required** parameter with no default, so a call site cannot omit the
   gate and silently expose the Pro-only share action.
2. **Nullable lambda** — LanScan, mDNS: `onShareResults: (() -> Unit)?`, null when not Pro.
   Used where callbacks are threaded through a stateless `Content` composable. LanScan has
   three export targets (results, event, saved inventory); its event row interleaves a
   BookmarkAdd button between copy and share, so it cannot adopt `ResultActions` without
   reordering the UI.
3. **Boolean parameter** — WiFi: `isPro` param to `WifiContent` gating the channel graph
   (non-action UI, so neither pattern above applies).

## Result Export

18 tool ViewModels expose `buildExportText(): String` — Ping, Traceroute, DNS, PortScan,
WHOIS, HttpTester, LanScan, TLS, IpInfo, IpCalc, mDNS, SpeedTest, WiFi, WifiSurvey,
CellTower, Devices, DnsLeak, VpnStatus.

Screens render the buttons via `ResultActions` (`core:ui`) and perform the export with
`ResultExporter.shareAsText()` / `copyToClipboard()` (`core:network/export/`).
NetLog is the exception: a dropdown menu item exporting JSON, not the shared row.
