---
title: "Device check of identity UI: findings and adb tips"
author: Bearyjd
date: 2026-11-15
branch: master
cost: { minutes: 28, tokens: 123074 }
touches:
  - feature/lanscan/src/test/kotlin/com/ventouxlabs/netlens/feature/lanscan/LanScanArpOrderTest.kt
  - feature/lanscan/src/test/kotlin/com/ventouxlabs/netlens/feature/lanscan/LanScanDiscoveryStateTest.kt
  - core/scan-testing/src/main/kotlin/com/ventouxlabs/netlens/core/scan/testing/InventoryFakes.kt
  - core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan/DeviceInventoryRepository.kt
  - core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan/identity/MergePreview.kt
  - core/scan/src/test/kotlin/com/ventouxlabs/netlens/core/scan/inventory/InventorySuggestionsTest.kt
  - feature/devices/src/main/kotlin/com/ventouxlabs/netlens/feature/devices/DevicesScreen.kt
  - feature/devices/src/main/kotlin/com/ventouxlabs/netlens/feature/devices/DevicesViewModel.kt
  - feature/devices/src/main/kotlin/com/ventouxlabs/netlens/feature/devices/MergeSuggestionsSheet.kt
  - feature/devices/src/main/kotlin/com/ventouxlabs/netlens/feature/devices/model/MergeSuggestionUi.kt
---

## Decided
- Device checks of the Devices suggestion sheet: seed rows into the DEBUG package DB only (pull via `adb exec-out run-as <pkg> cat databases/netlens.db`, check PRAGMA user_version==19, insert known_devices + device_merge_decisions SUGGESTED rows with ip 10.99.0.x so cleanup is exact, push back via `run-as sh -c 'cat > ...'`, delete -wal/-shm, verify by re-pull). Keep a backup; never touch com.ventouxlabs.netlens (Play release).

## Tried and ruled out
- `adb shell input text` through the IME garbles characters (Deiev for Devices): navigate by scrolling and tapping coordinates, read results with `screencap -d <physical display id>` (outer 4619827677550801153) and `uiautomator dump`.
- Reading "6 devices" from a screenshot 45 s into a scan: the ping sweep had not finished; persistence only happens at the end, so DB checks right after a tap show nothing.

## Watch out
- Tapping a suggested-network chip starts a CUSTOM-range scan => no network attribution (sightings under unknown-network). Only AUTO scans attribute.
- AUTO range under a VPN scans the tunnel /32 (pre-existing, core:network getActiveNetworkInterface), so ping finds nothing; only mDNS/SSDP find devices.
- The identity resolver is inert on real phones today: no MACs (Android blocks ARP) and no non-MAC identifiers are produced yet.
