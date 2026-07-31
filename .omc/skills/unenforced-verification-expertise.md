---
name: unenforced-verification-expertise
description: Green tests and green CI in this repo have repeatedly meant nothing — check that the check actually runs and can actually fail before trusting it
triggers:
  - "module has no tests"
  - "zero tests"
  - "CI is green"
  - testGplayDebugUnitTest
  - "FROM-CACHE"
  - "flavored source set"
  - "fake drifted"
  - "test passes for the wrong reason"
---

# Verification that doesn't verify

## The Insight

In this repo the recurring defect is not *missing* verification — it is verification that **exists, is green, and enforces nothing**. That is strictly worse than having none, because it reads as coverage to everyone who looks, including you.

Six independent instances were found in two days (2026-07-30/31). All the same shape:

| What looked verified | What was actually true |
|---|---|
| `app/src/testGplay/GplayProStatusTest` — 12 tests over the purchase path | `ci.yml` ran `testFossDebugUnitTest testDebugUnitTest`; neither reaches `src/testGplay`. **Never ran in CI.** |
| `feature:wifiaudit`'s `FakeNetworkEventDao` | Accepted `types`/`from`/`to` and returned `flowOf(inserted.take(limit))` — filters discarded |
| `feature:devices`' `FakeOuiLookup` | Matched the full MAC; the real `OuiLookupImpl` matches a 3-octet prefix |
| `HostName` scope-id guard, "a name carrying `%` is refused" | `IPV6_LITERAL` had no colon, so `cafe%evil.example` → `cafe`. Passed only because the tested case, `nas`, doesn't spell hex |
| R8 / resource shrinking / `lintVital` | Only ever ran on tag push. `ci.yml` built `assembleFossDebug` alone |
| A local "clean-room" release repro | `./gradlew clean` then a green build in 5s — the **build cache** restored `minifyFossReleaseWithR8` `FROM-CACHE`. R8 never ran |

## Why This Matters

Each one had already survived the process meant to catch it. #116's duplicate-`LazyColumn`-key crash cleared three review passes, an adversarial round and 750 green unit tests, then crashed on the first capture — a two-minute hardware walk found it. Reviewers looked *directly at* the offending line and cleared it ("uses DB primary keys, so no duplicate-key crash" — true within one list, wrong across two lists sharing a column).

## Recognition Pattern

Suspect unenforced verification whenever:

- You are about to record a module as **untested**. Check whether tests *exist and simply are not run* first. Getting this backwards is easy — I asserted `app/src/gplay` had zero tests when it had twelve.
- A **flavored** source set is involved. `:app` is the only flavored module here: `src/testFoss` needs `testFossDebugUnitTest`, `src/testGplay` needs `testGplayDebugUnitTest`. `testDebugUnitTest` covers the *unflavored* library modules and does not reach `:app` at all.
- A **fake** stands in for something with real semantics (a Room `@Query`, an OUI prefix lookup, a flow that completes vs. one that polls). Fakes drift *weaker*, never stronger.
- A build "passes" **suspiciously fast**. `BUILD SUCCESSFUL in 5s` after a `clean` means the build cache served the outputs; the expensive task never executed.
- The check only runs on a **branch you rarely push** (tag push, release workflow, nightly).

## The Approach

**Anything under `src/test*` that no CI task names by exact task name is decoration.** Enumerate the test source trees and map each to the task that runs it — do not assume a wildcard covers it.

```bash
find . -type d -name "test*" -path "*/src/*" -not -path "*/build/*"   # every tree
grep gradlew .github/workflows/ci.yml                                  # every task actually invoked
```

**Prove the check can fail.** The single highest-value habit in this repo: revert the fix and confirm the test fails, *and only that test*. It has repeatedly caught tests that passed for the wrong reason — including one where `NonCancellable` recorded a session id but nothing after it suspended, so a cancelled coroutine ran to completion and never reached the `catch`.

**Disable the cache when the point is to exercise the work.** `clean` empties `build/`; it does not defeat `org.gradle.caching=true`. Use `--no-build-cache` when reproducing a build failure, and check whether the expensive task says `FROM-CACHE` before believing a green result.

**Make the double no weaker than production.** Mirror the real implementation's normalisation and filtering, and say in KDoc which function it mirrors so the pairing survives an edit. Where a weak version previously existed, add an assert-the-non-behaviour test (`FakeOuiLookupTest` asserts that keying on a full MAC must **not** match) so the loose version cannot come back silently.

**Trust "no crash" less than it sounds.** An empty `adb logcat -b crash` buffer proves nothing crashed; it does not prove the path was exercised. Say which of the two you have.

## Example

The gplay gap, and its one-token fix:

```yaml
# ci.yml — before: the entire Play Billing purchase path compiled and tested by nothing
run: ./gradlew testFossDebugUnitTest testDebugUnitTest

# after
run: ./gradlew testFossDebugUnitTest testGplayDebugUnitTest testDebugUnitTest
```

Twelve existing, passing, committed tests over the money path went from ceremonial to enforced.
