---
title: "A device merge is logged in a table with no foreign keys, in the same transaction"
author: Bearyjd
date: 2026-11-16
branch: feat/identity-merge-log
cost: { minutes: 5, tokens: 5469 }
id: l-fd56c788
scope: repo
status: active
src: agent
touches:
  - app/build.gradle.kts
  - core/data-testing/build.gradle.kts
  - core/data-testing/src/main/kotlin/com/ventouxlabs/netlens/core/data/testing/FakeDeviceIdentityDao.kt
  - core/data-testing/src/main/kotlin/com/ventouxlabs/netlens/core/data/testing/FakeDeviceMergeDecisionDao.kt
  - core/data-testing/src/main/kotlin/com/ventouxlabs/netlens/core/data/testing/FakeKnownDeviceDao.kt
  - core/data-testing/src/test/kotlin/com/ventouxlabs/netlens/core/data/testing/FakeDeviceIdentityDaoTest.kt
  - core/data/schemas/com.ventouxlabs.netlens.core.data.NetLensDatabase/20.json
  - core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/NetLensDatabase.kt
  - core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/dao/DeviceIdentityDao.kt
  - core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/dao/DeviceMergeDecisionDao.kt
---

## Decided
Each merge writes one audit row (loser row, identifiers, sightings, survivor's user fields) inside the merge transaction. No foreign keys: the decision table cascaded away with the loser, and nothing wrote MERGED. Keep 200 rows, auto-merges evicted first. Deleting a device removes its whole merge chain's rows; clearing the inventory empties the log.

## Tried and ruled out
Deleting only rows where the deleted device is the survivor: an earlier merge into it kept the absorbed device's hostname and notes on disk. Catching log-write errors: a silent audit failure is worse than a rolled-back merge.

## Watch out
Snapshot text is hostile device data, stored raw and unbounded: flatten before any display, and expect a very large row. The prune SQL matches the text USER (pinned by a test).

In the code: DeviceIdentityDao.mergeDevices, KnownDeviceDao.delete, MIGRATION_19_20.
