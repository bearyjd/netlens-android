---
name: paparazzi-composition-smoke-expertise
description: Use Paparazzi as a JVM composition check with no golden images — it catches the Compose crashes this repo keeps shipping, and has four setup traps
triggers:
  - "Key \"1\" was already used"
  - "duplicate key LazyColumn"
  - Paparazzi
  - "Plugin with id 'app.cash.paparazzi' not found"
  - "screenshot test"
  - "composition error"
  - snapshot
---

# Paparazzi as a composition smoke test, not a screenshot test

## The Insight

The Compose bugs that escape this repo — duplicate `LazyColumn` keys, composition errors, measure/layout failures — **throw at render time**. They do not need pixel comparison to detect. So `paparazzi.snapshot { }` inside an ordinary unit test *is* the assertion, and you can skip golden images entirely: nothing recorded, no PNG committed, `verifyPaparazzi` never run in CI.

That discards everything expensive about screenshot testing (binary artefacts, a record-and-review step on every intentional UI change, cross-environment pixel flake) while keeping the part that has actual defect-catching history here.

## Why This Matters

`IllegalArgumentException: Key "1" was already used` crashed the Wi-Fi survey on its **first capture** — the feature's primary path. It survived three review passes, an adversarial round and 750 green unit tests; a two-minute walk on a real phone found it instantly. Reverting the fix (`dc03409`) makes a composition smoke test fail with that exact message in **26 seconds** on the JVM.

Note the distinction that made the difference: `SurveyListKeysTest` already pinned the key *functions*. It would still pass if a caller went back to raw ids at the `items()` site. Only rendering catches the real thing.

## Recognition Pattern

Reach for this when a screen has two or more keyed `items()` in **one** `LazyColumn`, especially when the two lists come from different Room tables — independent autoincrement sequences guarantee overlapping ids. In this repo ten modules use keyed `items()`; `app`, `wifi`, `monitor` and `devices` have multiple.

## The Approach

Four traps, all of which cost time:

1. **Declare the plugin at the root or the convention plugin cannot apply it.** `build-logic`'s `compileOnly` gives compile-time access and nothing at runtime; applying by id fails with `Plugin with id 'app.cash.paparazzi' not found`. Add `alias(libs.plugins.paparazzi) apply false` to the root `build.gradle.kts`.

2. **The render exception escapes via the JUnit rule, not out of `snapshot { }`.** A `try/catch` at the call site catches nothing and the test still fails — verified: the inline catch printed `NO THROW` while the test failed. So **assert nothing**; let the render failure be the failure. Wrapping it silently passes.

3. **Paparazzi's rule is JUnit4** and this repo is JUnit 5 everywhere. Add `junit4` + `junit-vintage-engine` so both styles run on one platform; no need to split the module's tests.

4. **Only state-driven composables can be rendered.** `WifiSurveyTab(state, callbacks…)` works. `HomeScreen` defaults its parameters to `hiltViewModel()` and cannot — test the stateless composable it delegates to.

Version compatibility was the worry and turned out not to be one: **Paparazzi 1.3.5 works on AGP 8.9 / Kotlin 2.1.0 / Compose BOM 2024.12 / JDK 21**, despite its release notes advertising AGP 8.4.2. Verify by spiking rather than trusting the changelog — no compatibility table exists.

**Cost:** layoutlib makes that module's test JVM heavy to start, which can starve the Gradle daemon on a many-core box and time out *other* modules' workers. See `gradle-local-parallelism-expertise`.

## Example

The regression test that would have stopped #116 — note it asserts nothing:

```kotlin
@get:Rule val paparazzi = Paparazzi()

@Test
fun `a point and a session sharing row id 1 render together`() {
    paparazzi.snapshot {
        WifiSurveyTab(
            state = WifiSurveyUiState(
                points = listOf(point(id = 1, …)),      // survey_points.id == 1
                sessions = listOf(session(id = 1, …)),  // survey_sessions.id == 1
            ),
            /* callbacks… */
        )
    }
}
```
