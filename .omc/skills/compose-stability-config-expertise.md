---
name: compose-stability-config-expertise
description: Gotchas wiring the Compose stability config file in this repo's build-logic convention plugin
triggers:
  - compose_stability.conf
  - stabilityConfigurationFile
  - stabilityConfigurationFiles
  - AndroidComposeConventionPlugin
  - compose stability
  - skippable composable
---

# Compose Stability Config (this repo's build-logic)

## The Insight
Three non-obvious traps bite when adding `compose_stability.conf` to `AndroidComposeConventionPlugin.kt`:
1. **The file format rejects `#` comments** — every non-blank line is parsed as a class-pattern, so a `#`-prefixed line silently becomes a bogus pattern. Use `//` comments.
2. **The Kotlin 2.x compiler plugin API is plural**: `ComposeCompilerGradlePluginExtension.stabilityConfigurationFiles.add(rootProject.file(...))` (a `ListProperty`), not the older singular `stabilityConfigurationFile`.
3. **Changing the config invalidates ALL Compose compilation, but a config-cache run can falsely report tasks UP-TO-DATE.** Validate a stability-config change with `./gradlew <module>:...  --rerun-tasks`, or you'll believe it took effect when it didn't.

## Why This Matters
A silently-mis-parsed pattern or a stale config-cache run means the stability marking never actually applies — composables stay non-skippable and the perf win you thought you shipped is a no-op, with no error to tell you.

## Recognition Pattern
- Editing `compose_stability.conf` or the compose convention plugin.
- Adding `com.ventouxlabs.netlens.**.model.**`-style patterns to mark UI-state/domain value classes stable.
- Verifying whether a stability change actually changed codegen.

## The Approach
- Comment with `//`. Pattern `com.ventouxlabs.netlens.**.model.**` matches both `feature.<name>.model.*` and `core.data.model.*` (`**` spans segments).
- INVARIANT: every class matched by a `model` pattern MUST stay immutable (`val`-only, read-only collections). A future `var` or mutable collection in a matched package makes Compose wrongly skip composables → stale UI with NO compile error. (This warning is documented in `compose_stability.conf` itself.)
- After any stability-config edit, re-verify with `--rerun-tasks` on at least one core + one feature module; don't trust an UP-TO-DATE result.
