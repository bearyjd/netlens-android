# Device Inventory ("Devices" tool) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persistent LAN device inventory ("Devices" tool) with custom naming, per-network watch, and Pro-gated background new-device alerts, reusing LanScan's scan engines via a new shared `core:scan` module.

**Architecture:** Extract LanScan's six scan engines + `NewDeviceNotifier` + a new `DeviceInventoryRepository` into `core:scan`, shared by `feature:lanscan` and a new `feature:devices`. Room grows v12→v13 (custom names + watched-network identity by gateway MAC). Background watch is a thin `DeviceWatchWorker` shell over a fully-injectable, unit-tested `WatchRunner`, scheduled by a `WatchScheduler` from `NetLensApplication.onCreate`. The feature graph stays flat: `feature:*` depend only on `core:*`, never on each other.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Hilt 2.53, Room 2.6.1, WorkManager 2.10.0, DataStore Preferences, Coroutines/Flow. Tests: JUnit 5 + Turbine + kotlinx-coroutines-test with hand-written fakes (no mocking frameworks).

## Global Constraints

Every task's requirements implicitly include this section. Values copied verbatim from `docs/superpowers/specs/2026-07-17-device-inventory-design.md`.

- **Network identity is the gateway MAC (+ subnet), never SSID.** Background checks resolve the current gateway MAC via `LinkProperties` + ARP so they need no location permission.
- **No new permissions.** SSID capture happens only in the foreground where `ACCESS_FINE_LOCATION` already exists; `POST_NOTIFICATIONS` already declared. Do not add any `<uses-permission>` beyond what already exists.
- **Pro gating:** inventory UI, custom naming, and foreground alerts are **free**; **background watch scheduling is Pro** (`LocalProStatus`; FOSS flavor is always Pro). Result export (Share) is Pro like the other 13 tools; Copy is free.
- **Watch cadence:** user-set 15 m / 30 m / 1 h / 6 h, **default 1 h**; `PeriodicWorkRequest` constraint `NetworkType.CONNECTED`.
- **Lite sweep only in background:** ping sweep + ARP + OUI lookup **only** — skip NetBIOS/SSDP/mDNS probes to bound battery cost.
- **Watched networks only:** background watch acts only on networks the user explicitly marked; unresolvable gateway or gateway not in the watched set → immediate `Result.success()` no-op.
- **Migration is additive and fatal-by-design:** single migration 12→13, no destructive fallback; schema exported as usual.
- **Display-name precedence everywhere:** `customName ?: hostname ?: vendor ?: ip`.
- **Kotlin style:** no `!!`; `val` over `var`; state via `MutableStateFlow` + `.update { it.copy(...) }`; exhaustive `when` (no `else`) on sealed types; all user-facing strings in the module's `strings.xml`.
- **Module shape:** new `core:scan` (`netlens.android.library` + `netlens.hilt`) and new `feature:devices` (`netlens.android.feature`). `feature:lanscan` and `feature:devices` both depend on `core:scan`; no feature→feature edges.
- **Gradle task names:** `:app` is flavored — build with `assembleFossDebug`, test with `testFossDebugUnitTest`. All other modules are unflavored — build/test via `assembleDebug` / `testDebugUnitTest`. CI runs `testFossDebugUnitTest testDebugUnitTest`, which auto-discovers any module with a `src/test` tree.

**WorkManager architecture decision (deliberate deviation from the spec's "Hilt `CoroutineWorker`" wording):** This repo has **no** `HiltWorkerFactory` / `Configuration.Provider`. The existing `WidgetRefreshWorker` obtains its Hilt dependencies through a manual `@EntryPoint` + `EntryPointAccessors.fromApplication(...)` inside `doWork()`, relying on WorkManager's default initializer. `DeviceWatchWorker` follows that exact proven pattern — the worker is a thin shell that pulls a fully Hilt-injected `WatchRunner` via an `@EntryPoint`. All real logic lives in `WatchRunner` and is unit-tested without WorkManager. Rationale: introducing `HiltWorkerFactory` + `Configuration.Provider` would create a second WorkManager init mechanism alongside the widget's, for zero testability gain (the logic is already injected/tested). If the team prefers full androidx-hilt worker integration instead, that is an isolated change to Task I only.

---

## File Structure

**New module `core:scan`** (`com.ventouxlabs.netlens.core.scan`):
- `engine/` — moved verbatim from `feature:lanscan`: `SubnetScanner(+Impl)`, `ArpTableReader(+Impl)`, `NetBiosProber(+Impl)`, `SsdpScanner(+Impl)`, `LanMdnsScanner(+Impl)`, `DeviceFingerprinter(+Impl)` (holds `PortFingerprint`).
- `model/` — moved: `LanDevice`, `DiscoveryMethod`, `SsdpDevice`, `NetBiosInfo`.
- `NewDeviceNotifier(+Impl)` — moved, plus its channel strings + `ic_new_device_notification` drawable.
- `DeviceInventoryRepository(+Impl)` — **new**: persist-scan-results + diff-against-known + notify-on-new + watched-network `networkId` tagging (extracted from `LanScanViewModel.persistScanResults`).
- `di/ScanModule.kt` — **new**: `@Binds` for every engine + notifier + repository.

**`core:data`** (Room v12→v13):
- `model/KnownDeviceEntity.kt` — add `customName`, `networkId`.
- `model/WatchedNetworkEntity.kt` — **new**.
- `dao/WatchedNetworkDao.kt` — **new**.
- `dao/KnownDeviceDao.kt` — add `setCustomName`, `getDevicesForNetwork`.
- `NetLensDatabase.kt` — version 13, new entity + DAO accessor.
- `di/DataModule.kt` — `MIGRATION_12_13`, register it, provide new DAO.
- `preferences/UserPreferencesRepository.kt` — add watch cadence + master-toggle prefs (Task G).

**`feature:lanscan`** — rewired to `core:scan`; engine/model/notifier files deleted; imports updated.

**New module `feature:devices`** (`com.ventouxlabs.netlens.feature.devices`):
- `DevicesScreen.kt`, `DevicesViewModel.kt`, `model/DevicesUiState.kt`, `model/DeviceDisplay.kt` (name-precedence extension), `model/WatchCadence.kt`.
- `NetworkIdentity.kt` (interface + impl: gateway MAC/subnet/SSID resolution), `di/DevicesModule.kt`.
- `WatchRunner.kt` (+ `WatchOutcome`), `WatchScheduler.kt` (interface + impl + `ScheduleAction`), `DeviceWatchWorker.kt`.
- `res/values/strings.xml`.

**`app`** — `ToolDestination.Devices`, NavHost entry, `DeepLinkRouter` mapping, `build.gradle.kts` dependency, `NetLensApplication` watch-scheduler hookup.

---

### Task A: Extract `core:scan` module (engines, models, notifier) — pure move

**Files:**
- Create: `core/scan/build.gradle.kts`, `core/scan/src/main/AndroidManifest.xml`, `core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan/di/ScanModule.kt`
- Modify: `settings.gradle.kts` (add `include(":core:scan")`), `feature/lanscan/build.gradle.kts`, `feature/lanscan/src/main/AndroidManifest.xml`, `feature/lanscan/.../di/LanScanModule.kt`, and every `feature:lanscan` file importing a moved type.
- Move (git mv + package rewrite): 6 engine interface+impl files, 4 models, `NewDeviceNotifier.kt`, `res/drawable/ic_new_device_notification.xml`, the moved notification strings, and the engine `Fake*`/tests.
- Test: moved `core/scan/src/test/.../engine/*Test.kt` (unchanged behavior).

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces (new package `com.ventouxlabs.netlens.core.scan`):
  - `engine.SubnetScanner { fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> }`
  - `engine.ArpTableReader { suspend fun getMacForIp(ip: String): String?; suspend fun getAll(): Map<String,String>; fun invalidateCache() }`
  - `engine.NetBiosProber { suspend fun probe(ip: String): NetBiosInfo? }`
  - `engine.SsdpScanner { fun discover(timeoutMs: Long = 3000): Flow<SsdpDevice> }`
  - `engine.LanMdnsScanner { fun discover(timeoutMs: Long = 5000): Flow<LanDevice> }`
  - `engine.DeviceFingerprinter { suspend fun fingerprint(device: LanDevice): LanDevice; fun classifyFromServices(services: List<String>): Pair<String?,String?>; fun classifyFromSsdp(ssdpDevice: SsdpDevice): Pair<String?,String?>; fun classifyFromNetBios(info: NetBiosInfo): String?; fun fingerprintWithPorts(device: LanDevice, openPorts: List<Int>): PortFingerprint }`
  - `engine.PortFingerprint(deviceType: String?, osGuess: String?, evidence: List<String>)`
  - `model.LanDevice`, `model.DiscoveryMethod`, `model.SsdpDevice`, `model.NetBiosInfo`
  - `NewDeviceNotifier { fun createChannel(); fun notify(device: KnownDeviceEntity) }` with `NewDeviceNotifierImpl.CHANNEL_ID = "new_device_detected"`

- [ ] **Step 1: Register the module and create its Gradle build file**

Add to `settings.gradle.kts` immediately after `include(":core:oui")`:

```kotlin
include(":core:scan")
```

Create `core/scan/build.gradle.kts`:

```kotlin
plugins {
    id("netlens.android.library")
    id("netlens.hilt")
}

android {
    namespace = "com.ventouxlabs.netlens.core.scan"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:oui"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.core.ktx)
}
```

Create `core/scan/src/main/AndroidManifest.xml` (the notifier lives here now, so the permission moves here):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
</manifest>
```

- [ ] **Step 2: Move engine + model + notifier source files with `git mv` and rewrite package lines**

Run from repo root:

```bash
SRC=feature/lanscan/src/main/kotlin/com/ventouxlabs/netlens/feature/lanscan
DST=core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan
mkdir -p "$DST/engine" "$DST/model" core/scan/src/main/res/drawable

git mv "$SRC/engine/SubnetScanner.kt"        "$DST/engine/SubnetScanner.kt"
git mv "$SRC/engine/SubnetScannerImpl.kt"    "$DST/engine/SubnetScannerImpl.kt"
git mv "$SRC/engine/ArpTableReader.kt"        "$DST/engine/ArpTableReader.kt"
git mv "$SRC/engine/NetBiosProber.kt"         "$DST/engine/NetBiosProber.kt"
git mv "$SRC/engine/SsdpScanner.kt"           "$DST/engine/SsdpScanner.kt"
git mv "$SRC/engine/LanMdnsScanner.kt"        "$DST/engine/LanMdnsScanner.kt"
git mv "$SRC/engine/LanMdnsScannerImpl.kt"    "$DST/engine/LanMdnsScannerImpl.kt"
git mv "$SRC/engine/DeviceFingerprinter.kt"   "$DST/engine/DeviceFingerprinter.kt"
git mv "$SRC/model/LanDevice.kt"              "$DST/model/LanDevice.kt"
git mv "$SRC/model/DiscoveryMethod.kt"        "$DST/model/DiscoveryMethod.kt"
git mv "$SRC/model/SsdpDevice.kt"             "$DST/model/SsdpDevice.kt"
git mv "$SRC/model/NetBiosInfo.kt"            "$DST/model/NetBiosInfo.kt"
git mv "$SRC/NewDeviceNotifier.kt"            "$DST/NewDeviceNotifier.kt"
git mv feature/lanscan/src/main/res/drawable/ic_new_device_notification.xml \
       core/scan/src/main/res/drawable/ic_new_device_notification.xml

# Rewrite the package + self-referential imports on every moved file.
grep -rl 'com.ventouxlabs.netlens.feature.lanscan.engine' "$DST" | xargs sed -i \
  's#com\.ventouxlabs\.netlens\.feature\.lanscan\.engine#com.ventouxlabs.netlens.core.scan.engine#g'
grep -rl 'com.ventouxlabs.netlens.feature.lanscan.model' "$DST" | xargs sed -i \
  's#com\.ventouxlabs\.netlens\.feature\.lanscan\.model#com.ventouxlabs.netlens.core.scan.model#g'
# NewDeviceNotifier.kt declares the base lanscan package.
sed -i 's#^package com\.ventouxlabs\.netlens\.feature\.lanscan$#package com.ventouxlabs.netlens.core.scan#' \
  "$DST/NewDeviceNotifier.kt"
```

- [ ] **Step 3: Move the notification strings into `core:scan`**

Cut these five lines out of `feature/lanscan/src/main/res/values/strings.xml` (the `<!-- Notifications -->` block, lines 92–96 in the current file) and create `core/scan/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="lanscan_notification_channel_name">New Device Alerts</string>
    <string name="lanscan_notification_channel_desc">Alerts when a new device is detected on your network</string>
    <string name="lanscan_notification_new_device_title">New device detected</string>
    <string name="lanscan_notification_new_device_text">%1$s (%2$s)</string>
    <string name="lanscan_notification_unknown_vendor">Unknown vendor</string>
</resources>
```

`NewDeviceNotifierImpl` already references `R.string.lanscan_notification_*` and `R.drawable.ic_new_device_notification` by simple name; because the file now declares `package com.ventouxlabs.netlens.core.scan`, its `R` resolves to `com.ventouxlabs.netlens.core.scan.R`, which now carries these resources. No code change needed inside the notifier beyond the package line from Step 2.

- [ ] **Step 4: Create the `core:scan` Hilt module and remove the moved bindings from `LanScanModule`**

Create `core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan/di/ScanModule.kt`:

```kotlin
package com.ventouxlabs.netlens.core.scan.di

import com.ventouxlabs.netlens.core.scan.NewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.NewDeviceNotifierImpl
import com.ventouxlabs.netlens.core.scan.engine.ArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.ArpTableReaderImpl
import com.ventouxlabs.netlens.core.scan.engine.DeviceFingerprinter
import com.ventouxlabs.netlens.core.scan.engine.DeviceFingerprinterImpl
import com.ventouxlabs.netlens.core.scan.engine.LanMdnsScanner
import com.ventouxlabs.netlens.core.scan.engine.LanMdnsScannerImpl
import com.ventouxlabs.netlens.core.scan.engine.NetBiosProber
import com.ventouxlabs.netlens.core.scan.engine.NetBiosProberImpl
import com.ventouxlabs.netlens.core.scan.engine.SsdpScanner
import com.ventouxlabs.netlens.core.scan.engine.SsdpScannerImpl
import com.ventouxlabs.netlens.core.scan.engine.SubnetScanner
import com.ventouxlabs.netlens.core.scan.engine.SubnetScannerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {

    @Binds
    @Singleton
    abstract fun bindSubnetScanner(impl: SubnetScannerImpl): SubnetScanner

    @Binds
    @Singleton
    abstract fun bindLanMdnsScanner(impl: LanMdnsScannerImpl): LanMdnsScanner

    @Binds
    @Singleton
    abstract fun bindSsdpScanner(impl: SsdpScannerImpl): SsdpScanner

    @Binds
    @Singleton
    abstract fun bindNetBiosProber(impl: NetBiosProberImpl): NetBiosProber

    @Binds
    @Singleton
    abstract fun bindArpTableReader(impl: ArpTableReaderImpl): ArpTableReader

    @Binds
    @Singleton
    abstract fun bindDeviceFingerprinter(impl: DeviceFingerprinterImpl): DeviceFingerprinter

    @Binds
    @Singleton
    abstract fun bindNewDeviceNotifier(impl: NewDeviceNotifierImpl): NewDeviceNotifier
}
```

Replace the entire body of `feature/lanscan/.../di/LanScanModule.kt` with an empty-but-valid module (all its bindings moved to `ScanModule`). Delete the file if nothing else lives in it — verify with `git grep 'class LanScanModule'` that nothing references it; it is only a Hilt module discovered by annotation, so deletion is safe:

```bash
git rm feature/lanscan/src/main/kotlin/com/ventouxlabs/netlens/feature/lanscan/di/LanScanModule.kt
rmdir feature/lanscan/src/main/kotlin/com/ventouxlabs/netlens/feature/lanscan/di 2>/dev/null || true
```

- [ ] **Step 5: Point `feature:lanscan` at `core:scan` and rewrite its imports**

Add to `feature/lanscan/build.gradle.kts` `dependencies { }` (first line):

```kotlin
    implementation(project(":core:scan"))
```

Remove the now-unused `android.permission.POST_NOTIFICATIONS` line from `feature/lanscan/src/main/AndroidManifest.xml` (it merges in transitively from `core:scan`); the manifest becomes:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

Rewrite the moved-type imports across the remaining `feature:lanscan` main sources (ViewModel, screens, remaining models):

```bash
grep -rl 'com.ventouxlabs.netlens.feature.lanscan.engine' \
  feature/lanscan/src/main | xargs sed -i \
  's#com\.ventouxlabs\.netlens\.feature\.lanscan\.engine#com.ventouxlabs.netlens.core.scan.engine#g'
# Move ONLY the four relocated model imports (LanDevice, DiscoveryMethod, SsdpDevice, NetBiosInfo).
for T in LanDevice DiscoveryMethod SsdpDevice NetBiosInfo; do
  grep -rl "com.ventouxlabs.netlens.feature.lanscan.model.$T" feature/lanscan/src/main | xargs -r sed -i \
    "s#com\.ventouxlabs\.netlens\.feature\.lanscan\.model\.$T#com.ventouxlabs.netlens.core.scan.model.$T#g"
done
# NewDeviceNotifier moved to the base core.scan package.
grep -rl 'com.ventouxlabs.netlens.feature.lanscan.NewDeviceNotifier' feature/lanscan/src/main | xargs -r sed -i \
  's#com\.ventouxlabs\.netlens\.feature\.lanscan\.NewDeviceNotifier#com.ventouxlabs.netlens.core.scan.NewDeviceNotifier#g'
```

`LanScanViewModel` references `NewDeviceNotifier` by simple name (same package before); after the rewrite it must import `com.ventouxlabs.netlens.core.scan.NewDeviceNotifier`. Verify no dangling references remain:

```bash
git grep -n 'feature\.lanscan\.engine\|feature\.lanscan\.model\.\(LanDevice\|DiscoveryMethod\|SsdpDevice\|NetBiosInfo\)\|feature\.lanscan\.NewDeviceNotifier' feature/lanscan/src/main
```

Expected: no output.

- [ ] **Step 6: Move the engine tests + fakes into `core:scan`**

```bash
STST=feature/lanscan/src/test/kotlin/com/ventouxlabs/netlens/feature/lanscan
DTST=core/scan/src/test/kotlin/com/ventouxlabs/netlens/core/scan
mkdir -p "$DTST/engine"

git mv "$STST/engine/ArpTableReaderTest.kt"       "$DTST/engine/ArpTableReaderTest.kt"
git mv "$STST/engine/DeviceFingerprinterTest.kt"  "$DTST/engine/DeviceFingerprinterTest.kt"
git mv "$STST/engine/NetBiosProberTest.kt"        "$DTST/engine/NetBiosProberTest.kt"
git mv "$STST/engine/SsdpScannerTest.kt"          "$DTST/engine/SsdpScannerTest.kt"
git mv "$STST/engine/SubnetScannerImplTest.kt"    "$DTST/engine/SubnetScannerImplTest.kt"
git mv "$STST/engine/FakeArpTableReader.kt"       "$DTST/engine/FakeArpTableReader.kt"
git mv "$STST/engine/FakeSubnetScanner.kt"        "$DTST/engine/FakeSubnetScanner.kt"
git mv "$STST/engine/FakeLanMdnsScanner.kt"       "$DTST/engine/FakeLanMdnsScanner.kt"
git mv "$STST/engine/FakeNetBiosProber.kt"        "$DTST/engine/FakeNetBiosProber.kt"
git mv "$STST/engine/FakeSsdpScanner.kt"          "$DTST/engine/FakeSsdpScanner.kt"
git mv "$STST/engine/FakeOuiLookup.kt"            "$DTST/engine/FakeOuiLookup.kt"

grep -rl 'com.ventouxlabs.netlens.feature.lanscan.engine' "$DTST" | xargs sed -i \
  's#com\.ventouxlabs\.netlens\.feature\.lanscan\.engine#com.ventouxlabs.netlens.core.scan.engine#g'
grep -rl 'com.ventouxlabs.netlens.feature.lanscan.model' "$DTST" | xargs sed -i \
  's#com\.ventouxlabs\.netlens\.feature\.lanscan\.model#com.ventouxlabs.netlens.core.scan.model#g'
```

`DeviceInventoryTest.kt`, `CidrValidationTest.kt`, and `LanScanBuildExportTextTest.kt` stay in `feature:lanscan` (they test the ViewModel). Their imports of the four moved models + `NewDeviceNotifier` are fixed by Task C (which rewrites the ViewModel test) and by this rewrite for the model imports:

```bash
for T in LanDevice DiscoveryMethod SsdpDevice NetBiosInfo; do
  grep -rl "com.ventouxlabs.netlens.feature.lanscan.model.$T" feature/lanscan/src/test | xargs -r sed -i \
    "s#com\.ventouxlabs\.netlens\.feature\.lanscan\.model\.$T#com.ventouxlabs.netlens.core.scan.model.$T#g"
done
grep -rl 'com.ventouxlabs.netlens.feature.lanscan.engine' feature/lanscan/src/test | xargs -r sed -i \
  's#com\.ventouxlabs\.netlens\.feature\.lanscan\.engine#com.ventouxlabs.netlens.core.scan.engine#g'
```

- [ ] **Step 7: Run the moved engine tests to verify they still pass**

Run: `./gradlew :core:scan:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `ArpTableReaderTest`, `DeviceFingerprinterTest`, `NetBiosProberTest`, `SsdpScannerTest`, `SubnetScannerImplTest` all pass.

- [ ] **Step 8: Run the lanscan tests to verify the rewiring compiles and passes**

Run: `./gradlew :feature:lanscan:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `DeviceInventoryTest`, `CidrValidationTest`, `LanScanBuildExportTextTest` all pass (behavior unchanged; only package paths moved).

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor: extract scan engines and NewDeviceNotifier into core:scan"
```

---

### Task B: Room v13 — custom names, watched networks

**Files:**
- Modify: `core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/model/KnownDeviceEntity.kt`
- Create: `core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/model/WatchedNetworkEntity.kt`
- Create: `core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/dao/WatchedNetworkDao.kt`
- Modify: `core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/dao/KnownDeviceDao.kt`
- Modify: `core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/NetLensDatabase.kt`
- Modify: `core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/di/DataModule.kt`
- Test: `core/data/src/test/kotlin/com/ventouxlabs/netlens/core/data/WatchedNetworkEntityTest.kt` (a small value-type test; Room migration itself needs on-device QA — see Task J notes)

**Interfaces:**
- Consumes: nothing from Task A.
- Produces:
  - `KnownDeviceEntity` gains `val customName: String? = null`, `val networkId: Long? = null`.
  - `WatchedNetworkEntity(id: Long = 0, displayName: String?, gatewayMac: String, subnet: String, watchEnabled: Boolean = true, addedAt: Long = System.currentTimeMillis())` — table `watched_networks`, unique index on `gatewayMac`.
  - `WatchedNetworkDao`: `observeAll(): Flow<List<WatchedNetworkEntity>>`, `suspend getByGatewayMac(mac: String): WatchedNetworkEntity?`, `suspend upsert(network: WatchedNetworkEntity): Long`, `suspend setWatchEnabled(id: Long, enabled: Boolean)`, `suspend delete(id: Long)`.
  - `KnownDeviceDao` gains `suspend setCustomName(id: Long, customName: String?)` and `fun getDevicesForNetwork(networkId: Long): Flow<List<KnownDeviceEntity>>`.

- [ ] **Step 1: Write a failing test for the new entity's watch-default**

Create `core/data/src/test/kotlin/com/ventouxlabs/netlens/core/data/WatchedNetworkEntityTest.kt`:

```kotlin
package com.ventouxlabs.netlens.core.data

import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchedNetworkEntityTest {

    @Test
    fun `watchEnabled defaults to true and id defaults to zero`() {
        val network = WatchedNetworkEntity(
            displayName = "Home",
            gatewayMac = "AA:BB:CC:DD:EE:FF",
            subnet = "192.168.1.0/24",
        )
        assertEquals(0L, network.id)
        assertTrue(network.watchEnabled)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*WatchedNetworkEntityTest*"`
Expected: FAIL — compilation error, `WatchedNetworkEntity` is unresolved.

- [ ] **Step 3: Create the `WatchedNetworkEntity`**

Create `core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/model/WatchedNetworkEntity.kt`:

```kotlin
package com.ventouxlabs.netlens.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watched_networks",
    indices = [Index(value = ["gatewayMac"], unique = true)],
)
data class WatchedNetworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // SSID captured in the foreground for display only; identity is the gateway MAC.
    val displayName: String?,
    val gatewayMac: String,
    val subnet: String,
    val watchEnabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*WatchedNetworkEntityTest*"`
Expected: PASS.

- [ ] **Step 5: Add the two columns to `KnownDeviceEntity`**

In `core/data/.../model/KnownDeviceEntity.kt`, add two fields after `osGuess`:

```kotlin
    val deviceType: String? = null,
    val osGuess: String? = null,
    // User-supplied friendly name; display precedence is customName ?: hostname ?: vendor ?: ip.
    val customName: String? = null,
    // Watched-network this row was last tagged against (null = unwatched/legacy).
    val networkId: Long? = null,
)
```

- [ ] **Step 6: Create the `WatchedNetworkDao`**

Create `core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/dao/WatchedNetworkDao.kt`:

```kotlin
package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedNetworkDao {
    @Query("SELECT * FROM watched_networks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchedNetworkEntity>>

    @Query("SELECT * FROM watched_networks WHERE gatewayMac = :mac")
    suspend fun getByGatewayMac(mac: String): WatchedNetworkEntity?

    // Unique index on gatewayMac makes REPLACE an idempotent upsert-by-identity.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(network: WatchedNetworkEntity): Long

    @Query("UPDATE watched_networks SET watchEnabled = :enabled WHERE id = :id")
    suspend fun setWatchEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM watched_networks WHERE id = :id")
    suspend fun delete(id: Long)
}
```

- [ ] **Step 7: Add the two new queries to `KnownDeviceDao`**

In `core/data/.../dao/KnownDeviceDao.kt`, add before `delete`:

```kotlin
    @Query("UPDATE known_devices SET customName = :customName WHERE id = :id")
    suspend fun setCustomName(id: Long, customName: String?)

    @Query("SELECT * FROM known_devices WHERE networkId = :networkId ORDER BY lastSeen DESC")
    fun getDevicesForNetwork(networkId: Long): Flow<List<KnownDeviceEntity>>
```

- [ ] **Step 8: Register the entity + DAO in `NetLensDatabase` and bump the version to 13**

In `NetLensDatabase.kt`: add imports `WatchedNetworkDao` and `WatchedNetworkEntity`; add `WatchedNetworkEntity::class,` to the `entities` array; change `version = 12` to `version = 13`; add the accessor:

```kotlin
    abstract fun knownDeviceDao(): KnownDeviceDao
    abstract fun watchedNetworkDao(): WatchedNetworkDao
}
```

- [ ] **Step 9: Add the additive migration + DAO provider in `DataModule`**

In `DataModule.kt`, add after `MIGRATION_11_12`:

```kotlin
    // v13: custom device names + watched-network identity (gateway MAC).
    // Additive only — new columns are nullable, new table is independent.
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE known_devices ADD COLUMN customName TEXT")
            db.execSQL("ALTER TABLE known_devices ADD COLUMN networkId INTEGER")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `watched_networks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `displayName` TEXT, `gatewayMac` TEXT NOT NULL, `subnet` TEXT NOT NULL, `watchEnabled` INTEGER NOT NULL DEFAULT 1, `addedAt` INTEGER NOT NULL)""",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_watched_networks_gatewayMac` ON `watched_networks` (`gatewayMac`)")
        }
    }
```

Add `MIGRATION_12_13` to the `.addMigrations(...)` call (append it after `MIGRATION_11_12`). Add the provider after `provideKnownDeviceDao`:

```kotlin
    @Provides
    fun provideWatchedNetworkDao(database: NetLensDatabase): WatchedNetworkDao =
        database.watchedNetworkDao()
```

Add `import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao` to the DAO imports.

- [ ] **Step 10: Run the core:data tests and confirm the schema exports at v13**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `WatchedNetworkEntityTest` and existing `UserPreferencesRepositoryTest` pass. Room writes `core/data/schemas/.../13.json` during compilation.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat(data): Room v13 — custom device names and watched networks"
```

---

### Task C: `DeviceInventoryRepository` + rewire `LanScanViewModel`

**Files:**
- Create: `core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan/DeviceInventoryRepository.kt`
- Modify: `core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan/di/ScanModule.kt`
- Modify: `feature/lanscan/.../LanScanViewModel.kt` (replace private `persistScanResults` with a call to the repository)
- Create: `core/scan/src/test/kotlin/com/ventouxlabs/netlens/core/scan/DeviceInventoryRepositoryTest.kt`
- Create test fakes in `core:scan`: `core/scan/src/test/kotlin/com/ventouxlabs/netlens/core/scan/InMemoryKnownDeviceDao.kt`, `RecordingNewDeviceNotifier.kt`, `FakeWatchedNetworkDao.kt`
- Modify: `feature/lanscan/.../DeviceInventoryTest.kt` (drop the persist-logic cases now covered in `core:scan`; keep the ViewModel-level toggle/delete cases)

**Interfaces:**
- Consumes (Task A): `NewDeviceNotifier`, `model.LanDevice`. (Task B): `KnownDeviceDao` (incl. `getByIpWithoutMac`, `setMacAddress`, `insertIfNew`, `updateLastSeen`), `WatchedNetworkDao.getByGatewayMac`, `KnownDeviceEntity.networkId`.
- Produces:
  - `DeviceInventoryRepository { suspend fun persistScan(devices: List<LanDevice>, networkId: Long?): Unit }`
  - Behavior: for each device, match by MAC then by mac-less IP; upgrade mac-less rows when a MAC resolves; update `lastSeen`/`ip`/`hostname`/`vendor`/`deviceType`/`osGuess` and set `networkId` on existing rows; insert new rows with `networkId`; fire `NewDeviceNotifier.notify(...)` exactly once per genuinely new row.

- [ ] **Step 1: Write the failing repository test**

Create `core/scan/src/test/kotlin/com/ventouxlabs/netlens/core/scan/DeviceInventoryRepositoryTest.kt`:

```kotlin
package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DeviceInventoryRepositoryTest {

    private fun repo(
        dao: InMemoryKnownDeviceDao,
        notifier: RecordingNewDeviceNotifier,
    ) = DeviceInventoryRepositoryImpl(dao, notifier)

    @Test
    fun `new device with MAC is persisted and notified once`() = runTest {
        val dao = InMemoryKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        repo(dao, notifier).persistScan(
            listOf(LanDevice(ip = "192.168.1.10", hostname = "phone", macAddress = "AA:BB:CC:DD:EE:01")),
            networkId = null,
        )
        val stored = dao.getByMac("AA:BB:CC:DD:EE:01")
        assertNotNull(stored)
        assertEquals("192.168.1.10", stored?.ip)
        assertEquals(1, notifier.notified.size)
    }

    @Test
    fun `re-seen device updates lastSeen and does not notify`() = runTest {
        val dao = InMemoryKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        dao.insertIfNew(
            KnownDeviceEntity(
                macAddress = "AA:BB:CC:DD:EE:04",
                hostname = "old", ip = "192.168.1.40", vendor = "Old",
                firstSeen = 1000L, lastSeen = 1000L,
            ),
        )
        notifier.notified.clear()
        repo(dao, notifier).persistScan(
            listOf(LanDevice(ip = "192.168.1.41", hostname = "new", macAddress = "AA:BB:CC:DD:EE:04")),
            networkId = 7L,
        )
        val updated = dao.getByMac("AA:BB:CC:DD:EE:04")
        assertEquals("192.168.1.41", updated?.ip)
        assertEquals(1000L, updated?.firstSeen)
        assertEquals(7L, updated?.networkId)
        assertEquals(0, notifier.notified.size)
    }

    @Test
    fun `mac-less device is persisted keyed by IP then upgrades in place`() = runTest {
        val dao = InMemoryKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        val r = repo(dao, notifier)
        r.persistScan(listOf(LanDevice(ip = "192.168.1.22", hostname = "d", macAddress = null)), networkId = null)
        r.persistScan(listOf(LanDevice(ip = "192.168.1.22", hostname = "d", macAddress = "AA:BB:CC:DD:EE:22")), networkId = null)
        assertEquals(1, dao.allDevices.size)
        assertNotNull(dao.getByMac("AA:BB:CC:DD:EE:22"))
        assertNull(dao.getByIpWithoutMac("192.168.1.22"))
    }

    @Test
    fun `insert tags the network id`() = runTest {
        val dao = InMemoryKnownDeviceDao()
        val notifier = RecordingNewDeviceNotifier()
        repo(dao, notifier).persistScan(
            listOf(LanDevice(ip = "192.168.1.5", macAddress = "AA:BB:CC:DD:EE:05")),
            networkId = 42L,
        )
        assertEquals(42L, dao.getByMac("AA:BB:CC:DD:EE:05")?.networkId)
    }
}
```

- [ ] **Step 2: Add the `core:scan` test fakes**

Create `core/scan/src/test/kotlin/com/ventouxlabs/netlens/core/scan/InMemoryKnownDeviceDao.kt` (ported from the existing lanscan test fake, in the new package, with the two Task B methods added):

```kotlin
package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

class InMemoryKnownDeviceDao : KnownDeviceDao {
    val allDevices = mutableListOf<KnownDeviceEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<KnownDeviceEntity>>(emptyList())

    override fun getAllDevices(): Flow<List<KnownDeviceEntity>> = flow

    override suspend fun getByMac(mac: String): KnownDeviceEntity? =
        allDevices.find { it.macAddress == mac }

    override suspend fun getByIpWithoutMac(ip: String): KnownDeviceEntity? =
        allDevices.find { it.ip == ip && it.macAddress == null }

    override fun getUnknownDevices(): Flow<List<KnownDeviceEntity>> =
        flowOf(allDevices.filter { !it.isKnown })

    override suspend fun insertIfNew(device: KnownDeviceEntity): Long {
        if (device.macAddress != null && allDevices.any { it.macAddress == device.macAddress }) return -1L
        val withId = device.copy(id = nextId++)
        allDevices.add(withId)
        flow.update { allDevices.toList() }
        return withId.id
    }

    override suspend fun updateLastSeen(
        id: Long, hostname: String?, ip: String, vendor: String?,
        lastSeen: Long, deviceType: String?, osGuess: String?,
    ) {
        val i = allDevices.indexOfFirst { it.id == id }
        if (i >= 0) {
            allDevices[i] = allDevices[i].copy(
                hostname = hostname, ip = ip, vendor = vendor,
                lastSeen = lastSeen, deviceType = deviceType, osGuess = osGuess,
            )
            flow.update { allDevices.toList() }
        }
    }

    override suspend fun setMacAddress(id: Long, mac: String) {
        val i = allDevices.indexOfFirst { it.id == id }
        if (i >= 0) { allDevices[i] = allDevices[i].copy(macAddress = mac); flow.update { allDevices.toList() } }
    }

    override suspend fun setKnown(id: Long, isKnown: Boolean) {
        val i = allDevices.indexOfFirst { it.id == id }
        if (i >= 0) { allDevices[i] = allDevices[i].copy(isKnown = isKnown); flow.update { allDevices.toList() } }
    }

    override suspend fun setCustomName(id: Long, customName: String?) {
        val i = allDevices.indexOfFirst { it.id == id }
        if (i >= 0) { allDevices[i] = allDevices[i].copy(customName = customName); flow.update { allDevices.toList() } }
    }

    override fun getDevicesForNetwork(networkId: Long): Flow<List<KnownDeviceEntity>> =
        flowOf(allDevices.filter { it.networkId == networkId })

    override fun search(query: String): Flow<List<KnownDeviceEntity>> =
        flowOf(allDevices.filter { it.hostname?.contains(query) == true || it.ip.contains(query) })

    override suspend fun delete(id: Long) { allDevices.removeAll { it.id == id }; flow.update { allDevices.toList() } }

    override suspend fun deleteAll() { allDevices.clear(); flow.update { emptyList() } }
}
```

Create `core/scan/src/test/kotlin/com/ventouxlabs/netlens/core/scan/RecordingNewDeviceNotifier.kt`:

```kotlin
package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity

class RecordingNewDeviceNotifier : NewDeviceNotifier {
    val notified = mutableListOf<KnownDeviceEntity>()
    override fun createChannel() {}
    override fun notify(device: KnownDeviceEntity) { notified.add(device) }
}
```

Create `core/scan/src/test/kotlin/com/ventouxlabs/netlens/core/scan/FakeWatchedNetworkDao.kt` (used from Task H onward; add now so `core:scan` test sources are complete):

```kotlin
package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWatchedNetworkDao : WatchedNetworkDao {
    val networks = mutableListOf<WatchedNetworkEntity>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<WatchedNetworkEntity>>(emptyList())

    override fun observeAll(): Flow<List<WatchedNetworkEntity>> = flow

    override suspend fun getByGatewayMac(mac: String): WatchedNetworkEntity? =
        networks.find { it.gatewayMac == mac }

    override suspend fun upsert(network: WatchedNetworkEntity): Long {
        val existing = networks.indexOfFirst { it.gatewayMac == network.gatewayMac }
        return if (existing >= 0) {
            networks[existing] = network.copy(id = networks[existing].id)
            flow.value = networks.toList()
            networks[existing].id
        } else {
            val withId = network.copy(id = nextId++)
            networks.add(withId)
            flow.value = networks.toList()
            withId.id
        }
    }

    override suspend fun setWatchEnabled(id: Long, enabled: Boolean) {
        val i = networks.indexOfFirst { it.id == id }
        if (i >= 0) { networks[i] = networks[i].copy(watchEnabled = enabled); flow.value = networks.toList() }
    }

    override suspend fun delete(id: Long) { networks.removeAll { it.id == id }; flow.value = networks.toList() }
}
```

- [ ] **Step 3: Run the repository test to verify it fails**

Run: `./gradlew :core:scan:testDebugUnitTest --tests "*DeviceInventoryRepositoryTest*"`
Expected: FAIL — `DeviceInventoryRepositoryImpl` is unresolved.

- [ ] **Step 4: Implement the repository (extracted verbatim logic from `LanScanViewModel.persistScanResults`)**

Create `core/scan/src/main/kotlin/com/ventouxlabs/netlens/core/scan/DeviceInventoryRepository.kt`:

```kotlin
package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceInventoryRepository {
    /**
     * Persists a scan's devices into the known-device inventory, tagging each row with
     * [networkId] (null for ad-hoc foreground scans). Fires [NewDeviceNotifier] exactly
     * once per genuinely new row. Shared by foreground LanScan and the background watch.
     */
    suspend fun persistScan(devices: List<LanDevice>, networkId: Long?)
}

@Singleton
class DeviceInventoryRepositoryImpl @Inject constructor(
    private val knownDeviceDao: KnownDeviceDao,
    private val newDeviceNotifier: NewDeviceNotifier,
) : DeviceInventoryRepository {

    override suspend fun persistScan(devices: List<LanDevice>, networkId: Long?) {
        val now = System.currentTimeMillis()
        for (device in devices) {
            val mac = device.macAddress
            // Devices with no resolvable MAC still get an inventory row keyed by IP;
            // if a MAC later resolves for that IP, upgrade the row instead of duplicating.
            val existing = mac?.let { knownDeviceDao.getByMac(it) }
                ?: knownDeviceDao.getByIpWithoutMac(device.ip)
            if (existing != null) {
                if (mac != null && existing.macAddress == null) {
                    knownDeviceDao.setMacAddress(existing.id, mac)
                }
                knownDeviceDao.updateLastSeen(
                    id = existing.id,
                    hostname = device.hostname ?: existing.hostname,
                    ip = device.ip,
                    vendor = device.vendor ?: existing.vendor,
                    lastSeen = now,
                    deviceType = device.deviceType ?: existing.deviceType,
                    osGuess = device.osGuess ?: existing.osGuess,
                )
                if (networkId != null && existing.networkId != networkId) {
                    knownDeviceDao.setNetworkId(existing.id, networkId)
                }
            } else {
                val entity = KnownDeviceEntity(
                    macAddress = mac,
                    hostname = device.hostname,
                    ip = device.ip,
                    vendor = device.vendor,
                    firstSeen = now,
                    lastSeen = now,
                    isKnown = false,
                    deviceType = device.deviceType,
                    osGuess = device.osGuess,
                    networkId = networkId,
                )
                val insertResult = knownDeviceDao.insertIfNew(entity)
                if (insertResult != -1L) {
                    newDeviceNotifier.notify(entity.copy(id = insertResult))
                }
            }
        }
    }
}
```

Add `setNetworkId` to `KnownDeviceDao` (Task B added the other new queries; this one belongs with the repository that uses it). In `core/data/.../dao/KnownDeviceDao.kt`, add next to `setCustomName`:

```kotlin
    @Query("UPDATE known_devices SET networkId = :networkId WHERE id = :id")
    suspend fun setNetworkId(id: Long, networkId: Long?)
```

Add the matching override to `InMemoryKnownDeviceDao` (Step 2 file):

```kotlin
    override suspend fun setNetworkId(id: Long, networkId: Long?) {
        val i = allDevices.indexOfFirst { it.id == id }
        if (i >= 0) { allDevices[i] = allDevices[i].copy(networkId = networkId); flow.update { allDevices.toList() } }
    }
```

- [ ] **Step 5: Bind the repository in `ScanModule`**

In `core/scan/.../di/ScanModule.kt`, add the import and binding:

```kotlin
    @Binds
    @Singleton
    abstract fun bindDeviceInventoryRepository(
        impl: DeviceInventoryRepositoryImpl,
    ): DeviceInventoryRepository
```

(Add `import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository` / `DeviceInventoryRepositoryImpl`.)

- [ ] **Step 6: Run the repository test to verify it passes**

Run: `./gradlew :core:scan:testDebugUnitTest --tests "*DeviceInventoryRepositoryTest*"`
Expected: PASS (all four cases).

- [ ] **Step 7: Rewire `LanScanViewModel` to use the repository**

In `feature/lanscan/.../LanScanViewModel.kt`:
1. Add constructor param `private val deviceInventoryRepository: DeviceInventoryRepository,` (add `import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository`) and remove the `newDeviceNotifier: NewDeviceNotifier` param and its import — the notifier is now internal to the repository.
2. Delete the entire private `persistScanResults(...)` function (current lines 298–340).
3. Replace the call site `persistScanResults(_uiState.value.devices)` (in `startScan`) with:

```kotlin
            deviceInventoryRepository.persistScan(_uiState.value.devices, networkId = null)
```

- [ ] **Step 8: Update `DeviceInventoryTest` to the new ViewModel shape**

In `feature/lanscan/.../DeviceInventoryTest.kt`:
1. Replace the `newDeviceNotifier = fakeNotifier` constructor argument with `deviceInventoryRepository = DeviceInventoryRepositoryImpl(fakeKnownDeviceDao, fakeNotifier)` and add imports `com.ventouxlabs.netlens.core.scan.DeviceInventoryRepositoryImpl`. Keep `fakeNotifier` as a `RecordingNewDeviceNotifier` (this file already defines its own private `RecordingNewDeviceNotifier`; keep it, or import the `core:scan` one — either compiles).
2. Delete the four persist-mechanics cases now owned by `DeviceInventoryRepositoryTest`: `device without MAC is still persisted, keyed by IP`, `re-scanning the same mac-less device updates it instead of duplicating`, `mac-less device upgrades in place once a MAC resolves for its IP`, and `existing device updates lastSeen without notification`. Keep `new device with MAC is persisted after scan`, `new device triggers notification`, `toggleKnown flips isKnown flag`, `deleteDevice removes from inventory`, `multiple devices in single scan are all persisted` (these exercise the ViewModel→repository wiring end-to-end).
3. The private `InMemoryKnownDeviceDao` inside this test file needs the three new `KnownDeviceDao` overrides (`setCustomName`, `setNetworkId`, `getDevicesForNetwork`) — add them mirroring the `core:scan` fake from Step 2/Step 4.

- [ ] **Step 9: Run both module test suites**

Run: `./gradlew :core:scan:testDebugUnitTest :feature:lanscan:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat(scan): extract DeviceInventoryRepository and rewire LanScan"
```

---

### Task D: `feature:devices` scaffold + navigation

**Files:**
- Create: `feature/devices/build.gradle.kts`, `feature/devices/src/main/AndroidManifest.xml`, `feature/devices/src/main/res/values/strings.xml` (title only for now), and a placeholder `feature/devices/src/main/kotlin/com/ventouxlabs/netlens/feature/devices/DevicesScreen.kt`.
- Modify: `settings.gradle.kts`, `app/build.gradle.kts`, `app/.../navigation/ToolDestination.kt`, `app/.../navigation/NetLensNavHost.kt`, `app/.../navigation/DeepLinkRouter.kt`.

**Interfaces:**
- Consumes: nothing yet (placeholder screen).
- Produces: `ToolDestination.Devices` (route `"devices"`), a composable `DevicesScreen(onBack, onNavigateToTool)` mounted at that route and reachable via `netlens://feature/devices`.

- [ ] **Step 1: Register the module**

Add to `settings.gradle.kts` after `include(":feature:lanscan")`:

```kotlin
include(":feature:devices")
```

Create `feature/devices/build.gradle.kts`:

```kotlin
plugins {
    id("netlens.android.feature")
}

android {
    namespace = "com.ventouxlabs.netlens.feature.devices"
}

dependencies {
    implementation(project(":core:scan"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:oui"))
    implementation(libs.work.runtime)
    implementation(libs.core.ktx)
    implementation(libs.compose.material.icons)
}
```

Create `feature/devices/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

Create `feature/devices/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="devices_title">Devices</string>
    <string name="navigate_back">Back</string>
</resources>
```

- [ ] **Step 2: Create a minimal placeholder screen (replaced in Task F)**

Create `feature/devices/src/main/kotlin/com/ventouxlabs/netlens/feature/devices/DevicesScreen.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit = {},
    onNavigateToTool: (String, String) -> Unit = { _, _ -> },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.devices_title))
        }
    }
}
```

- [ ] **Step 3: Add the `ToolDestination.Devices` entry**

In `app/.../navigation/ToolDestination.kt`, add `import androidx.compose.material.icons.filled.Devices` and a new entry after `LanScan` (Discovery category):

```kotlin
    Devices(
        route = "devices",
        icon = Icons.Default.Devices,
        label = "Devices",
        description = "Inventory & watch alerts",
        category = ToolCategory.Discovery,
    ),
```

- [ ] **Step 4: Mount the screen in `NetLensNavHost` and add the deep-link mapping**

In `NetLensNavHost.kt`, add `import com.ventouxlabs.netlens.feature.devices.DevicesScreen` and a composable next to the LanScan entry:

```kotlin
        composable(ToolDestination.Devices.route) {
            DevicesScreen(
                onBack = navController::popBackStack,
                onNavigateToTool = navigateToTool,
            )
        }
```

In `DeepLinkRouter.kt`, add to `PATH_TO_ROUTE`:

```kotlin
    "devices" to ToolDestination.Devices.route,
```

- [ ] **Step 5: Add the module dependency to `app`**

In `app/build.gradle.kts`, add under the feature-module block after `:feature:lanscan`:

```kotlin
    implementation(project(":feature:devices"))
```

- [ ] **Step 6: Build the FOSS debug APK to verify wiring**

Run: `./gradlew assembleFossDebug`
Expected: BUILD SUCCESSFUL; the Devices tool compiles into the app and appears under Discovery.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(devices): scaffold feature:devices module and navigation"
```

---

### Task E: `DevicesViewModel` + `DevicesUiState` + core actions

**Files:**
- Create: `feature/devices/.../model/DevicesUiState.kt`, `model/DeviceDisplay.kt`, `model/WatchCadence.kt`
- Create: `feature/devices/.../NetworkIdentity.kt`
- Create: `feature/devices/.../DevicesViewModel.kt`
- Create: `feature/devices/src/test/kotlin/com/ventouxlabs/netlens/feature/devices/DevicesViewModelTest.kt`
- Create test fakes: `feature/devices/src/test/.../FakeNetworkIdentity.kt` (+ reuse `core:scan` DAO fakes via a local copy — see Step 2)

**Interfaces:**
- Consumes (Task B): `KnownDeviceDao` (`getAllDevices`, `setCustomName`, `setKnown`, `delete`), `WatchedNetworkDao` (`observeAll`, `upsert`, `setWatchEnabled`, `delete`), `KnownDeviceEntity` (with `customName`, `networkId`), `WatchedNetworkEntity`.
- Produces:
  - `DevicesUiState(devices: List<KnownDeviceEntity>, searchQuery: String, watchedNetworks: List<WatchedNetworkEntity>, cadence: WatchCadence, masterWatchEnabled: Boolean, selectedDeviceId: Long?)`
  - `WatchCadence` enum: `FIFTEEN_MIN(15)`, `THIRTY_MIN(30)`, `ONE_HOUR(60)`, `SIX_HOURS(360)`; `companion fun fromMinutes(m: Int): WatchCadence` (default `ONE_HOUR`).
  - `KnownDeviceEntity.displayName(): String` extension = `customName ?: hostname ?: vendor ?: ip`.
  - `NetworkIdentity { suspend fun currentGatewayMac(): String?; fun currentSubnet(): String?; fun currentSsid(): String? }` (impl added Task H; a `FakeNetworkIdentity` used here).
  - `DevicesViewModel` actions: `setSearchQuery(q)`, `rename(id, name)`, `toggleKnown(id)`, `delete(id)`, `selectDevice(id?)`, `watchCurrentNetwork()`, `toggleNetworkWatch(id, enabled)`, `removeWatchedNetwork(id)`.

- [ ] **Step 1: Write the failing ViewModel test**

Create `feature/devices/src/test/kotlin/com/ventouxlabs/netlens/feature/devices/DevicesViewModelTest.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import app.cash.turbine.test
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {

    private lateinit var knownDao: FakeKnownDeviceDao
    private lateinit var watchedDao: FakeWatchedNetworkDao
    private lateinit var identity: FakeNetworkIdentity
    private lateinit var viewModel: DevicesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        knownDao = FakeKnownDeviceDao()
        watchedDao = FakeWatchedNetworkDao()
        identity = FakeNetworkIdentity()
        viewModel = DevicesViewModel(knownDao, watchedDao, identity)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `search filters by display name`() = runTest {
        knownDao.seed(KnownDeviceEntity(macAddress = "M1", hostname = "printer", ip = "192.168.1.2", vendor = null))
        knownDao.seed(KnownDeviceEntity(macAddress = "M2", hostname = "laptop", ip = "192.168.1.3", vendor = null))
        viewModel.setSearchQuery("print")
        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertEquals(1, state.devices.size)
            assertEquals("printer", state.devices.first().hostname)
        }
    }

    @Test
    fun `rename persists a trimmed custom name`() = runTest {
        knownDao.seed(KnownDeviceEntity(id = 5, macAddress = "M1", hostname = "h", ip = "192.168.1.2", vendor = null))
        viewModel.rename(5, "  Living Room TV  ")
        assertEquals("Living Room TV", knownDao.byId(5)?.customName)
    }

    @Test
    fun `rename with blank clears the custom name`() = runTest {
        knownDao.seed(KnownDeviceEntity(id = 5, macAddress = "M1", hostname = "h", ip = "192.168.1.2", vendor = null, customName = "Old"))
        viewModel.rename(5, "   ")
        assertNull(knownDao.byId(5)?.customName)
    }

    @Test
    fun `toggleKnown flips the flag`() = runTest {
        knownDao.seed(KnownDeviceEntity(id = 7, macAddress = "M1", hostname = "h", ip = "192.168.1.2", vendor = null, isKnown = false))
        viewModel.toggleKnown(7)
        assertTrue(knownDao.byId(7)?.isKnown == true)
    }

    @Test
    fun `watchCurrentNetwork captures gateway identity into a watched network`() = runTest {
        identity.gatewayMac = "AA:BB:CC:DD:EE:FF"
        identity.subnet = "192.168.1.0/24"
        identity.ssid = "HomeWiFi"
        viewModel.watchCurrentNetwork()
        val watched = watchedDao.getByGatewayMac("AA:BB:CC:DD:EE:FF")
        assertEquals("HomeWiFi", watched?.displayName)
        assertEquals("192.168.1.0/24", watched?.subnet)
    }

    @Test
    fun `watchCurrentNetwork is a no-op when gateway is unresolvable`() = runTest {
        identity.gatewayMac = null
        viewModel.watchCurrentNetwork()
        assertTrue(watchedDao.networks.isEmpty())
    }
}
```

- [ ] **Step 2: Add the test fakes for `feature:devices`**

Create `feature/devices/src/test/kotlin/com/ventouxlabs/netlens/feature/devices/FakeNetworkIdentity.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

class FakeNetworkIdentity : NetworkIdentity {
    var gatewayMac: String? = null
    var subnet: String? = null
    var ssid: String? = null
    override suspend fun currentGatewayMac(): String? = gatewayMac
    override fun currentSubnet(): String? = subnet
    override fun currentSsid(): String? = ssid
}
```

Create `feature/devices/src/test/kotlin/com/ventouxlabs/netlens/feature/devices/FakeKnownDeviceDao.kt` (a Flow-backed fake exposing `getAllDevices` reactively so the ViewModel's `combine` emits):

```kotlin
package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeKnownDeviceDao : KnownDeviceDao {
    private val devices = mutableListOf<KnownDeviceEntity>()
    private val flow = MutableStateFlow<List<KnownDeviceEntity>>(emptyList())

    fun seed(device: KnownDeviceEntity) { devices.add(device); flow.value = devices.toList() }
    fun byId(id: Long): KnownDeviceEntity? = devices.find { it.id == id }

    override fun getAllDevices(): Flow<List<KnownDeviceEntity>> = flow
    override suspend fun getByMac(mac: String): KnownDeviceEntity? = devices.find { it.macAddress == mac }
    override suspend fun getByIpWithoutMac(ip: String): KnownDeviceEntity? = devices.find { it.ip == ip && it.macAddress == null }
    override fun getUnknownDevices(): Flow<List<KnownDeviceEntity>> = flowOf(devices.filter { !it.isKnown })
    override suspend fun insertIfNew(device: KnownDeviceEntity): Long { devices.add(device); flow.value = devices.toList(); return device.id }
    override suspend fun updateLastSeen(id: Long, hostname: String?, ip: String, vendor: String?, lastSeen: Long, deviceType: String?, osGuess: String?) {}
    override suspend fun setMacAddress(id: Long, mac: String) {}
    override suspend fun setKnown(id: Long, isKnown: Boolean) { update(id) { it.copy(isKnown = isKnown) } }
    override suspend fun setCustomName(id: Long, customName: String?) { update(id) { it.copy(customName = customName) } }
    override suspend fun setNetworkId(id: Long, networkId: Long?) { update(id) { it.copy(networkId = networkId) } }
    override fun getDevicesForNetwork(networkId: Long): Flow<List<KnownDeviceEntity>> = flowOf(devices.filter { it.networkId == networkId })
    override fun search(query: String): Flow<List<KnownDeviceEntity>> = flowOf(devices)
    override suspend fun delete(id: Long) { devices.removeAll { it.id == id }; flow.value = devices.toList() }
    override suspend fun deleteAll() { devices.clear(); flow.value = emptyList() }

    private fun update(id: Long, transform: (KnownDeviceEntity) -> KnownDeviceEntity) {
        val i = devices.indexOfFirst { it.id == id }
        if (i >= 0) { devices[i] = transform(devices[i]); flow.value = devices.toList() }
    }
}
```

Create `feature/devices/src/test/kotlin/com/ventouxlabs/netlens/feature/devices/FakeWatchedNetworkDao.kt` — identical body to the `core:scan` fake from Task C Step 2 but in package `com.ventouxlabs.netlens.feature.devices` (copy it; test fakes are not shared across module test source sets).

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :feature:devices:testDebugUnitTest --tests "*DevicesViewModelTest*"`
Expected: FAIL — `DevicesViewModel`, `NetworkIdentity`, `WatchCadence` unresolved.

- [ ] **Step 4: Create the models and `NetworkIdentity` interface**

Create `feature/devices/.../model/WatchCadence.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices.model

enum class WatchCadence(val minutes: Int) {
    FIFTEEN_MIN(15),
    THIRTY_MIN(30),
    ONE_HOUR(60),
    SIX_HOURS(360),
    ;

    companion object {
        val DEFAULT = ONE_HOUR
        fun fromMinutes(minutes: Int): WatchCadence =
            entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}
```

Create `feature/devices/.../model/DeviceDisplay.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices.model

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity

/** Display-name precedence: customName ?: hostname ?: vendor ?: ip. */
fun KnownDeviceEntity.displayName(): String =
    customName ?: hostname ?: vendor ?: ip
```

Create `feature/devices/.../model/DevicesUiState.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices.model

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity

data class DevicesUiState(
    val devices: List<KnownDeviceEntity> = emptyList(),
    val searchQuery: String = "",
    val watchedNetworks: List<WatchedNetworkEntity> = emptyList(),
    val cadence: WatchCadence = WatchCadence.DEFAULT,
    val masterWatchEnabled: Boolean = false,
    val selectedDeviceId: Long? = null,
)
```

Create `feature/devices/.../NetworkIdentity.kt` (interface only for now; impl in Task H):

```kotlin
package com.ventouxlabs.netlens.feature.devices

/**
 * Resolves the current network's identity. Gateway MAC + subnet come from
 * LinkProperties + ARP (no location permission). SSID is display-only and read
 * only in the foreground where ACCESS_FINE_LOCATION already exists.
 */
interface NetworkIdentity {
    suspend fun currentGatewayMac(): String?
    fun currentSubnet(): String?
    fun currentSsid(): String?
}
```

- [ ] **Step 5: Implement `DevicesViewModel`**

Create `feature/devices/.../DevicesViewModel.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import com.ventouxlabs.netlens.feature.devices.model.DevicesUiState
import com.ventouxlabs.netlens.feature.devices.model.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_NAME_LENGTH = 60

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val knownDeviceDao: KnownDeviceDao,
    private val watchedNetworkDao: WatchedNetworkDao,
    private val networkIdentity: NetworkIdentity,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedDeviceId = MutableStateFlow<Long?>(null)

    private val devicesFlow = combine(
        knownDeviceDao.getAllDevices(),
        _searchQuery,
    ) { devices, query ->
        if (query.isBlank()) devices
        else devices.filter { it.displayName().contains(query, ignoreCase = true) || it.ip.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                devicesFlow,
                _searchQuery,
                _selectedDeviceId,
                watchedNetworkDao.observeAll(),
            ) { devices, query, selectedId, watched ->
                DevicesUiState(
                    devices = devices,
                    searchQuery = query,
                    watchedNetworks = watched,
                    selectedDeviceId = selectedId,
                )
            }.collect { next ->
                // Preserve cadence/masterWatchEnabled, which Task G folds in from preferences.
                _uiState.update {
                    next.copy(cadence = it.cadence, masterWatchEnabled = it.masterWatchEnabled)
                }
            }
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun selectDevice(id: Long?) { _selectedDeviceId.value = id }

    fun rename(id: Long, rawName: String) {
        val trimmed = rawName.trim().take(MAX_NAME_LENGTH)
        viewModelScope.launch {
            knownDeviceDao.setCustomName(id, trimmed.ifBlank { null })
        }
    }

    fun toggleKnown(id: Long) {
        viewModelScope.launch {
            val device = uiState.value.devices.find { it.id == id } ?: return@launch
            knownDeviceDao.setKnown(id, !device.isKnown)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { knownDeviceDao.delete(id) }
    }

    fun watchCurrentNetwork() {
        viewModelScope.launch {
            val gatewayMac = networkIdentity.currentGatewayMac() ?: return@launch
            val subnet = networkIdentity.currentSubnet() ?: return@launch
            watchedNetworkDao.upsert(
                WatchedNetworkEntity(
                    displayName = networkIdentity.currentSsid(),
                    gatewayMac = gatewayMac,
                    subnet = subnet,
                    watchEnabled = true,
                ),
            )
        }
    }

    fun toggleNetworkWatch(id: Long, enabled: Boolean) {
        viewModelScope.launch { watchedNetworkDao.setWatchEnabled(id, enabled) }
    }

    fun removeWatchedNetwork(id: Long) {
        viewModelScope.launch { watchedNetworkDao.delete(id) }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :feature:devices:testDebugUnitTest --tests "*DevicesViewModelTest*"`
Expected: PASS (all six cases).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(devices): DevicesViewModel with inventory + watch-capture actions"
```

---

### Task F: `DevicesScreen` UI — list, search, detail sheet, strings

**Files:**
- Replace: `feature/devices/.../DevicesScreen.kt` (full UI)
- Create: `feature/devices/.../DeviceDetailSheet.kt`
- Modify: `feature/devices/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes (Task E): `DevicesViewModel` (`uiState`, `setSearchQuery`, `rename`, `toggleKnown`, `delete`, `selectDevice`), `DevicesUiState`, `KnownDeviceEntity.displayName()`.
- Produces: a fully rendered inventory screen; a `DeviceDetailSheet` reachable via `selectedDeviceId`.

- [ ] **Step 1: Add the screen strings**

Replace `feature/devices/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="devices_title">Devices</string>
    <string name="navigate_back">Back</string>
    <string name="devices_search_placeholder">Search devices…</string>
    <string name="devices_clear_search">Clear search</string>
    <string name="devices_section_new">New</string>
    <string name="devices_section_known">Known</string>
    <string name="devices_empty">No devices yet.\nRun a LAN scan or enable watch to build your inventory.</string>
    <string name="devices_no_results">No devices match your search</string>
    <string name="devices_mac_unknown">MAC unknown</string>
    <string name="devices_first_seen">First: %1$s</string>
    <string name="devices_last_seen">Last: %1$s</string>
    <string name="devices_detail_rename">Custom name</string>
    <string name="devices_detail_rename_hint">Friendly name (optional)</string>
    <string name="devices_detail_mark_known">Mark as known</string>
    <string name="devices_detail_mark_unknown">Mark as unknown</string>
    <string name="devices_detail_vendor">Vendor: %1$s</string>
    <string name="devices_detail_type">Type: %1$s</string>
    <string name="devices_detail_os">OS: %1$s</string>
    <string name="devices_detail_delete">Delete device</string>
    <string name="devices_detail_save">Save</string>
    <string name="devices_detail_close">Close</string>
    <string name="devices_cd_copy_results">Copy results</string>
    <string name="devices_cd_share">Share results</string>
</resources>
```

- [ ] **Step 2: Implement the detail bottom sheet**

Create `feature/devices/.../DeviceDetailSheet.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.feature.devices.model.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailSheet(
    device: KnownDeviceEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onToggleKnown: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        var name by remember(device.id) { mutableStateOf(device.customName ?: "") }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(device.displayName(), style = MaterialTheme.typography.titleLarge)
            Text(device.ip, style = MaterialTheme.typography.labelSmall)
            Text(
                device.macAddress ?: stringResource(R.string.devices_mac_unknown),
                style = MaterialTheme.typography.labelSmall,
            )
            device.vendor?.let { Text(stringResource(R.string.devices_detail_vendor, it)) }
            device.deviceType?.let { Text(stringResource(R.string.devices_detail_type, it)) }
            device.osGuess?.let { Text(stringResource(R.string.devices_detail_os, it)) }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.devices_detail_rename)) },
                placeholder = { Text(stringResource(R.string.devices_detail_rename_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onRename(name); onDismiss() }) {
                    Text(stringResource(R.string.devices_detail_save))
                }
                OutlinedButton(onClick = onToggleKnown) {
                    Text(
                        if (device.isKnown) stringResource(R.string.devices_detail_mark_unknown)
                        else stringResource(R.string.devices_detail_mark_known),
                    )
                }
            }
            OutlinedButton(onClick = { onDelete(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.devices_detail_delete))
            }
        }
    }
}
```

- [ ] **Step 3: Replace `DevicesScreen` with the list UI**

Replace `feature/devices/.../DevicesScreen.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.feature.devices.model.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit = {},
    onNavigateToTool: (String, String) -> Unit = { _, _ -> },
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selected = uiState.devices.find { it.id == uiState.selectedDeviceId }

    if (selected != null) {
        DeviceDetailSheet(
            device = selected,
            onDismiss = { viewModel.selectDevice(null) },
            onRename = { viewModel.rename(selected.id, it) },
            onToggleKnown = { viewModel.toggleKnown(selected.id) },
            onDelete = { viewModel.delete(selected.id) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text(stringResource(R.string.devices_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, stringResource(R.string.devices_clear_search))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            val newDevices = uiState.devices.filter { !it.isKnown }
            val knownDevices = uiState.devices.filter { it.isKnown }

            if (uiState.devices.isEmpty()) {
                Text(
                    if (uiState.searchQuery.isBlank()) stringResource(R.string.devices_empty)
                    else stringResource(R.string.devices_no_results),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (newDevices.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.devices_section_new)) }
                        items(newDevices, key = { it.id }) { DeviceRow(it) { viewModel.selectDevice(it.id) } }
                    }
                    if (knownDevices.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.devices_section_known)) }
                        items(knownDevices, key = { it.id }) { DeviceRow(it) { viewModel.selectDevice(it.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceRow(device: KnownDeviceEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(device.displayName()) },
        supportingContent = {
            Text(
                "${device.ip}  ·  ${device.macAddress ?: stringResource(R.string.devices_mac_unknown)}",
                style = MaterialTheme.typography.labelSmall,
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
    HorizontalDivider()
}
```

Note: the `ListItem` row uses `MaterialTheme.typography.labelSmall`, which the theme maps to `MonoFontFamily` — this satisfies the typography rule for IP/MAC without any per-call font override.

- [ ] **Step 4: Build to verify the UI compiles**

Run: `./gradlew :feature:devices:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the devices tests (regression on the ViewModel)**

Run: `./gradlew :feature:devices:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `DevicesViewModelTest` still passes.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(devices): inventory list, search, and device detail sheet"
```

---

### Task G: Watch settings UI + preferences (Pro-gated)

**Files:**
- Modify: `core/data/.../preferences/UserPreferencesRepository.kt` (cadence + master-toggle)
- Modify: `feature/devices/.../DevicesViewModel.kt` (fold prefs into state; add `setCadence`, `setMasterWatch`)
- Create: `feature/devices/.../WatchScheduler.kt` (interface + `ScheduleAction`; impl in Task I)
- Modify: `feature/devices/.../DevicesScreen.kt` (watch section, Pro-gated)
- Modify: `feature/devices/src/main/res/values/strings.xml`
- Modify: `feature/devices/.../DevicesViewModelTest.kt` (cadence/master-toggle + scheduler-call cases); create `feature/devices/src/test/.../FakeUserPreferences...` shim and `RecordingWatchScheduler`
- Modify: `core/data/.../preferences/UserPreferencesRepositoryTest.kt` (new prefs round-trip)

**Interfaces:**
- Consumes (Task E): `DevicesViewModel`, `DevicesUiState`, `WatchCadence`. `LocalProStatus` (core:billing).
- Produces:
  - `UserPreferencesRepository`: `val watchCadenceMinutes: Flow<Int>` (default 60), `suspend fun setWatchCadenceMinutes(minutes: Int)`, `val watchMasterEnabled: Flow<Boolean>` (default false), `suspend fun setWatchMasterEnabled(enabled: Boolean)`.
  - `WatchScheduler { fun apply(isPro: Boolean, masterEnabled: Boolean, cadence: WatchCadence) }` and pure `computeScheduleAction(isPro, masterEnabled, cadence): ScheduleAction` where `ScheduleAction = Enqueue(cadence: WatchCadence) | Cancel`.
  - `DevicesViewModel` gains `setCadence(WatchCadence)` and `setMasterWatch(Boolean)`, both persisting to prefs; `DevicesScreen` calls `WatchScheduler.apply(...)` on change (via a passed lambda, so the ViewModel stays free of `isPro`).

- [ ] **Step 1: Add a failing preferences round-trip test**

In `core/data/.../preferences/UserPreferencesRepositoryTest.kt`, add (follow the file's existing DataStore-under-test setup):

```kotlin
    @Test
    fun `watch cadence round-trips and defaults to 60`() = runTest {
        assertEquals(60, repository.watchCadenceMinutes.first())
        repository.setWatchCadenceMinutes(30)
        assertEquals(30, repository.watchCadenceMinutes.first())
    }

    @Test
    fun `watch master toggle round-trips and defaults to false`() = runTest {
        assertEquals(false, repository.watchMasterEnabled.first())
        repository.setWatchMasterEnabled(true)
        assertEquals(true, repository.watchMasterEnabled.first())
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*UserPreferencesRepositoryTest*"`
Expected: FAIL — `watchCadenceMinutes` / `watchMasterEnabled` unresolved.

- [ ] **Step 3: Add the preference accessors**

In `UserPreferencesRepository.kt`, add reads:

```kotlin
    val watchCadenceMinutes: Flow<Int> = dataStore.data.map { it[WATCH_CADENCE_MINUTES] ?: DEFAULT_WATCH_CADENCE_MINUTES }
    val watchMasterEnabled: Flow<Boolean> = dataStore.data.map { it[WATCH_MASTER_ENABLED] ?: false }

    suspend fun setWatchCadenceMinutes(minutes: Int) {
        dataStore.edit { it[WATCH_CADENCE_MINUTES] = minutes }
    }

    suspend fun setWatchMasterEnabled(enabled: Boolean) {
        dataStore.edit { it[WATCH_MASTER_ENABLED] = enabled }
    }
```

In the `companion object`, add:

```kotlin
        private val WATCH_CADENCE_MINUTES = intPreferencesKey("watch_cadence_minutes")
        private val WATCH_MASTER_ENABLED = booleanPreferencesKey("watch_master_enabled")
        const val DEFAULT_WATCH_CADENCE_MINUTES = 60
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests "*UserPreferencesRepositoryTest*"`
Expected: PASS.

- [ ] **Step 5: Create the `WatchScheduler` interface + pure decision function**

Create `feature/devices/.../WatchScheduler.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.feature.devices.model.WatchCadence

sealed interface ScheduleAction {
    data class Enqueue(val cadence: WatchCadence) : ScheduleAction
    data object Cancel : ScheduleAction
}

/**
 * Pure scheduling decision: background watch runs only when the user is Pro AND the
 * master toggle is on. Any other combination cancels the periodic work.
 */
fun computeScheduleAction(isPro: Boolean, masterEnabled: Boolean, cadence: WatchCadence): ScheduleAction =
    if (isPro && masterEnabled) ScheduleAction.Enqueue(cadence) else ScheduleAction.Cancel

interface WatchScheduler {
    /** Enqueues or cancels the periodic watch based on [computeScheduleAction]. */
    fun apply(isPro: Boolean, masterEnabled: Boolean, cadence: WatchCadence)
}
```

- [ ] **Step 6: Fold prefs into `DevicesViewModel` and add the actions**

In `DevicesViewModel.kt`:
1. Add constructor param `private val userPreferences: UserPreferencesRepository,` (import `com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository`).
2. In `init`, add a second collector that folds cadence + master toggle into state:

```kotlin
        viewModelScope.launch {
            combine(
                userPreferences.watchCadenceMinutes,
                userPreferences.watchMasterEnabled,
            ) { minutes, master ->
                WatchCadence.fromMinutes(minutes) to master
            }.collect { (cadence, master) ->
                _uiState.update { it.copy(cadence = cadence, masterWatchEnabled = master) }
            }
        }
```

   (Import `com.ventouxlabs.netlens.feature.devices.model.WatchCadence`, `kotlinx.coroutines.flow.combine` already present.)
3. Add the actions:

```kotlin
    fun setCadence(cadence: WatchCadence) {
        viewModelScope.launch { userPreferences.setWatchCadenceMinutes(cadence.minutes) }
    }

    fun setMasterWatch(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setWatchMasterEnabled(enabled) }
    }
```

- [ ] **Step 7: Add the watch section to `DevicesScreen` (Pro-gated, pattern 1)**

Add the watch-section strings to `feature/devices/src/main/res/values/strings.xml`:

```xml
    <string name="devices_watch_section">Network Watch</string>
    <string name="devices_watch_master">Background new-device alerts</string>
    <string name="devices_watch_this_network">Watch this network</string>
    <string name="devices_watch_cadence">Check every</string>
    <string name="devices_watch_cadence_15">15 min</string>
    <string name="devices_watch_cadence_30">30 min</string>
    <string name="devices_watch_cadence_60">1 hour</string>
    <string name="devices_watch_cadence_360">6 hours</string>
    <string name="devices_watch_pro_upsell">Background watch is a Pro feature. Upgrade to get alerts when a new device joins your network.</string>
    <string name="devices_watch_remove">Remove</string>
    <string name="devices_watch_notif_prompt">Enable notifications to receive new-device alerts.</string>
```

In `DevicesScreen`, read Pro status (pattern 1) and inject the scheduler at the screen level. Add near the top of the composable:

```kotlin
    val proStatus = com.ventouxlabs.netlens.core.billing.LocalProStatus.current
    val isPro by proStatus.isPro.collectAsStateWithLifecycle()
```

Then render a `WatchSection` composable at the top of the `LazyColumn` (or above it), passing `isPro`, `uiState.watchedNetworks`, `uiState.cadence`, `uiState.masterWatchEnabled`, and callbacks `viewModel::watchCurrentNetwork`, `viewModel::toggleNetworkWatch`, `viewModel::removeWatchedNetwork`, and:

```kotlin
        onMasterToggle = { enabled ->
            viewModel.setMasterWatch(enabled)
            watchScheduler.apply(isPro, enabled, uiState.cadence)
        },
        onCadenceChange = { cadence ->
            viewModel.setCadence(cadence)
            watchScheduler.apply(isPro, uiState.masterWatchEnabled, cadence)
        },
```

`watchScheduler` is obtained via `hiltViewModel`-free access — inject it by adding it to a small `@Composable` entry point OR (simpler, matching this repo's DI style) pass it in from the ViewModel. To keep the ViewModel free of `isPro`, expose the scheduler through the ViewModel as a plain field:

Add to `DevicesViewModel` constructor `private val watchScheduler: WatchScheduler,` and a passthrough:

```kotlin
    fun applySchedule(isPro: Boolean) {
        watchScheduler.apply(isPro, uiState.value.masterWatchEnabled, uiState.value.cadence)
    }
```

Then the screen calls `viewModel.setMasterWatch(enabled)` / `viewModel.setCadence(cadence)` followed by `viewModel.applySchedule(isPro)`. When `!isPro`, render the upsell row (`devices_watch_pro_upsell`) instead of the interactive controls. Full `WatchSection` composable:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchSection(
    isPro: Boolean,
    watchedNetworks: List<com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity>,
    cadence: com.ventouxlabs.netlens.feature.devices.model.WatchCadence,
    masterEnabled: Boolean,
    onWatchThisNetwork: () -> Unit,
    onToggleNetwork: (Long, Boolean) -> Unit,
    onRemoveNetwork: (Long) -> Unit,
    onMasterToggle: (Boolean) -> Unit,
    onCadenceChange: (com.ventouxlabs.netlens.feature.devices.model.WatchCadence) -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        androidx.compose.ui.Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.devices_watch_section), style = MaterialTheme.typography.titleMedium)
        if (!isPro) {
            Text(stringResource(R.string.devices_watch_pro_upsell), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        androidx.compose.foundation.layout.Row(
            androidx.compose.ui.Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.devices_watch_master))
            androidx.compose.material3.Switch(checked = masterEnabled, onCheckedChange = onMasterToggle)
        }
        Text(stringResource(R.string.devices_watch_cadence), style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            com.ventouxlabs.netlens.feature.devices.model.WatchCadence.entries.forEach { option ->
                androidx.compose.material3.FilterChip(
                    selected = option == cadence,
                    onClick = { onCadenceChange(option) },
                    label = { Text(cadenceLabel(option)) },
                )
            }
        }
        androidx.compose.material3.OutlinedButton(onClick = onWatchThisNetwork, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.devices_watch_this_network))
        }
        watchedNetworks.forEach { network ->
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(network.displayName ?: network.gatewayMac, style = MaterialTheme.typography.labelSmall)
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(
                        checked = network.watchEnabled,
                        onCheckedChange = { onToggleNetwork(network.id, it) },
                    )
                    androidx.compose.material3.TextButton(onClick = { onRemoveNetwork(network.id) }) {
                        Text(stringResource(R.string.devices_watch_remove))
                    }
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun cadenceLabel(cadence: com.ventouxlabs.netlens.feature.devices.model.WatchCadence): String =
    when (cadence) {
        com.ventouxlabs.netlens.feature.devices.model.WatchCadence.FIFTEEN_MIN -> stringResource(R.string.devices_watch_cadence_15)
        com.ventouxlabs.netlens.feature.devices.model.WatchCadence.THIRTY_MIN -> stringResource(R.string.devices_watch_cadence_30)
        com.ventouxlabs.netlens.feature.devices.model.WatchCadence.ONE_HOUR -> stringResource(R.string.devices_watch_cadence_60)
        com.ventouxlabs.netlens.feature.devices.model.WatchCadence.SIX_HOURS -> stringResource(R.string.devices_watch_cadence_360)
    }
```

Mount `WatchSection(...)` above the device list (between the search field and the `LazyColumn`), wiring the callbacks as described.

- [ ] **Step 8: Add the ViewModel scheduler-call test**

In `DevicesViewModelTest.kt`, add a `RecordingWatchScheduler` fake and cases. Create `feature/devices/src/test/.../RecordingWatchScheduler.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.feature.devices.model.WatchCadence

class RecordingWatchScheduler : WatchScheduler {
    data class Call(val isPro: Boolean, val masterEnabled: Boolean, val cadence: WatchCadence)
    val calls = mutableListOf<Call>()
    override fun apply(isPro: Boolean, masterEnabled: Boolean, cadence: WatchCadence) {
        calls.add(Call(isPro, masterEnabled, cadence))
    }
}
```

Update the test `setUp()` to construct the ViewModel with the extra params (add a `FakeUserPreferences` — since `UserPreferencesRepository` is a concrete class backed by DataStore, wrap a real in-memory `DataStore` the same way `UserPreferencesRepositoryTest` does, OR extract the two watch flows behind a tiny interface if simpler; the minimal path is to reuse the DataStore-under-test builder from `UserPreferencesRepositoryTest`). Add cases:

```kotlin
    @Test
    fun `setCadence persists the cadence`() = runTest {
        viewModel.setCadence(WatchCadence.SIX_HOURS)
        assertEquals(360, userPreferences.watchCadenceMinutes.first())
    }

    @Test
    fun `applySchedule enqueues only when pro and master enabled`() = runTest {
        // computeScheduleAction is unit-tested separately; here assert the scheduler is invoked.
        viewModel.setMasterWatch(true)
        viewModel.applySchedule(isPro = true)
        assertTrue(scheduler.calls.any { it.isPro && it.masterEnabled })
    }
```

Add a pure-function test in a new file `feature/devices/src/test/.../ScheduleActionTest.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScheduleActionTest {
    @Test
    fun `enqueue only when pro and enabled`() {
        assertEquals(ScheduleAction.Enqueue(WatchCadence.ONE_HOUR), computeScheduleAction(true, true, WatchCadence.ONE_HOUR))
        assertEquals(ScheduleAction.Cancel, computeScheduleAction(false, true, WatchCadence.ONE_HOUR))
        assertEquals(ScheduleAction.Cancel, computeScheduleAction(true, false, WatchCadence.ONE_HOUR))
        assertEquals(ScheduleAction.Cancel, computeScheduleAction(false, false, WatchCadence.ONE_HOUR))
    }
}
```

- [ ] **Step 9: Run the tests**

Run: `./gradlew :core:data:testDebugUnitTest :feature:devices:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; new preference, cadence, and `ScheduleAction` cases pass.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat(devices): Pro-gated watch settings and cadence preferences"
```

---

### Task H: `WatchRunner` + `NetworkIdentity` impl + tests

**Files:**
- Create: `feature/devices/.../WatchRunner.kt` (+ `WatchOutcome`)
- Create: `feature/devices/.../NetworkIdentityImpl.kt`
- Modify: `feature/devices/.../di/DevicesModule.kt` (bind `NetworkIdentity`, `WatchScheduler` — scheduler impl lands in Task I; create the module now with `NetworkIdentity` binding)
- Create: `feature/devices/src/test/.../WatchRunnerTest.kt`, plus fakes: reuse `FakeNetworkIdentity`, `FakeWatchedNetworkDao`, and add `FakeSubnetScanner`, `FakeArpTableReader`, `FakeOuiLookup`, `RecordingDeviceInventoryRepository`.

**Interfaces:**
- Consumes (Task A): `SubnetScanner`, `ArpTableReader`, `DeviceInventoryRepository`, `model.LanDevice`. (Task B): `WatchedNetworkDao.getByGatewayMac`. (Task E): `NetworkIdentity`. `OuiLookup` (core:oui).
- Produces:
  - `WatchRunner { suspend fun run(): WatchOutcome }`
  - `WatchOutcome = NoGateway | NotWatched | Swept(deviceCount: Int)` (sealed).
  - Behavior: resolve gateway MAC → if null → `NoGateway`; look up watched network → if null or `!watchEnabled` → `NotWatched`; else lite-sweep the watched subnet (ping + ARP + OUI only), tag with `networkId`, persist via `DeviceInventoryRepository`, return `Swept(count)`.
  - `NetworkIdentityImpl` resolving gateway MAC via `ConnectivityManager` `LinkProperties` default-route gateway + `ArpTableReader`, subnet via link addresses, SSID via `WifiManager`.

- [ ] **Step 1: Write the failing `WatchRunner` test**

Create `feature/devices/src/test/kotlin/com/ventouxlabs/netlens/feature/devices/WatchRunnerTest.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.data.model.WatchedNetworkEntity
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchRunnerTest {

    private fun runner(
        identity: FakeNetworkIdentity,
        watchedDao: FakeWatchedNetworkDao,
        subnetScanner: FakeSubnetScanner = FakeSubnetScanner(),
        arp: FakeArpTableReader = FakeArpTableReader(),
        oui: FakeOuiLookup = FakeOuiLookup(),
        repo: RecordingDeviceInventoryRepository = RecordingDeviceInventoryRepository(),
    ) = WatchRunner(identity, watchedDao, subnetScanner, arp, oui, repo) to repo

    @Test
    fun `unresolvable gateway is a no-op`() = runTest {
        val identity = FakeNetworkIdentity().apply { gatewayMac = null }
        val (r, repo) = runner(identity, FakeWatchedNetworkDao())
        assertEquals(WatchOutcome.NoGateway, r.run())
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `gateway not in watched set is a no-op`() = runTest {
        val identity = FakeNetworkIdentity().apply { gatewayMac = "AA:BB:CC:DD:EE:FF"; subnet = "192.168.1.0/24" }
        val (r, repo) = runner(identity, FakeWatchedNetworkDao())
        assertEquals(WatchOutcome.NotWatched, r.run())
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `watched network sweep persists tagged devices`() = runTest {
        val identity = FakeNetworkIdentity().apply { gatewayMac = "AA:BB:CC:DD:EE:FF"; subnet = "192.168.1.0/24" }
        val watchedDao = FakeWatchedNetworkDao().apply {
            upsertBlocking(WatchedNetworkEntity(id = 3, displayName = "Home", gatewayMac = "AA:BB:CC:DD:EE:FF", subnet = "192.168.1.0/24", watchEnabled = true))
        }
        val subnetScanner = FakeSubnetScanner().apply { devices = listOf(LanDevice(ip = "192.168.1.10")) }
        val arp = FakeArpTableReader().apply { table = mapOf("192.168.1.10" to "11:22:33:44:55:66") }
        val oui = FakeOuiLookup().apply { vendors = mapOf("11:22:33:44:55:66" to "Acme") }
        val (r, repo) = runner(identity, watchedDao, subnetScanner, arp, oui)
        val outcome = r.run()
        assertEquals(WatchOutcome.Swept(1), outcome)
        assertEquals(1, repo.calls.size)
        assertEquals(3L, repo.calls.first().networkId)
        assertEquals("11:22:33:44:55:66", repo.calls.first().devices.first().macAddress)
        assertEquals("Acme", repo.calls.first().devices.first().vendor)
    }

    @Test
    fun `watched but disabled network is a no-op`() = runTest {
        val identity = FakeNetworkIdentity().apply { gatewayMac = "AA:BB:CC:DD:EE:FF"; subnet = "192.168.1.0/24" }
        val watchedDao = FakeWatchedNetworkDao().apply {
            upsertBlocking(WatchedNetworkEntity(id = 4, displayName = "Home", gatewayMac = "AA:BB:CC:DD:EE:FF", subnet = "192.168.1.0/24", watchEnabled = false))
        }
        val (r, repo) = runner(identity, watchedDao)
        assertEquals(WatchOutcome.NotWatched, r.run())
        assertTrue(repo.calls.isEmpty())
    }
}
```

- [ ] **Step 2: Add the `WatchRunner` fakes**

Create `feature/devices/src/test/.../FakeSubnetScanner.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.scan.engine.SubnetScanner
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeSubnetScanner : SubnetScanner {
    var devices: List<LanDevice> = emptyList()
    override fun scan(subnet: String, prefixLength: Int): Flow<LanDevice> = flow { devices.forEach { emit(it) } }
}
```

Create `feature/devices/src/test/.../FakeArpTableReader.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.scan.engine.ArpTableReader

class FakeArpTableReader : ArpTableReader {
    var table: Map<String, String> = emptyMap()
    override suspend fun getMacForIp(ip: String): String? = table[ip]
    override suspend fun getAll(): Map<String, String> = table
    override fun invalidateCache() {}
}
```

Create `feature/devices/src/test/.../FakeOuiLookup.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.oui.OuiLookup

class FakeOuiLookup : OuiLookup {
    var vendors: Map<String, String> = emptyMap()
    override suspend fun lookup(mac: String): String? = vendors[mac]
}
```

Create `feature/devices/src/test/.../RecordingDeviceInventoryRepository.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository
import com.ventouxlabs.netlens.core.scan.model.LanDevice

class RecordingDeviceInventoryRepository : DeviceInventoryRepository {
    data class Call(val devices: List<LanDevice>, val networkId: Long?)
    val calls = mutableListOf<Call>()
    override suspend fun persistScan(devices: List<LanDevice>, networkId: Long?) {
        calls.add(Call(devices, networkId))
    }
}
```

Add a synchronous `upsertBlocking` helper to `feature/devices/src/test/.../FakeWatchedNetworkDao.kt` (the copy created in Task E) so tests can seed without a coroutine:

```kotlin
    fun upsertBlocking(network: WatchedNetworkEntity) {
        networks.add(network)
    }
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :feature:devices:testDebugUnitTest --tests "*WatchRunnerTest*"`
Expected: FAIL — `WatchRunner` / `WatchOutcome` unresolved.

- [ ] **Step 4: Implement `WatchRunner`**

Create `feature/devices/.../WatchRunner.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.oui.OuiLookup
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository
import com.ventouxlabs.netlens.core.scan.engine.ArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.SubnetScanner
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.data.dao.WatchedNetworkDao
import kotlinx.coroutines.flow.toList
import javax.inject.Inject

sealed interface WatchOutcome {
    data object NoGateway : WatchOutcome
    data object NotWatched : WatchOutcome
    data class Swept(val deviceCount: Int) : WatchOutcome
}

/**
 * The full background-watch logic, injectable and testable without WorkManager.
 * Lite sweep only: ping sweep + ARP + OUI. No NetBIOS/SSDP/mDNS, to bound battery cost.
 */
class WatchRunner @Inject constructor(
    private val networkIdentity: NetworkIdentity,
    private val watchedNetworkDao: WatchedNetworkDao,
    private val subnetScanner: SubnetScanner,
    private val arpTableReader: ArpTableReader,
    private val ouiLookup: OuiLookup,
    private val deviceInventoryRepository: DeviceInventoryRepository,
) {
    suspend fun run(): WatchOutcome {
        val gatewayMac = networkIdentity.currentGatewayMac() ?: return WatchOutcome.NoGateway
        val watched = watchedNetworkDao.getByGatewayMac(gatewayMac)
        if (watched == null || !watched.watchEnabled) return WatchOutcome.NotWatched

        val subnetCidr = networkIdentity.currentSubnet() ?: watched.subnet
        val (subnet, prefix) = parseCidr(subnetCidr) ?: return WatchOutcome.NotWatched

        arpTableReader.invalidateCache()
        val reachable = subnetScanner.scan(subnet, prefix).toList()
        val arp = arpTableReader.getAll()
        val enriched = reachable.map { device ->
            val mac = device.macAddress ?: arp[device.ip]
            val vendor = mac?.let { ouiLookup.lookup(it) } ?: device.vendor
            device.copy(macAddress = mac, vendor = vendor)
        }
        deviceInventoryRepository.persistScan(enriched, networkId = watched.id)
        return WatchOutcome.Swept(enriched.size)
    }

    private fun parseCidr(cidr: String): Pair<String, Int>? {
        val parts = cidr.trim().split("/")
        if (parts.size != 2) return null
        val prefix = parts[1].toIntOrNull() ?: return null
        if (prefix < 16 || prefix > 30) return null
        val octets = parts[0].split(".")
        if (octets.size != 4 || octets.any { o -> o.toIntOrNull()?.let { it in 0..255 } != true }) return null
        return parts[0] to prefix
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :feature:devices:testDebugUnitTest --tests "*WatchRunnerTest*"`
Expected: PASS (all four cases).

- [ ] **Step 6: Implement `NetworkIdentityImpl` and the DI module**

Create `feature/devices/.../NetworkIdentityImpl.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.ventouxlabs.netlens.core.network.calculateNetworkAddress
import com.ventouxlabs.netlens.core.scan.engine.ArpTableReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkIdentityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val arpTableReader: ArpTableReader,
) : NetworkIdentity {

    private val cm: ConnectivityManager
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override suspend fun currentGatewayMac(): String? {
        val gatewayIp = currentGatewayIp() ?: return null
        arpTableReader.invalidateCache()
        return arpTableReader.getMacForIp(gatewayIp)
    }

    override fun currentSubnet(): String? {
        val network = cm.activeNetwork ?: return null
        val link = cm.getLinkProperties(network) ?: return null
        val addr = link.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val ip = addr.address.hostAddress ?: return null
        val prefix = addr.prefixLength
        val networkAddress = calculateNetworkAddress(ip, prefix)
        return "$networkAddress/$prefix"
    }

    override fun currentSsid(): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION") // Foreground-only; ACCESS_FINE_LOCATION already granted.
            wm.connectionInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
        } catch (_: Exception) {
            null
        }
    }

    private fun currentGatewayIp(): String? {
        val network = cm.activeNetwork ?: return null
        val link = cm.getLinkProperties(network) ?: return null
        val defaultRoute = link.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?: link.routes.firstOrNull { it.gateway is Inet4Address }
        return defaultRoute?.gateway?.hostAddress
    }
}
```

(`calculateNetworkAddress(ip, prefixLength)` already exists in `core:network` — same helper `LanScanViewModel` uses.)

Create `feature/devices/.../di/DevicesModule.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices.di

import com.ventouxlabs.netlens.feature.devices.NetworkIdentity
import com.ventouxlabs.netlens.feature.devices.NetworkIdentityImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DevicesModule {

    @Binds
    @Singleton
    abstract fun bindNetworkIdentity(impl: NetworkIdentityImpl): NetworkIdentity
}
```

- [ ] **Step 7: Run the devices suite and build**

Run: `./gradlew :feature:devices:testDebugUnitTest :feature:devices:assembleDebug`
Expected: BUILD SUCCESSFUL; all tests pass; `NetworkIdentityImpl` compiles.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(devices): WatchRunner lite-sweep logic and NetworkIdentity resolver"
```

---

### Task I: `DeviceWatchWorker` + `WatchScheduler` impl + app-start hookup

**Files:**
- Create: `feature/devices/.../DeviceWatchWorker.kt`
- Create: `feature/devices/.../WatchSchedulerImpl.kt`
- Modify: `feature/devices/.../di/DevicesModule.kt` (bind `WatchScheduler`)
- Modify: `app/.../NetLensApplication.kt` (schedule at start when Pro + master enabled)
- Modify: `app/build.gradle.kts` (app already depends on `:feature:devices` from Task D; add `:core:data`/`:core:billing` already present)
- Test: `feature/devices/src/test/.../ScheduleActionTest.kt` already covers the pure decision (Task G). The `CoroutineWorker` shell + real scheduling need on-device QA (Task J notes).

**Interfaces:**
- Consumes (Task H): `WatchRunner`. (Task G): `WatchScheduler`, `computeScheduleAction`, `ScheduleAction`, `WatchCadence`. `UserPreferencesRepository` (watch prefs), `ProStatus` (core:billing).
- Produces:
  - `DeviceWatchWorker` — thin `CoroutineWorker` pulling `WatchRunner` via `@EntryPoint` (mirrors `WidgetRefreshWorker`), unique periodic work name `"device_watch"`.
  - `WatchSchedulerImpl.apply(...)` enqueues (`ExistingPeriodicWorkPolicy.UPDATE`, `NetworkType.CONNECTED`) or cancels `"device_watch"`.

- [ ] **Step 1: Implement the worker (EntryPoint pattern, matching `WidgetRefreshWorker`)**

Create `feature/devices/.../DeviceWatchWorker.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class DeviceWatchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun watchRunner(): WatchRunner
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                WorkerEntryPoint::class.java,
            )
            when (entryPoint.watchRunner().run()) {
                is WatchOutcome.NoGateway,
                is WatchOutcome.NotWatched,
                is WatchOutcome.Swept,
                -> Result.success()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Transient failure: back off, WorkManager caps attempts; give up after 3.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "device_watch"
        private const val MAX_ATTEMPTS = 3
    }
}
```

- [ ] **Step 2: Implement `WatchSchedulerImpl`**

Create `feature/devices/.../WatchSchedulerImpl.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WatchScheduler {

    override fun apply(isPro: Boolean, masterEnabled: Boolean, cadence: WatchCadence) {
        val wm = WorkManager.getInstance(context)
        when (val action = computeScheduleAction(isPro, masterEnabled, cadence)) {
            is ScheduleAction.Cancel -> wm.cancelUniqueWork(DeviceWatchWorker.UNIQUE_WORK_NAME)
            is ScheduleAction.Enqueue -> {
                val request = PeriodicWorkRequestBuilder<DeviceWatchWorker>(
                    action.cadence.minutes.toLong(), TimeUnit.MINUTES,
                )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build()
                // UPDATE (not KEEP): a cadence change must replace the existing schedule.
                wm.enqueueUniquePeriodicWork(
                    DeviceWatchWorker.UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            }
        }
    }
}
```

- [ ] **Step 3: Bind `WatchScheduler` in `DevicesModule`**

Add to `feature/devices/.../di/DevicesModule.kt`:

```kotlin
    @Binds
    @Singleton
    abstract fun bindWatchScheduler(impl: WatchSchedulerImpl): WatchScheduler
```

- [ ] **Step 4: Schedule from `NetLensApplication.onCreate`**

Replace `app/.../NetLensApplication.kt` with the watch hookup added (inject `WatchScheduler`, `UserPreferencesRepository`, `ProStatus`; read the current values once at start):

```kotlin
package com.ventouxlabs.netlens

import android.app.Application
import com.ventouxlabs.netlens.core.billing.ProStatus
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.feature.devices.WatchScheduler
import com.ventouxlabs.netlens.feature.devices.model.WatchCadence
import com.ventouxlabs.netlens.widget.enqueuePeriodicWidgetRefresh
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NetLensApplication : Application() {

    @Inject lateinit var watchScheduler: WatchScheduler
    @Inject lateinit var userPreferences: UserPreferencesRepository
    @Inject lateinit var proStatus: ProStatus

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        enqueuePeriodicWidgetRefresh(this)

        // Reconcile the background watch at start: WatchSchedulerImpl enqueues only when
        // Pro AND the master toggle is on, and cancels otherwise (e.g. Pro lost).
        appScope.launch {
            val masterEnabled = userPreferences.watchMasterEnabled.first()
            val cadence = WatchCadence.fromMinutes(userPreferences.watchCadenceMinutes.first())
            watchScheduler.apply(proStatus.isPro.value, masterEnabled, cadence)
        }
    }
}
```

- [ ] **Step 5: Build the app to verify worker + scheduler + hookup compile**

Run: `./gradlew assembleFossDebug`
Expected: BUILD SUCCESSFUL. (FOSS flavor is always Pro, so the start-up reconcile will enqueue whenever the master toggle is on.)

- [ ] **Step 6: Run the devices tests (regression)**

Run: `./gradlew :feature:devices:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `ScheduleActionTest`, `WatchRunnerTest`, `DevicesViewModelTest` all pass.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(devices): DeviceWatchWorker, WatchScheduler, and app-start reconcile"
```

---

### Task J: Export + Pro-gating + deep link + full-suite verification

**Files:**
- Modify: `feature/devices/.../DevicesViewModel.kt` (`buildExportText()`)
- Modify: `feature/devices/.../DevicesScreen.kt` (Share/Copy `IconButton`s in the `TopAppBar`, Share Pro-gated)
- Modify: `feature/devices/build.gradle.kts` (add `:core:network` — already present from Task D — confirm)
- Create: `feature/devices/src/test/.../DevicesBuildExportTextTest.kt`
- Modify: `feature/devices/src/main/res/values/strings.xml` (export strings already added Task F/G — confirm `devices_cd_share`/`devices_cd_copy_results` exist)
- Modify: `NewDeviceNotifier` deep-link target (confirm notifications open `netlens://feature/devices`)

**Interfaces:**
- Consumes: `DevicesViewModel.uiState`, `KnownDeviceEntity.displayName()`, `ResultExporter` (core:network), `LocalProStatus`.
- Produces: `DevicesViewModel.buildExportText(): String`; Pro-gated Share and always-on Copy in the top bar; new-device notifications deep-link to the Devices screen.

- [ ] **Step 1: Write the failing export test**

Create `feature/devices/src/test/kotlin/com/ventouxlabs/netlens/feature/devices/DevicesBuildExportTextTest.kt`:

```kotlin
package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesBuildExportTextTest {

    private lateinit var knownDao: FakeKnownDeviceDao
    private lateinit var viewModel: DevicesViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        knownDao = FakeKnownDeviceDao()
        viewModel = DevicesViewModel(knownDao, FakeWatchedNetworkDao(), FakeNetworkIdentity(), /* prefs + scheduler as in Task G setUp */)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `export lists each device with custom name precedence`() = runTest {
        knownDao.seed(KnownDeviceEntity(id = 1, macAddress = "AA:BB", hostname = "raw-host", ip = "192.168.1.5", vendor = "Acme", customName = "Office Printer"))
        val text = viewModel.buildExportText()
        assertTrue(text.contains("Office Printer"))
        assertTrue(text.contains("192.168.1.5"))
        assertTrue(text.contains("AA:BB"))
    }
}
```

(Use the same ViewModel constructor arguments established in Task G's `setUp`.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :feature:devices:testDebugUnitTest --tests "*DevicesBuildExportTextTest*"`
Expected: FAIL — `buildExportText` unresolved.

- [ ] **Step 3: Implement `buildExportText`**

Add to `DevicesViewModel`:

```kotlin
    fun buildExportText(): String {
        val current = uiState.value
        val sb = StringBuilder()
        sb.appendLine("Device inventory (${current.devices.size} devices):")
        current.devices.forEach { device ->
            val mac = device.macAddress ?: "no-mac"
            val vendor = device.vendor?.let { "  Vendor=$it" } ?: ""
            val status = if (device.isKnown) "known" else "new"
            sb.appendLine("${device.displayName()}  ${device.ip}  $mac  [$status]$vendor")
        }
        return sb.toString().trimEnd()
    }
```

(Import `com.ventouxlabs.netlens.feature.devices.model.displayName`.)

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :feature:devices:testDebugUnitTest --tests "*DevicesBuildExportTextTest*"`
Expected: PASS.

- [ ] **Step 5: Add Share/Copy to the top bar (pattern 1 Pro-gating for Share)**

In `DevicesScreen`, add to the `TopAppBar` `actions = { ... }` block (import `androidx.compose.material.icons.filled.ContentCopy`, `androidx.compose.material.icons.filled.Share`, `com.ventouxlabs.netlens.core.network.export.ResultExporter`, `androidx.compose.ui.platform.LocalContext`):

```kotlin
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(onClick = {
                        com.ventouxlabs.netlens.core.network.export.ResultExporter.copyToClipboard(
                            context, "Devices", viewModel.buildExportText(),
                        )
                    }) {
                        Icon(androidx.compose.material.icons.Icons.Default.ContentCopy, stringResource(R.string.devices_cd_copy_results))
                    }
                    if (isPro) {
                        IconButton(onClick = {
                            com.ventouxlabs.netlens.core.network.export.ResultExporter.shareAsText(
                                context, "Device Inventory", viewModel.buildExportText(),
                            )
                        }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Share, stringResource(R.string.devices_cd_share))
                        }
                    }
                },
```

(`isPro` is already read at the top of the composable from Task G.)

- [ ] **Step 6: Point new-device notifications at the Devices screen**

In `core/scan/.../NewDeviceNotifier.kt`, give the notification a content intent deep-linking to `netlens://feature/devices` so a tap opens the inventory. Add inside `notify(...)` before building:

```kotlin
        val deepLink = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("netlens://feature/devices"),
        ).setPackage(context.packageName)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            device.id.hashCode(),
            deepLink,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
```

and add `.setContentIntent(pendingIntent)` to the `NotificationCompat.Builder` chain. The `netlens://feature/devices` route resolves via the Task D `DeepLinkRouter` mapping and the existing `<data android:scheme="netlens" android:host="feature" />` intent filter.

- [ ] **Step 7: Run every module's unit tests (the CI set)**

Run: `./gradlew testFossDebugUnitTest testDebugUnitTest`
Expected: BUILD SUCCESSFUL across `:core:scan`, `:core:data`, `:feature:lanscan`, `:feature:devices`, `:app` (foss), and all other modules — no regressions.

- [ ] **Step 8: Build the release-candidate debug APKs for both flavors**

Run: `./gradlew assembleFossDebug assembleGplayDebug`
Expected: BUILD SUCCESSFUL for both. (gplay must compile because `WatchSchedulerImpl` reconcile reads `proStatus.isPro.value` — verify `GplayProStatus` provides it.)

- [ ] **Step 9: Record the on-device QA gaps (no code — verification note)**

The following cannot be unit-verified in this repo (no Robolectric/instrumentation): the Room 12→13 migration on a populated DB, WorkManager periodic scheduling and `NetworkType.CONNECTED` gating, `NetworkIdentityImpl` gateway/subnet/SSID resolution against a live `ConnectivityManager`/`WifiManager`, the `DeviceWatchWorker` shell, and notification rendering + deep-link tap. Flag these for manual QA on a device rather than assuming coverage. All decision logic they wrap (`computeScheduleAction`, `WatchRunner.run`, `DeviceInventoryRepository.persistScan`, `buildExportText`, migration SQL shape) IS unit-tested.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat(devices): export, Pro-gated share, and new-device deep link"
```
