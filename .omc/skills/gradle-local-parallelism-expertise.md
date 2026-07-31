---
name: gradle-local-parallelism-expertise
description: Cold release builds and full test runs fail on a many-core dev box — it is worker concurrency, not heap, and the tempting repo-level fix breaks F-Droid
triggers:
  - "GC overhead limit exceeded"
  - "OutOfMemoryError"
  - "Not enough memory to run compilation"
  - "kotlin.daemon.jvmargs"
  - "SocketTimeoutException: Connect timed out"
  - "Gradle Test Executor"
  - "finished with non-zero exit value"
  - assembleFossRelease
---

# Local Gradle failures on a many-core machine

## The Insight

`org.gradle.jvmargs=-Xmx2048m` in this repo is sized for **CI's 4-core `ubuntu-latest` runner**, and `org.gradle.parallel=true` with `org.gradle.workers.max` unset spawns one worker per core. On a 22-core workstation that is ~22 concurrent Kotlin compilations sharing one 2 GB heap, or a swarm of forked test JVMs competing for the daemon.

The failure is **local and about concurrency**. It is not a defect in the build, and the obvious fix is wrong.

## Why This Matters

Two distinct symptoms, same root cause, both of which look like real bugs:

1. **Cold `assembleFossRelease` fails** with `OutOfMemoryError: GC overhead limit exceeded` / `OOMErrorException: Not enough memory to run compilation`. Intermittent — a rerun often succeeds because the build cache serves the outputs and nothing recompiles.
2. **Full test runs kill random unrelated modules** with `Process 'Gradle Test Executor N' finished with non-zero exit value 1`, and the module varies run to run. The cause is `java.net.SocketTimeoutException: Connect timed out` in `TcpOutgoingConnector` — the test worker cannot reach the daemon, which is starved. **No `hs_err` log and no kernel OOM kill**, so it is not a JVM crash.

Symptom 2 got noticeably worse after adding Paparazzi to `:feature:wifi`, because layoutlib makes that one test JVM slow and heavy to start.

## Recognition Pattern

- The failure only reproduces **cold** (`--no-build-cache`), never on a warm rerun
- Which module fails **changes between runs** (posture, then speedtest, then tls)
- Test XML shows **no assertion failure** — the task failed, nothing inside it did
- CI is green on the same commit

## The Approach

**Fix it per-machine, never in the repo.**

```properties
# ~/.gradle/gradle.properties   — NOT the repo's gradle.properties
org.gradle.workers.max=6
```

Locally, pass `--max-workers=4` (or `2` when Paparazzi tests are in the run) for one-off cold builds.

**Do not raise `org.gradle.jvmargs` in the repo.** Two independent reasons:

- **It does not work.** Kotlin compilation runs *inside the Gradle daemon* here — the stack shows `GradleCompilerRunnerWithWorkers` under `NoIsolationWorkerFactory` — so `kotlin.daemon.jvmargs` is never consulted. Raising the gradle heap to 4096m plus a 2048m Kotlin daemon still OOM'd at 22 workers; dropping to 4 workers at the stock 2048m succeeded in 1m52s with R8 genuinely executing.
- **It would risk F-Droid.** F-Droid builds this from source on their own buildserver. A heap sized for a workstation is its own failure mode there, and a broken F-Droid build is silent — the app just stops updating.

**Evidence that it is not a repo problem, before you "fix" it:** `release.yml` runs `assembleRelease bundleRelease` cold at 2048m on `ubuntu-latest` and has succeeded for five consecutive releases. Check that before concluding the config is too tight.

**When reproducing a cold-build failure, capture the log properly.** `./gradlew … | tail -20` discards the stack trace and leaves you with only `BUILD FAILED` — this cost a full diagnosis cycle. Redirect the whole thing to a file instead.
