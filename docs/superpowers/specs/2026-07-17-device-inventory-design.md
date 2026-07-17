# Device Inventory ("Devices" tool) — Design

Date: 2026-07-17
Status: Approved (brainstorming complete; implementation plan pending)

## Purpose

Give NetLens a persistent LAN device inventory with custom naming and
background new-device alerts — the top gap identified in the 2026-07-16
competitive analysis (Fing's premium hook). Builds on the existing
`KnownDeviceEntity`/`KnownDeviceDao` foundation and LanScan's foreground
new-device notifications.

## Decisions (settled during brainstorming)

| Question | Decision |
|----------|----------|
| v1 scope | Full: dedicated Devices tool UI **and** background watch |
| Network scope | Watched networks only — user explicitly marks a network; identity is **gateway MAC** (+ subnet), not SSID, so background checks need no location permission |
| Pro gating | Inventory UI/naming/foreground alerts free; **background watch is Pro** (`LocalProStatus`; FOSS flavor always-Pro) |
| Cadence | User-set: 15 m / 30 m / 1 h / 6 h, default 1 h; Wi-Fi-connected constraint |
| Module shape | New `feature:devices` + extract scan engines into new `core:scan` shared with `feature:lanscan` |

## What already exists (do not rebuild)

- `KnownDeviceEntity` (MAC identity with IP fallback, first/last seen,
  `isKnown`, deviceType/osGuess) + `KnownDeviceDao` (search, unknown filter,
  mark-known) in `core:data`, Room v12.
- LanScan persists every foreground scan (`LanScanViewModel.persistScanResults`)
  and shows known/unknown badges.
- `NewDeviceNotifier` (channel `new_device_detected`) fires during foreground
  scans; `POST_NOTIFICATIONS` declared in the lanscan module manifest.
- WorkManager infrastructure precedent in `widget/WidgetRefreshWorker`.

## 1. Data model (`core:data`, Room v12 → v13)

- `KnownDeviceEntity` += `customName: String?`, `networkId: Long?`
  (FK-style reference to watched network; null = unwatched/legacy rows).
  Display-name precedence everywhere: `customName ?: hostname ?: vendor ?: ip`.
- New `WatchedNetworkEntity`: `id`, `displayName` (SSID captured in
  foreground), `gatewayMac` (unique index — the network identity), `subnet`,
  `watchEnabled: Boolean`, `addedAt: Long`.
- New `WatchedNetworkDao` (observe all, upsert by gatewayMac, toggle, delete).
- `KnownDeviceDao` += `setCustomName(id, name)`, per-network list query.
- Single additive migration 12→13; schema exported as usual.

## 2. `core:scan` extraction (pure move, no behavior change)

Move from `feature:lanscan` to new `core:scan` (`netlens.android.library` +
`netlens.hilt`):

- Engines + impls: `SubnetScanner`, `ArpTableReader`, `NetBiosProber`,
  `SsdpScanner`, `LanMdnsScanner`, `DeviceFingerprinter`; their models and
  Hilt bindings; their `Fake*` fakes and tests.
- `NewDeviceNotifier` (+ channel strings/icon resources).
- New `DeviceInventoryRepository`: extracts the persist-scan-results +
  diff-against-known + notify-on-new logic currently private to
  `LanScanViewModel`, so foreground scans and the background worker share one
  code path. Also owns watched-network matching (current gateway MAC ∈
  watched set → `networkId` tagging).

`feature:lanscan` and `feature:devices` both depend on `core:scan`.
The feature graph stays flat (no feature→feature edges).

## 3. `feature:devices` (new tool module)

- `ToolDestination.Devices`, route `"devices"`, Discovery category; NavHost
  entry + `app/build.gradle.kts` dependency, per the add-a-tool recipe in
  CLAUDE.md.
- `DevicesScreen`:
  - Device list grouped **New** (isKnown=false) / **Known**, search field,
    mono font for IP/MAC per typography rules.
  - Detail bottom sheet: rename (customName), mark known/unknown, vendor,
    deviceType/osGuess, first/last seen, delete.
  - **Network watch** section: watched-network list, "Watch this network"
    action (captures SSID + gateway MAC + subnet while foregrounded, where
    `ACCESS_FINE_LOCATION` already exists), cadence picker, master toggle.
    Entire section Pro-gated (pattern 1: direct `if (isPro)`) with an
    upsell row when not Pro.
- `DevicesViewModel`: `MutableStateFlow<UiState>` + `.update{}`; actions:
  rename, toggleKnown, delete, search, watchCurrentNetwork, setCadence,
  toggleWatch. `buildExportText()` + Share/Copy `IconButton`s in the
  `TopAppBar` via `ResultExporter` (export itself Pro-gated like the other
  13 tools).
- All user-facing strings in the module's `strings.xml`.

## 4. Background watch

- `DeviceWatchWorker` (Hilt `CoroutineWorker`) in `feature:devices`; unique
  `PeriodicWorkRequest` named `device_watch`, interval = chosen cadence,
  constraint `NetworkType.CONNECTED`.
- Worker sequence:
  1. Resolve current gateway IP via `LinkProperties` and its MAC via ARP —
     no location permission required. Unresolvable or not in the watched set
     → `Result.success()` immediately (cheap no-op).
  2. On a watched network: **lite sweep** — ping sweep + ARP + OUI lookup
     only (skip NetBIOS/SSDP/mDNS probes to bound battery cost).
  3. Feed results through `DeviceInventoryRepository`; for each genuinely new
     device, `NewDeviceNotifier` fires with a deep link to
     `netlens://feature/devices`.
- Scheduling rules: a `WatchScheduler` (in `feature:devices`, Hilt-injected
  into `NetLensApplication.onCreate`) enqueues at app start when watch
  enabled AND Pro;
  re-enqueue (REPLACE) on cadence change; cancel on master-toggle off or Pro
  loss. Cadence + toggle persisted in `UserPreferencesRepository` (DataStore).

## 5. Error handling

- Worker transient failure → `Result.retry()` with WorkManager backoff,
  bounded by run-attempt count (give up after 3 attempts until next period).
- Notification permission not granted → watch section shows a prompt chip;
  notifier already no-ops safely without the permission.
- Rename input: trimmed, length-capped, empty string clears `customName`.
- Migration failure is fatal-by-design (no destructive fallback), consistent
  with existing migrations.

## 6. Testing

- Moved engine tests/fakes must stay green post-extraction (same
  fake-per-engine pattern, new package).
- New unit tests (JUnit 5 + Turbine + fakes, no mocking frameworks):
  - `DeviceInventoryRepository`: diff/notify logic — new device notifies
    once, re-seen device updates lastSeen, MAC-less IP fallback, network
    tagging.
  - `DevicesViewModel`: rename/search/toggle/watch actions against a fake
    repository.
  - `WatchRunner` (plain injectable class holding the worker's logic; the
    `CoroutineWorker` is a thin shell): not-watched no-op, watched-network
    sweep, Pro/toggle gating of scheduling decisions.
- Known verification gaps (flagged per repo guidance, not blockers): the
  `CoroutineWorker` shell, WorkManager scheduling, Room 12→13 migration, and
  notification rendering need on-device/manual QA — no Robolectric or
  instrumentation exists in this repo.

## Out of scope (v1)

- Device-type icon auto-classification beyond the existing fingerprinter.
- Per-device alert muting; alert history screen.
- Multi-user/cloud sync of the inventory (anti-recommendation — privacy).
- Watching non-Wi-Fi (ethernet/USB-tethered) networks.
