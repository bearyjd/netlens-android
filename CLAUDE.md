# NetLens agent guide

NetLens is an Android network-diagnostics app (`com.ventouxlabs.netlens`). Keep an edit scoped to the owning module and verify the affected behavior before handoff.

## First actions

1. Map the relevant module and its callers before changing cross-module code. The module and data maps are in [docs/CODEMAPS](docs/CODEMAPS/).
2. Run the narrowest relevant Gradle test while iterating; before a broad handoff run the three test tasks below. A green two-task run is incomplete because their source sets do not overlap.
3. For a widget visual or lifecycle change, use the device check described below. JVM tests cannot validate Glance RemoteViews output.

## Modules and boundaries

- `app` owns the activity, navigation, flavors, and the concrete billing implementation. `foss` is always Pro; `gplay` uses Play Billing.
- `feature:*` owns a tool's screen, ViewModel, DI bindings, and domain code. Features must not depend on one another: promote shared contracts, engines, and models into `core:*`.
- `core:data` owns Room and preferences; schema edits require a forward migration. `core:network` owns connectivity, SSRF defenses, coroutine utilities, and export. `core:scan` owns shared discovery/port logic. Shared fakes belong in `core:*-testing`, consumed only with `testImplementation`.
- `widget` is a Glance home-screen widget. Its rendered layout is Android RemoteViews, not Compose UI.

Use `StateFlow` UI state and immutable `copy` updates. Put user-facing text in module string resources. Use Inter for UI text and JetBrains Mono for technical values. Namespace keys when separate lists share one lazy container, since independent IDs can collide.

## Build and test

```bash
./gradlew assembleFossDebug
./gradlew assembleGplayDebug
./gradlew :feature:ping:testDebugUnitTest
./gradlew testFossDebugUnitTest testGplayDebugUnitTest testDebugUnitTest
```

`app` is flavored; core, feature, and widget modules use unflavored `testDebugUnitTest`. The full command is CI's coverage boundary: FOSS, GPlay, and unflavored module tests are disjoint. Build configuration is the authority for SDK and dependency versions; do not duplicate it here.

Prefer hand-written fakes and Ktor `MockEngine`; use `SsrfRedirectProbe` from `core:network-testing` for redirect-SSRF tests. Never make a weaker private copy of a shared fake. Paparazzi composition smoke tests catch Compose errors but do not record goldens or prove appearance.

## Data and security invariants

- Treat LAN discovery names, DNS/HTTP responses, and exported device metadata as hostile. Flatten display/export text with `DisplayText.flatten`; do not let a device forge rows or control formatting.
- Preserve ownership in `known_devices`: scans update scan-derived fields; detail UI updates user-authored fields. A rescan must not overwrite a user's name, tags, notes, or location.
- Security-sensitive URL or network predicates must retain every prior restriction when tightened; test bypass cases as well as the intended allow case. `SsdpScannerImpl.isSafeLocationUrl` is especially high risk.
- Add a Room migration for every schema change and validate it with the existing Room/Robolectric setup where applicable.

## Widget/device-only verification

Glance can fail silently: vertical content weights can delete a child, `fillMaxHeight()` in a row can evict siblings, and a container emits at most 10 children. Only root layout owns surplus-space gaps; `MAX_WIDGET_CHIPS` is capped at 4. These are device-observed constraints, not JVM-testable ones.

For any affected widget, build the release package used by the placed widget, install it only after checking the signing target, then run:

```bash
python3 tools/verify_widget.py <adb-serial> [FourByTwo|Dashboard|Standard|Compact]
```

Read [the widget 4x2 device-forensics handoff](docs/handoffs/widget-4x2-device-forensics-2026-09-13.md) before modifying that layout or its verification script. It contains the live density task, measurement calibration, and exact failure modes.

## On-demand references

- [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md): project conventions and test setup.
- [docs/RELEASING.md](docs/RELEASING.md) and `.claude/skills/android-release/SKILL.md`: release process, signing, version, and F-Droid requirements. Use the release skill for a release.
- [docs/HANDOFF.md](docs/HANDOFF.md): current handoff index; historical sessions and prior roadmap are archived from there.
- [DESIGN.md](DESIGN.md): visual system and product UI rules.
