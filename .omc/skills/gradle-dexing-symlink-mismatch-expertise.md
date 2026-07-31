---
name: gradle-dexing-symlink-mismatch
description: Gradle DexingNoClasspathTransform fails when /home is a symlink to /var/home — fix with module clean
triggers:
  - DexingNoClasspathTransform
  - "located outside the root directory"
  - "/home/user" vs "/var/home/user"
  - bundleLibRuntimeToDirDebug
---

# Gradle Dexing Symlink Path Mismatch

## The Insight
On systems where `/home` is a symlink to `/var/home` (common on Fedora Silverblue/Kinoite with ostree), Gradle's build cache can store class file paths using the resolved path (`/var/home/user/...`) while the dexing transform expects the symlink path (`/home/user/...`), or vice versa. The DexingNoClasspathTransform then rejects files as "located outside the root directory."

## Why This Matters
The error looks like a corrupted build or a Gradle bug. The stack trace points at a specific `.class` file but the real issue is path normalization inconsistency in cached build artifacts. Rebuilding without cleaning won't fix it because the stale cache entries persist.

## Recognition Pattern
- `DexingNoClasspathTransform` error mentioning a path with `/home/user/` and a root with `/var/home/user/` (or reversed)
- Happens after editing source files in a module
- Only affects the module whose source was changed

## The Approach
Clean only the affected module, then rebuild:
```bash
./gradlew :feature:<module>:clean
./gradlew :app:assembleDebug
```
Don't clean the entire project — it's slow and unnecessary. The path mismatch is scoped to the module with stale cached classes.
