---
title: A scan from a suggested-network chip is attributed to the Wi-Fi network only when the live network confirms it
author: Bearyjd
date: 2026-11-15
branch: master
cost: { minutes: 30, tokens: 67981 }
id: l-75d078d8
scope: repo
status: active
src: agent
touches:
  - feature/devices/src/main/kotlin/com/ventouxlabs/netlens/feature/devices/MergeSuggestionsSheet.kt
  - feature/lanscan/src/main/kotlin/com/ventouxlabs/netlens/feature/lanscan/LanScanViewModel.kt
  - feature/lanscan/src/test/kotlin/com/ventouxlabs/netlens/feature/lanscan/ScanNetworkAttributionTest.kt
---

## Decided
A custom-range scan is attributed (network key + SSID label) only when BOTH hold: its normalized range equals a non-VPN suggested network, AND the live identity snapshot taken inside the LAN-network binding reports the same subnet. Otherwise the scan is unattributed.

## Tried and ruled out
Matching only against the suggested-network list. The identity snapshot describes just the one network the process is bound to, so a hotspot or Ethernet chip would borrow the Wi-Fi gateway key and SSID. The list is also refreshed only when the Scan tab is selected, so after moving to another network that reuses the same range (192.168.1.0/24 at home and the office) a stale chip matched, and the start/end same-network check could not catch it because both readings were of the new network.

## Watch out
A typed range equal to the live LAN range is attributed on purpose: it is the same network. A VPN chip, another interface's chip and any other range stay unattributed. AUTO scans are unchanged.

In the code: LanScanViewModel.startScan (the attributable gate and normalizedCidr helper); tests in ScanNetworkAttributionTest, including the hotspot and stale-suggestion cases that failed before the fix.
