---
title: Verifying scan persistence and the inventory on a real device
author: Bearyjd
date: 2026-10-22
branch: chore/trail-learnings
cost: { minutes: 1, tokens: 9988 }
touches:
  - feature/lanscan/src/main/kotlin/com/ventouxlabs/netlens/feature/lanscan/LanScanViewModel.kt
  - core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan/DeviceInventoryRepository.kt
---

A LAN scan writes history and the inventory only when the whole scan has finished (startScan tail: saveToHistory, then persistScan). Give it more than 90 seconds before judging the History or Inventory tabs; opening them early shows empty lists. The scan screen shows only 5-6 identified devices early on, but about 35 hosts are saved for a /24 (about 30 with no name, vendor, type or MAC). Do not read the early on-screen count as the saved list; that mistake produced a wrong diagnosis once. Android denies /proc/net/arp (logcat avc denied), so every row is IP-keyed and shows MAC unknown. To check the database, copy it off the device: adb exec-out run-as com.ventouxlabs.netlens.debug cat databases/netlens.db (and the -wal and -shm files) into an empty directory, then query it with python3 -I sqlite3; the device has no sqlite3. The Pixel Fold has two displays, so screencap needs -d with the inner (...152) or outer (...153) id, and the debug build is a separate package (com.ventouxlabs.netlens.debug).
