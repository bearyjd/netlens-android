# Device Fingerprinting & Stable Identity Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking. Each numbered PR must leave
> `./gradlew testFossDebugUnitTest testGplayDebugUnitTest testDebugUnitTest` green and both flavors
> assembling. Read the Global Constraints before starting any task.

**Goal:** Identify LAN devices far more precisely — device type, OS, vendor, *model* — and recognise
the same physical device across rescans, MAC randomization, IP changes and different networks, using
only unprivileged Android APIs.

**Approach in one paragraph:** Every discovery source and a new active probe stage emit two kinds of
output: *observations* (classification evidence such as "HTTP Server: lighttpd", "IPP model: HP
LaserJet M404") and *identifiers* (values unique to one physical device, such as a UPnP UDN, a TLS
key hash or an SMB server GUID). A rule-based `DeviceClassifier` turns observations into per-field
guesses with confidence. A `DeviceIdentityResolver` turns identifiers into a match against the
`known_devices` inventory, replacing today's MAC-or-IP lookup. Identity is no longer a single MAC
column but a set of identifiers per device, stored in a new table.

**Tech stack:** existing — Kotlin, Hilt, Room, Coroutines/Flow, Compose. No new third-party
dependencies are required; every wire protocol below is small enough to hand-encode. Tests: JUnit 5,
hand-written fakes, captured byte fixtures, Robolectric only where `core:data` already uses it.

---

## Decisions (settled)

| Topic | Decision |
|---|---|
| Pricing | **Free for everyone, both flavors.** No `ProStatus` checks anywhere in this work. |
| Active probing default | **Automatic on networks the user has marked as theirs** (watched networks), with a one-time explainer. **Elsewhere: passive discovery only**, plus an "Identify devices" action for that scan. |
| Background watch | Stays **lite** (ping + ARP + OUI). No active probes in the background — battery cost and surprise. The resolver must still work with the few identifiers a lite sweep yields. |
| Identifier storage | **Raw values on device**, so users can see *why* two sightings were merged. Exports exclude identifiers unless the user opts in, and then emit **salted hashes** (per-install salt). |
| Rule licensing | Probe payloads and match rules are **written independently**. Do not copy `nmap-service-probes`, `nmap-os-db`, p0f or Fingerbank data (NPSL/other licences are incompatible with GPL/F-Droid). Public protocol specs and our own captures only. |
| Rule format | **Kotlin DSL** (type-checked, no runtime parser to attack). Revisit a JSON asset only if outside contributions to rules become a goal. |

## What already exists (baseline)

- `core:scan/engine/DeviceFingerprinter.kt` — `if`-chain classifier over mDNS service types, SSDP,
  NetBIOS, hostname and open ports; one confidence number for both type and OS.
- `core:scan/DeviceInventoryRepository.kt` — identity = `getByMac(mac) ?: getByIpWithoutMac(ip)`.
- `core:data/model/KnownDeviceEntity.kt` — unique index on `macAddress`; DB **version 17**.
- Discovery orchestration lives in `feature:lanscan/LanScanViewModel.startScan` (ping, mDNS, SSDP,
  then ARP + NetBIOS enrichment). `feature:devices/WatchRunner` runs a separate lite sweep.
- `SsdpScanner` already fetches description XML (capped, SSRF-checked by `isSafeLocationUrl`).
- `NetBiosProber` parses the NBSTAT name table but discards the unit ID (the adapter MAC).
- `LanMdnsScannerImpl` uses `NsdManager`, keeps service types and service name, drops TXT records.
- `feature:devices/NetworkIdentityImpl` identifies a network by **gateway MAC via ARP**.

### Platform constraint that shapes the whole design

The app targets SDK 36, minSdk 29. On Android 10+ an app targeting API ≥ 29 generally **cannot read
`/proc/net/arp`** (SELinux), and netlink neighbour dumps are blocked from API 30. Consequences:

1. Device MACs are frequently unavailable today, so MAC-keyed identity silently degrades to IP-keyed
   identity — exactly the duplication this plan fixes.
2. `NetworkIdentityImpl.currentGatewayMac()` may return `null` on many devices, so watched-network
   identity is also at risk. Task 1.1 verifies this on hardware; the `NetworkKey` fallback (Task 1.6)
   covers it regardless.

The plan therefore treats MAC as *one* identifier among many and adds MAC sources that do not need
ARP: NBSTAT unit ID, EUI-64 IPv6 addresses, and MAC-bearing mDNS TXT keys.

---

## Global Constraints

- **Module ownership.** Engines, probes, classifier and resolver live in `core:scan`; storage in
  `core:data`; UI in `feature:lanscan` and `feature:devices`. No feature→feature dependency. Shared
  fakes in `core:scan-testing` / `core:data-testing`, consumed with `testImplementation` only.
- **Hostile input.** Every byte from the network is attacker-controlled. Every parser:
  - reads through a byte cap (default 16 KiB per response, 64 KiB for favicon/XML, existing 256 KiB
    for SSDP XML) and a line-length cap;
  - bounds-checks every length field against the actual buffer, never against a claimed length
    (see the `NetBiosProber.parseResponse` comment for the bug class);
  - passes every display or export string through `DisplayText.flatten`, and additionally caps it at
    256 chars for display;
  - is covered by a `*HostileInputTest` (truncated, oversized, length-field lies, control
    characters, invalid UTF-8, pathological regex input).
- **No catastrophic regexes.** Rule regexes are matched against capped input only; a unit test
  asserts that no rule pattern contains nested quantifiers (`(…+)+`, `(…*)*`, `(…+)*`).
- **Probe targets are pinned.** A probe connects only to the literal IP of a device discovered in the
  current scan's subnet. No DNS resolution, no redirect following, `Host:` header is the IP literal.
  Any URL a device supplies (e.g. a favicon link, a UPnP `presentationURL`) is ignored unless it
  passes the same predicate as `SsdpScannerImpl.isSafeLocationUrl` (same IP, http/https). Do not
  weaken `isSafeLocationUrl`; any change to it needs bypass-case tests.
- **No credentials, no brute force.** SNMP uses only community `public`, one request. No login
  attempts on any service. No exploit-style probes.
- **Ownership in `known_devices`.** Scans write only scan-derived fields. `customName`, `tags`,
  `notes`, `location`, `isKnown` are user-authored and are never overwritten by a scan **or by a
  merge** (merge rules in Task 1.4).
- **One migration.** All schema changes land in a single `MIGRATION_17_18` in PR 1. Later PRs add no
  columns; if a later task discovers a missing column, amend PR 1 before it merges, or add a 18→19
  migration with its own test — never edit a shipped migration.
- **Strings** go in the owning module's `strings.xml`. Technical values (identifiers, banners, hashes)
  render in JetBrains Mono, labels in Inter. Lazy lists that mix identifiers, evidence and
  suggestions namespace their keys (e.g. `"id:$kind:$value"`, `"obs:$id"`).
- **Kotlin style:** `StateFlow` UI state, immutable `copy` updates, no `!!`, exhaustive `when` over
  sealed types.
- **Budgets (deep identify):** ≤ 32 concurrent sockets; connect timeout 400 ms; per-probe read
  timeout 1.5 s; per-host budget 8 s; whole stage hard cap 60 s; fully cancellable; progress
  reported to the UI.

---

## Domain model (core:scan)

```kotlin
// core/scan/model/identity/
enum class IdentifierKind(val strength: Strength, val scope: Scope) {
    MAC_UNIVERSAL(STRONG, GLOBAL),        // IEEE-assigned MAC (U/L bit clear)
    MAC_LOCAL(MEDIUM, NETWORK),           // randomized / locally administered: valid on one network only
    UPNP_UDN(STRONG, GLOBAL),             // uuid:… from SSDP description / USN
    UPNP_SERIAL(STRONG, GLOBAL),          // <serialNumber>, keyed with manufacturer+model
    TLS_KEY_SHA256(STRONG, GLOBAL),       // SHA-256 of SubjectPublicKeyInfo, self-signed only
    SSH_HOSTKEY_SHA256(STRONG, GLOBAL),   // SHA-256 of host key blob
    SMB_SERVER_GUID(STRONG, GLOBAL),
    IPP_PRINTER_UUID(STRONG, GLOBAL),
    MDNS_DEVICE_ID(STRONG, GLOBAL),       // AirPlay deviceid, Cast id, HAP id, Matter/ESPHome mac …
    SNMP_ENGINE_ID(STRONG, GLOBAL),
    HOSTNAME(WEAK, NETWORK),              // never auto-merges; scoring input only
}
enum class Strength { STRONG, MEDIUM, WEAK }
enum class Scope { GLOBAL, NETWORK }

data class DeviceIdentifier(
    val kind: IdentifierKind,
    val value: String,            // normalised (lowercase hex, no separators, etc.)
    val source: ObservationSource,
    val proven: Boolean = false,  // holder demonstrated the private key (completed TLS handshake / verified SSH sig)
)

enum class ObservationSource { PING, ARP, OUI, MDNS, MDNS_TXT, SSDP, NETBIOS, PORTS, BANNER, HTTP, TLS, SSH, SMB, SNMP, IPP, RTSP, TTL, IPV6 }
enum class ObservedField { DEVICE_TYPE, OS, OS_VERSION, VENDOR, MODEL, PRODUCT }

data class Observation(
    val source: ObservationSource,
    val field: ObservedField,
    val value: String,   // flattened + capped
    val weight: Int,     // 0..100 contribution, set by the rule that produced it
    val detail: String?, // human-readable evidence, e.g. "port 631 (IPP)"
)
```

`LanDevice` gains `identifiers: Set<DeviceIdentifier>`, `observations: List<Observation>`,
`model: String?`, and `additionalIps: Set<String>`. `fingerprintEvidence` stays and is derived from
observations for display, so existing UI and persistence keep working during the transition.

**Identifiers are unique, not authenticated.** A hostile device can announce another device's UPnP
UDN, mDNS id or SSH host key. Only `proven = true` identifiers (completed TLS handshake; SSH with a
verified signature) demonstrate possession of a private key. This is no worse than today — a MAC is
trivially spoofable — but the resolver must detect contradictions (Task 1.4) and must never let a
claimed identifier *silence* a new-device alert for a device that is simultaneously visible elsewhere.

---

## Schema: MIGRATION_17_18 (core:data)

```sql
-- 1. known_devices: new scan-derived columns; MAC no longer unique (one device can own many MACs)
ALTER TABLE known_devices ADD COLUMN model TEXT;
ALTER TABLE known_devices ADD COLUMN typeConfidence INTEGER;
ALTER TABLE known_devices ADD COLUMN osConfidence INTEGER;
DROP INDEX IF EXISTS index_known_devices_macAddress;
CREATE INDEX IF NOT EXISTS index_known_devices_macAddress ON known_devices(macAddress);

-- 2. identifiers. scopeKey is NOT NULL ('' = global, otherwise the NetworkKey) because SQLite treats
--    NULLs as distinct in a UNIQUE index, which would allow duplicate global identifiers.
CREATE TABLE device_identifiers (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  deviceId INTEGER NOT NULL REFERENCES known_devices(id) ON DELETE CASCADE,
  kind TEXT NOT NULL, value TEXT NOT NULL, scopeKey TEXT NOT NULL,
  source TEXT NOT NULL, proven INTEGER NOT NULL DEFAULT 0,
  firstSeen INTEGER NOT NULL, lastSeen INTEGER NOT NULL);
CREATE UNIQUE INDEX index_device_identifiers_kind_value_scopeKey ON device_identifiers(kind, value, scopeKey);
CREATE INDEX index_device_identifiers_deviceId ON device_identifiers(deviceId);

-- 3. observations (capped at 64 rows per device by the repository; lets rules be re-run after an
--    app update without a rescan, and backs the evidence panel)
CREATE TABLE device_observations (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  deviceId INTEGER NOT NULL REFERENCES known_devices(id) ON DELETE CASCADE,
  source TEXT NOT NULL, field TEXT NOT NULL, value TEXT NOT NULL,
  weight INTEGER NOT NULL, detail TEXT, lastSeen INTEGER NOT NULL);
CREATE UNIQUE INDEX index_device_observations_dedupe ON device_observations(deviceId, source, field, value);

-- 4. where a device has been seen (cross-network history)
CREATE TABLE device_sightings (
  deviceId INTEGER NOT NULL REFERENCES known_devices(id) ON DELETE CASCADE,
  networkKey TEXT NOT NULL, networkLabel TEXT, lastIp TEXT NOT NULL,
  firstSeen INTEGER NOT NULL, lastSeen INTEGER NOT NULL,
  PRIMARY KEY(deviceId, networkKey));

-- 5. user decisions the resolver must respect
CREATE TABLE device_merge_decisions (
  deviceIdLow INTEGER NOT NULL REFERENCES known_devices(id) ON DELETE CASCADE,
  deviceIdHigh INTEGER NOT NULL REFERENCES known_devices(id) ON DELETE CASCADE,
  decision TEXT NOT NULL,  -- 'DISTINCT'
  decidedAt INTEGER NOT NULL,
  PRIMARY KEY(deviceIdLow, deviceIdHigh));

-- 6. backfill each existing MAC as an identifier (stored MACs are uppercase, colon-separated)
INSERT OR IGNORE INTO device_identifiers(deviceId, kind, value, scopeKey, source, proven, firstSeen, lastSeen)
SELECT id,
       CASE WHEN substr(macAddress, 2, 1) IN ('2','3','6','7','A','B','E','F') THEN 'MAC_LOCAL' ELSE 'MAC_UNIVERSAL' END,
       lower(replace(macAddress, ':', '')),
       '',          -- legacy rows have no reliable network key; global keeps today's matching behaviour
       'ARP', 0, firstSeen, lastSeen
FROM known_devices WHERE macAddress IS NOT NULL;
```

Room entities: `DeviceIdentifierEntity`, `DeviceObservationEntity`, `DeviceSightingEntity`,
`DeviceMergeDecisionEntity`; DAOs `DeviceIdentifierDao`, `DeviceObservationDao`,
`DeviceSightingDao`, `DeviceMergeDecisionDao`. Bump `NetLensDatabase` to version 18, export the
schema, update the `docs/CODEMAPS/data.md` entity/DAO counts.

---

## PR 1 — Identity foundation (no user-visible change except fewer duplicates)

### Task 1.1: Device reality check (do first, 30 min, no code merged)
- [ ] On an Android 10+ device and an Android 14+ device, run a debug build and log: is
      `/proc/net/arp` readable? Does `NsdServiceInfo.attributes` contain TXT data after resolve? Does
      `NetworkIdentityImpl.currentGatewayMac()` return non-null?
- [ ] Record results in this plan under "Device findings". They decide how much weight the ARP-free
      MAC sources (PR 3) and `NetworkKey` fallbacks (Task 1.6) carry. They do not block PR 1.

### Task 1.2: Domain model
- [ ] Add `core/scan/model/identity/` types from the Domain model section.
- [ ] `MacAddresses` utility: normalise, `isLocallyAdministered`, `isMulticast`, reject
      all-zero/broadcast, EUI-64 → MAC (flip U/L bit, strip `ff:fe`).
- [ ] Tests: normalisation table, U/L bit, EUI-64 round-trip, junk input.

### Task 1.3: Schema + migration
- [ ] Entities, DAOs and `MIGRATION_17_18` as specified; register in `DataModule`.
- [ ] `MigrationTest`: 17→18 with rows having a universal MAC, a local MAC, no MAC, and two rows with
      the same MAC (must not abort the migration — `INSERT OR IGNORE`).
- [ ] Assert that user-authored columns are byte-identical before and after.
- [ ] `core:data-testing`: `FakeDeviceIdentifierDao`, `FakeDeviceObservationDao`,
      `FakeDeviceSightingDao`, `FakeDeviceMergeDecisionDao`; extend `FakeKnownDeviceDao`.

### Task 1.4: `DeviceIdentityResolver` (core:scan, pure logic over DAOs)
Input: one scan's `List<LanDevice>` + `NetworkKey`. Output per device:
`Resolution.Existing(id) | Resolution.New | Resolution.Ambiguous(ids)`, plus `List<MergeSuggestion>`.

Algorithm:
1. **Within-scan grouping.** Devices in the same scan sharing a GLOBAL STRONG identifier are one
   physical device with several addresses (multi-homed NAS, router with several interfaces). Merge
   them, primary IP = the one with most identifiers, record the rest in `additionalIps`. Surface
   "seen at N addresses" in the UI — a device spoofing another device's UDN is visible this way.
2. **Candidate lookup.** For each identifier: GLOBAL kinds match `scopeKey = ''`; NETWORK kinds match
   `scopeKey = currentNetworkKey`. Collect candidate rows.
3. **Exactly one candidate** → `Existing`.
4. **Several candidates** → try **auto-merge** of the candidate rows, allowed only if *all* of:
   - they share a STRONG identifier;
   - no identifier kind has *different* values on the two rows that could not legitimately co-exist
     (two different `SMB_SERVER_GUID`s = two machines; two different `MAC_LOCAL`s is normal);
   - no `DISTINCT` decision exists between them;
   - at most one row has any user-authored field set (otherwise → `MergeSuggestion` for the user).
   Merge keeps the row with the earliest `firstSeen`, moves identifiers, observations and sightings,
   copies the other row's user fields only into *empty* fields, ORs `isKnown`, deletes the other row.
   If any rule fails → `Ambiguous`, attach the scan data to the most recently seen candidate, emit a
   suggestion.
5. **No candidate via identifiers** → legacy fallback: `getByIpWithoutMac(ip)` *only* when the device
   has no identifiers at all (keeps today's behaviour for lite background sweeps); else `New`.
6. **Weak scoring** (only for `New` results): compare against rows seen in the last 180 days on any
   network. Score: model exact (+40), normalised hostname exact (+30; strip `-2`, `.local`, case),
   OUI vendor equal (+10), service-type Jaccard × 20, deviceType equal (+10). Score ≥ 60 →
   `MergeSuggestion(newDevice, row, score, reasons)`. **Never auto-merged.**

- [ ] Implement resolver + `MergeSuggestion` model.
- [ ] Tests (one per rule above), including the invariants:
  - [ ] a merge never overwrites a non-empty `customName`/`tags`/`notes`/`location`;
  - [ ] a `DISTINCT` decision suppresses both auto-merge and suggestion;
  - [ ] a device with no identifiers resolves exactly as `DeviceInventoryRepository` does today
        (port the existing `DeviceInventoryRepositoryTest` cases to the resolver);
  - [ ] claimed STRONG identifier on a device seen at another IP in the same scan → grouped, not
        silently merged into a known row while the real holder is also present at a different MAC.

### Task 1.5: Wire the repository
- [ ] `DeviceInventoryRepositoryImpl.persistScan` delegates identity to the resolver, then upserts
      identifiers (bump `lastSeen`), observations (cap 64/device, evict lowest weight then oldest),
      sightings, and scan-derived columns with the existing "higher confidence wins per field" rule
      (now per field via `typeConfidence`/`osConfidence`).
- [ ] `NewDeviceNotifier` fires only for `Resolution.New`. Not for `Existing`, not for merges.
      `Ambiguous` does not notify (it attaches to an existing row) but is surfaced in the UI.
- [ ] Keep `macAddress` column populated with the most recent universal MAC (or local MAC if none)
      for backward compatibility with existing UI, search and export.
- [ ] Expose `pendingMergeSuggestions: Flow<List<MergeSuggestion>>` (in-memory, per process; they
      are recomputed on each scan, so nothing needs persisting until the user decides).

### Task 1.6: `NetworkKey` (promote network identity into core:scan)
Move `NetworkIdentity` from `feature:devices` to `core:scan` (lanscan needs it too) and add
`currentNetworkKey(): NetworkKey` with a fallback chain, first hit wins:
1. `gw-mac:<gateway MAC>` (when ARP works);
2. `gw-id:<kind>:<value>` — a STRONG identifier of the device at the gateway IP, learnt in a previous
   scan (router UPnP UDN or TLS key). Works without ARP;
3. `bssid:<BSSID>` (foreground only, location permission already held);
4. `l3:<sha256(gatewayIp|subnet|ssid)>` as last resort.
- [ ] Store the resolved key's human label (SSID or subnet) in `device_sightings.networkLabel`.
- [ ] `WatchedNetworkEntity` keeps `gatewayMac` as is. Watched-network matching gains a fallback that
      compares the stored gateway's identifiers, so watching keeps working when ARP is blocked.
- [ ] Tests with a fake `ConnectivityManager` boundary (`NetworkInterfaceProvider` pattern already in
      `core:network`).

**PR 1 exit check:** existing tests green; resolver suite green; migration test green; manual scan on
a real LAN produces the same inventory as before, minus duplicates.

---

## PR 2 — Extract the discovery pipeline (refactor, no behaviour change)

Discovery orchestration currently lives in `LanScanViewModel.startScan`. Probes, enrichment and the
"Identify devices" action must be shared by `feature:lanscan` and `feature:devices`, so promote it.

### Task 2.1: `DeviceDiscoveryPipeline` (core:scan)
- [ ] `fun run(request: DiscoveryRequest): Flow<DiscoveryEvent>` where
      `DiscoveryRequest(cidr, mode: Lite | Standard | Deep, networkKey)` and events are
      `DeviceUpdated(LanDevice)`, `Progress(stage, done, total)`, `Finished(devices)`.
- [ ] `Lite` = today's `WatchRunner` sweep. `Standard` = today's foreground scan (ping, mDNS, SSDP,
      ARP, NetBIOS). `Deep` = Standard + PR 4/5 probe stage (stubbed until then).
- [ ] Move `mergeDevice` / `strongerFingerprint` logic out of the ViewModel into the pipeline.
- [ ] `LanScanViewModel` and `WatchRunner` call the pipeline; their tests keep passing unchanged
      except for injection.
- [ ] `FakeDeviceDiscoveryPipeline` in `core:scan-testing`.

---

## PR 3 — Passive enrichment (identifiers and evidence from what we already receive)

### Task 3.1: SSDP
- [ ] Parse and keep `UDN`, `serialNumber`, `modelNumber`, `modelDescription`, `manufacturerURL`
      host only, and the M-SEARCH response `SERVER:` and `USN:` headers.
- [ ] Identifiers: `UPNP_UDN` (lowercased uuid), `UPNP_SERIAL` as `manufacturer|model|serial`
      (serials are only unique per vendor/model; skip placeholder serials like `0`, `123456`, all
      same char, or equal to the model).
- [ ] Observations: `SERVER` header OS token (e.g. `Linux/4.9` → OS Linux), model/manufacturer.
- [ ] Extend `SsdpHostileInputTest`: duplicated tags, 10 000 nested elements, entity expansion text
      (the extractor must not expand entities), oversized UDN.

### Task 3.2: NetBIOS
- [ ] Parse the unit ID (6 bytes after the name table) → `MAC_UNIVERSAL`/`MAC_LOCAL` identifier when
      not all-zero (Samba reports zeros). This yields MACs where ARP is blocked.
- [ ] Keep `<20>` (file server) and `<03>` presence as observations (Windows/Samba server).
- [ ] Extend `NetBiosHostileInputTest` with name counts that overrun the buffer.

### Remaining work (not yet written)

The rest of PR 3 (mDNS TXT records and additional service types) and the later PRs (classifier
rewrite, active probe stage, UI, cross-network history, export and privacy-policy updates) still need
to be planned.

---

## Device findings

_To be filled in by Task 1.1._
