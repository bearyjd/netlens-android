# Session Handoff — the two open `widget/` items are closed (2026-08-16)

Supersedes the "What's still open in `widget/`" section of the handoff below. Release status is
unchanged: **v1.3.3 is still the released version**, and everything since it is tests and docs, so
there is nothing user-facing waiting to ship.

## The prediction was half right, and the half it got wrong is the useful part

Both items were framed by the previous session as *blocked pending a workaround*. Neither actually
was — but not for the reason either entry gave.

- **`doWork()` did not need interface seams.** The entry said it "needs interface seams before it's
  testable at all", meaning wrap the Room DAO, Ktor client and raw `Socket` in interfaces and
  inject fakes. That machinery was never the obstacle, because **almost none of the logic that
  decides what the widget shows is in the I/O** — it is in the derivations between the reads and
  the DataStore write. Extracting those (`WidgetSnapshot.kt`: `resolveWidgetScore`,
  `resolveIpDisplay`, `applyWidgetSnapshot`) yields 17 framework-free tests and leaves `doWork()` a
  thin shell. This is the same lesson #156–#158 kept producing, one level up: **check whether the
  logic can be pulled out pure before deciding what the test infrastructure must support.**
- **`detectEncryptionType` is blocked outright — for the branch nobody was asking about.** The
  suspicion about `TransportInfo` was right for API 31+: `transportInfo` can only ever be null
  under Robolectric, so every S+ assertion reduces to "returns null", which is true with or without
  the logic beneath it. That branch needs a real device. But the entry treated the whole function
  as blocked, and **the pre-S branch is live code** — `minSdk` is 29, so API 29/30 devices take it
  — and it tests fine. Three tests now cover the BSSID match and the transport guard.

## Two traps worth carrying forward

- **A test can pass because it never reached the code it claims to test.** The transport-guard test
  passed with the guard *deleted*, because at the default SDK it took the S+ branch and returned
  null for an unrelated reason. Only running it at SDK 29, against a WiFi state that would resolve
  to WPA3 without the guard, makes it real. Same family as #158's "extracted function drifted from
  production": the test is green, the reason is wrong.
- **`git checkout` cannot revert an untracked file.** A mutation-check loop reverting between
  mutations silently accumulated all six instead, because `WidgetSnapshot.kt` was new and unstaged.
  It happened to still be readable — each round's failures were a superset of the last — but it
  also hid that one mutation was never killed. **`git add -N` a new file before mutation-testing
  it, or copy it aside.** The mutation it hid: an "org without an ASN" test that claimed to pin the
  `ifBlank` fallback but could not, since `substringAfter(" ")` returns the whole string when the
  delimiter is absent. The fallback only matters for an org that is *nothing but* an ASN.

## Verification

All six mutations on the new code are killed by exactly their predicted test, plus two more
(`PING_MS` dropped, `rssiLevel` swapped for `rssi`) confirming the write-coverage assertions catch
a dropped or mis-mapped key — the specific regression this extraction risks. A separate reviewer
pass compared all 26 DataStore writes against the pre-refactor body key by key, condition by
condition, and found no unintended divergence. `assembleFossDebug` plus all three CI test tasks
pass.

The one intentional behavior change: `LAST_SCAN_TIMESTAMP` and `LAST_REFRESH_MS` now share a single
`nowMs` rather than two `System.currentTimeMillis()` calls microseconds apart. Nothing compares or
subtracts them.

---

# Session Handoff — Robolectric adoption complete, v1.3.3 still current (2026-08-14/15)

Supersedes the v1.3.3 handoff below on everything except release status, which is unchanged —
**v1.3.3 is still the released version**; nothing this session reaches users until the next tag.

## TL;DR

- **Four PRs merged (#155-#158), master `89cdf72`, CI green, no open PRs.** All four
  branches auto-deleted on merge and confirmed gone via `git fetch --prune` (their
  remote-tracking refs briefly lingered locally — that's a stale-ref artifact, not a real branch
  still existing on origin; don't re-investigate that if you see it again elsewhere).
- **The roadmap's longest-standing item — "Robolectric adoption" — is done**, in the places it was
  actually needed. `.agent_native/agent_roadmap.md`'s entry has the full detail; this is the
  summary.
- **`spike/baseline-journey-extension` remains open on origin, untouched** — same as every prior
  handoff. It's an intentionally-preserved dead end (see the roadmap and the 2026-08-07 handoff
  further down), not forgotten work. Stop re-flagging it.

## What shipped, in order

**#155 — `core:data` real Room SQL (JVM/Robolectric split).** The roadmap's own audit had
correctly closed most Robolectric-motivating cases via interface seams, but missed that **no test
anywhere ran a real Room `@Query`** — every DAO test ran against a hand-written fake, so a broken
SQL string could still ship green. Tried the "no Robolectric at all" path first
(`Room.inMemoryDatabaseBuilder<T>()`, which Android's own docs now recommend over Robolectric for
Room) — **it doesn't work for a non-KMP `com.android.library` module**: that reified builder only
ships in Room's `-jvm`-target artifact, and bolting it onto the `-android` artifact this repo's
production build needs creates a duplicate-class hazard, not a working test. Confirmed by
decompiling `room-runtime-android-2.7.2.jar`. Fell back to the classic, pre-KMP
`MigrationTestHelper` constructor under Robolectric — which is why `core:data` stays pinned to
Room 2.6.1 *deliberately*, not from neglect: 2.7's KMP release removed that constructor. New
opt-in convention plugin `netlens.android.robolectric`, applied only to `core:data`. Real tests
now cover the `known_devices` write-path disjointness invariant (previously a code comment, not a
test) and all 12 migrations.

**#156 — `widget/`'s cross-render regression + lifecycle.** The other genuinely-Robolectric case:
Glance/`AppWidgetManager`/WorkManager/`ConnectivityManager` code with a real shipped bug
(`refreshAllWidgets`'s doc comment records a Pixel 10 incident — `updateAll()`'s stale
`providerToReceiver` map pushed one widget's RemoteViews onto another instance) and zero
regression coverage. **The fix itself needed no Robolectric** — the bug-class logic was pulled
into an injectable `internal suspend fun refreshWidgets` and pinned with plain JUnit5 fakes,
mirroring the repo's `ArpTableReaderImpl.parseArpTable` pattern. Robolectric (the same convention
plugin, applied to `widget/` this time) covers what's genuinely framework-bound: the
`ConnectivityManager.NetworkCallback` register/unregister lifecycle — including a
private-static-companion double-registration hazard the inventory pass caught — and WorkManager
enqueue shape via the officially-supported `androidx.work:work-testing`.

**#157 — the security check the #156 inventory flagged and deferred.** `OpenDeeplinkAction`'s
scheme/host allowlist was the module's one untested security boundary. Extracted to
`isAllowedDeeplinkUri`, tested including the adversarial case that's the actual point of having
this test: userinfo/authority confusion in the raw URI string (`netlens://feature@evil.com` vs
`netlens://evil.com@feature`) — confirmed `Uri.host` resolves the true authority, not something a
naive string check would be fooled by.

**#158 — `NetworkCollector`'s derivations.** Zero tests despite its single 113-line `collect()`
producing every value the widget renders. Three pure derivations extracted and covered without
Robolectric (`cellGenerationFor` — a 15-branch constant mapping; `isVpnInterfaceName`;
`cellularLinkSpeedMbps`); `collect()`'s reachable branches covered under Robolectric.

## The pattern worth repeating

Every new test across all four PRs was **mutation-checked, not just written**: break the
invariant in real production code (not the test fixture), confirm the predicted test — and only
that test — fails, then revert. This caught real defects twice, both before merge:

- `KnownDeviceDaoTest`'s original search assertion couldn't distinguish a substring `LIKE` from a
  prefix `LIKE` bug, because every fixture string happened to match at position 0.
- **In #158 it caught a defect in the refactor itself.** The first extraction pass left
  `detectCellGeneration` and `collect()` still using their own inline copies of the logic that had
  supposedly been extracted — so the new tests would have passed green while production ran a
  different copy. The mutation surfaced it by hitting *two* source sites instead of one. This is
  the "double drifted from production" failure mode this repo already has a note about
  (`.omc/skills/unenforced-verification-expertise.md`), in a new costume: an *extracted* function
  drifting from the original it was supposed to replace. **After extracting, grep for the old
  expression** — if it still appears anywhere, the extraction isn't finished.

**Before reaching for Robolectric, check whether the logic can be pulled out pure first.** Most of
these PRs' headline fixes (`refreshWidgets`'s dispatch logic, `isAllowedDeeplinkUri`, all three of
#158's derivations) turned out to need zero Robolectric once separated from the Android calls
around them — only the parts that couldn't be pulled out (`Uri.parse` itself,
`ConnectivityManager`, WorkManager, real Room SQL) got the Robolectric convention plugin. Don't
reach for it by default.

## Robolectric shadow limits, confirmed empirically (#158)

Worth knowing before scoping anything else that touches network state — each was verified with a
throwaway spike rather than assumed, and each *reduced scope* rather than getting a workaround:

- `LinkProperties.interfaceName` and `setDnsServers` work. **`LinkAddress` is not constructible**
  (package-private constructor, no public `addLinkAddress`) → `localIp`/`hasIpv6` untestable.
- **`WifiInfo` does not implement `TransportInfo` in Robolectric's SDK sandbox**, so
  `NetworkCapabilities.setTransportInfo(wifiInfo)` throws `ClassCastException`. `ShadowWifiInfo`
  itself is fine (`newInstance`/`setRssi`/`setLinkSpeed` all exist) — the *cast* is what fails.
- **`WifiManager.calculateSignalLevel(rssi, 5)` is stubbed to a constant** — `-55` and `-95` both
  return `4`. Asserting `rssiLevel` would have been green and meaningless.

## What's still open in `widget/`

**Both items below were closed on 2026-08-16 — see the section at the top of this file.** The
original text is kept here only because the resolutions correct it:
- `WidgetRefreshWorker.doWork()` end-to-end (434 lines, live network + raw socket calls) — needs
  interface seams before it's testable at all.
- `detectEncryptionType` — **re-check this against #158's `TransportInfo` finding before planning
  it.** It was originally deferred as "real `WifiInfo` construction is awkward," but the actual
  blocker looks broader than that framing: it reads `WifiInfo` *via* `NetworkCapabilities.transportInfo`,
  which is precisely the cast that fails. It may be blocked outright rather than merely awkward.

Also recorded in the roadmap, deliberately unfixed because both are decisions rather than test
changes: `collect()`'s pre-Q WiFi fallback is **dead code** under `minSdk 29` (`Q` *is* 29), and
its outer `catch (_: Exception)` renders any internal bug as a normal-looking empty widget rather
than an error state.

None of these are urgent. Nothing is broken; this is backlog, not a live gap.

---

# Session Handoff — v1.3.3 shipped (2026-08-08)

Supersedes the "five security findings" handoff below on the release status: **v1.3.3 is
released**, not pending. That handoff's bodies (SSDP guard rounds, cross-model review findings,
control-character CI gate) are still accurate history, just no longer the current release state.

## TL;DR

- **v1.3.3 is the released version**, tagged and published 2026-08-08
  (https://github.com/bearyjd/netlens-android/releases/tag/v1.3.3). `gradle.properties` is
  `1.3.3 / 17`. All four release artifacts (foss/gplay × apk/aab) built, signed, and uploaded;
  release CI green. Cert SHA-256 unchanged (`8fdfc928f8...`) — in-place upgrade from 1.3.2 works.
- **PR #154** (merged, branch deleted) added the actual fix for #152's second half: LAN Scan
  now shows a distinct "couldn't reach the local network" notice instead of a bare "0 devices"
  when the process fails to bind to Wi-Fi/Ethernet — not just under a VPN, any unreachable-LAN
  case (cellular-only, no network at all). Also fixed a real bug caught in review:
  `bindProcessToNetwork`'s return value was being discarded, so a network going invalid between
  selection and bind (e.g. Wi-Fi dropping mid-scan) would have been silently reported as bound.
- **F-Droid MR #42628 still open, not blocked on us.** Maintainer's requested changes (latest-
  version-only, full commit hash, reproducible-builds answer) were addressed weeks ago; pipeline
  passed a week ago on `ff6ccabf` (pinned at 1.2.6). Posted a nudge comment today
  (note_3663038993) since the `waiting-for-upstream` label looked stale. **Don't bump the pinned
  commit in that MR proactively** — `AutoUpdateMode: Version` + `Tags` backfills 1.2.7→1.3.3
  automatically once merged, and touching the recipe now just re-triggers CI for no benefit.
- Four merged feature/chore branches (`chore/bump-1.3.3`, `chore/untrack-prps`,
  `docs/handoff-2026-08-08`, `fix/lan-scan-vpn-binding`) were already auto-deleted on merge —
  confirmed via `git fetch --prune`, nothing left to clean up there.
- `spike/baseline-journey-extension` remains open on origin, unmerged — untouched, presumed
  intentional in-progress work, not evaluated this session.

---

# Session Handoff — five security findings in the LAN parsers, three of them mine (2026-08-08)

Supersedes the 2026-08-06 handoff below on everything except the baseline-profile section,
which is still current.

## TL;DR

- **Six PRs merged (#144-#149), master `abcbfff`, CI green, no open PRs.** 979 tests.
- **v1.3.2 is still the released version** (2026-08-05). Nothing shipped today reaches users
  until the next release, and `gradle.properties` is still `1.3.2 / 16` — **bump before tagging**.
- **Five security findings in the LAN discovery path.** Two were pre-existing; **three I
  introduced while fixing the first two**. Details below, because the pattern matters more
  than the bugs.
- **A CI gate now rejects literal control characters in source.** It exists because a
  convention failed three times in one session.

## The SSDP guard: three rounds, and I made it worse before better

`isSafeLocationUrl` decides whether the app will fetch an attacker-supplied URL. Any device
on the LAN can answer an M-SEARCH and choose that URL.

| Round | Finding | Found by |
|---|---|---|
| 1 | DNS rebinding: resolved to validate, `openConnection` resolved again to connect | scoping pass |
| 1 | Cross-host SSRF: nothing tied the URL to the responder, so `LOCATION: http://192.168.1.1/admin` was fetched | scoping pass |
| 2 | **Redirects were never disabled.** `HttpURLConnection` follows them by default, so the new host check was bypassable in one hop | `/review` + security specialist |
| 3 | **I deleted the loopback/link-local rejection.** Replaced it with the responder match instead of adding to it | `/codex review` [P1] |

Round 3 is the one to learn from. **I shipped a net security downgrade and was confident
about it.** The old guard rejected loopback and link-local unconditionally; my rewrite only
checked `host == responderIp`. UDP source addresses are forgeable, so a match proves nothing
about the destination — and another app on the same device can answer from `127.0.0.1`.

**My own test encoded the hole as intended behaviour.** It was named *"loopback and link-local
stay rejected UNLESS they are the responder"* and asserted only the cases that still passed.
It went green against the vulnerability it was describing. A second test asserted a bug too:
the IPv6 normalisation case used `fe80::1`, a link-local address, so it asserted that a
special address was fetchable.

**Rule: when tightening a security predicate, ADD the check. Do not let it replace the old
one.** Both conditions were necessary; neither was sufficient.

## Cross-model review found 0% overlap, and that is the useful number

`/review` (Claude, plus a security specialist subagent) and `/codex review` each did a
thorough pass over the same 137-line diff. Between them: four real findings, **zero in
common**.

- My pass asked *"is the new guard bypassable?"* and found the redirect.
- Codex asked *"what did the old guard do that the new one no longer does?"* — a diff-aware
  question I never asked, because I was reviewing my own reasoning rather than the delta.
- The security specialist confirmed my finding at confidence 10 and **missed Codex's
  entirely**, with the same blind spot.

For anything security-shaped, run both, and run them **before** merge rather than after.

## Literal control characters: a convention that failed three times

`SsdpHostileInputTest.kt` (merged in #147) contained two NUL bytes and a `U+202E` bidi
override — written as raw bytes instead of Kotlin escapes.

**Why it matters:** git and grep treat such a file as **binary**. It vanishes from `grep`,
appears as `Bin N -> M bytes` in a diff, and disappears from any grep-based audit or
security scan. Three greps during the review returned nothing and nearly produced a wrong
conclusion about test coverage that does exist. `U+202E` is also the Trojan Source
(CVE-2021-42574) class.

I then made the same mistake **twice more in the same session** — once writing new test
fixtures, and once *inside the comment of the CI check written to prevent it*. A logged
learning did not stop it.

**`ci.yml` now rejects it.** Two details are load-bearing, both learned by getting them
wrong first:

- **`grep` needs `-a`.** Without it grep classifies the offending file as binary and
  **suppresses the match** — the check passes on exactly the files it exists to catch. The
  first version of the gate did this and silently missed a test NUL.
- **`.yml` is in the glob list** because the gate's own file was not, and that is where the
  third instance landed.

## Export rows can be forged by a device name

Phase 4 of the fuzzing scope (the mDNS data-flow review). mDNS has **no parser of ours** —
`NsdManager` does that work — so the surface is the strings it returns and where they land.

LAN Scan and Devices export one device per line. The hostname comes from an mDNS
`serviceName`, a NetBIOS name, or an SSDP `friendlyName`, and no parser strips **inner**
control characters because `.trim()` only touches the ends. A hostname of
`nas)\n192.168.1.1 (Router)` therefore forges a second row in text the user shares.

Fixed with `DisplayText.flatten` (`core:network`), a sibling to `HostName`. **The split is
deliberate:** `HostName` validates and returns null (right for a URI authority);
`DisplayText` flattens and keeps the value (right for display, because `Brian's MacBook Pro`
is a legitimate mDNS name that `HostName` correctly rejects — using the validator there
would delete real devices from the export).

Also worth knowing: the Devices export **already** split on newlines for user-authored
`notes` but not for the network-supplied hostname, which is backwards from a threat view.

## Fuzzing: what it found that hand-written cases did not

Phases 1-2 landed in #147. Two crashes, both reachable from the LAN, both masked by
catch-all handlers so nothing ever failed:

- `parseDeviceXml` threw `StringIndexOutOfBoundsException` when a closing tag preceded its
  opening tag (`substring(29, 0)`).
- `parseResponse` threw `ArrayIndexOutOfBoundsException` when the reported length exceeded
  the array. Latent — `receive()` caps at `buffer.size` — but the parser was relying on an
  invariant only its caller held.

**The NetBIOS one was found by the randomised test, not by hand.** A hand probe of the
*same input* reported "ok", because it used a zero-filled array and the name-skip loop exits
on the first zero byte. It takes non-zero bytes to reach the indexing. The deterministic
regression test now fills with `0x41` so catching it does not depend on a fuzz seed, and
random passes use a fixed seed.

**Phase 3 (Jazzer) is not recommended.** Phase 2 found the bug that was there; a fuzzing
dependency plus a corpus to maintain is hard to justify without evidence the corpus is
missing something.

## Test doubles: the `Fake*` grep undercounts, again

#146 consolidated `KnownDeviceDao`. The handoff said three doubles; **there were four** — the
fourth in `core:scan`'s own test source set, named `InMemory*`, invisible to both a `Fake*`
grep and a sweep of `feature/`. That is the third time this session a naming convention hid
duplication from the audit meant to find it.

One of the four was **inert**: every write a no-op, `insertIfNew` returning a constant
without storing. Any persistence test written against it would have passed regardless of the
real `@Query`. **Grep the interface name, not the `Fake` prefix.**

## Open items — 2026-08-08

1. **Bump the version before the next release.** `gradle.properties` still reads `1.3.2 / 16`,
   which is the published version. Everything merged today is unreleased. `/android-release`
   checks this, but the bump is not automatic.
2. **Play Console bootstrap** — unchanged, and entirely human work in Google's consoles:
   create the app, hand-upload one AAB, then the service account. The repo side is done and
   verified (`docs/play-store.md` has the recipe and the screenshot pipeline).
3. **Better store screenshots need a populated phone.** The listing shows Ping and Settings
   instead of the coverage survey, device tagging and launchable services. Set custom names on
   inventoried devices first — the tagging feature hides real hostnames *and* makes the shots
   look curated.
4. **`.claude/PRPs` is still half-tracked** and still needs a human decision. Three options in
   the PM handoff.
5. **`DevicesViewModelTest` flake** — still unfixed by design. Wait for the stack trace
   `testLogging` now gives; do not chase it cold.
6. **Display sinks still receive unflattened strings.** Compose `Text` and the notification
   get raw hostnames. A newline there wraps rather than forges, so it is cosmetic — but a
   future line-oriented sink inherits the bug. Ingestion-level flattening would close the
   class; the construction points are scattered across `LanScanViewModel`, so it is separate
   work.
7. **F-Droid: still never listed.** MR #42628 is the initial-inclusion request. No tag reaches
   an F-Droid user until a maintainer merges it.

# Session Handoff — baseline profile: what regenerating does and does not fix (2026-08-06)

Superseded the 2026-08-05 night handoff on the baseline-profile item only. **That handoff and
the two below it (2026-08-05 evening, 2026-08-05 PM) were removed on 2026-08-08** — the release
records they carried are now in `docs/RELEASING.md` and the CHANGELOG, which is where a reader
looks anyway. Recover the full text with `git show d1b8868:docs/HANDOFF.md` if a decision from
them needs its original reasoning.

## CORRECTED: the profile problem is journey SCOPE, not staleness

This file has said, in several places, that the baseline profile "has never seen the one-tap
location UI" and framed it as **stale** — implying a re-dispatch fixes it. **It does not, and
never could.** Measured across a full successful regeneration:

```
ScanLocation entries:  before 0  →  after 0
lanscan entries:       before 6  →  after 6   (unchanged)
```

`BaselineProfileGenerator`'s journey is **cold start into HomeScreen + a home-grid scroll**. It
has never navigated further, at any point in its history. **A profile only contains what the
benchmark walks.** Regenerating on a fresh date refreshes the paths already covered and adds
nothing else.

Left uncorrected, that framing buys a ~20-minute CI run every release and delivers nothing for
the features it claims to cover. **Do not re-dispatch expecting feature coverage.** Extending
coverage is a code change to `baselineprofile/`, and the attempt is described below.

## What the regeneration DID do (PR #144)

Run `31092524613`, API 34 emulator. Real but modest:

```
baseline-prof.txt   23068 → 23071 lines   (481 changed)
startup-prof.txt    21430 → 21414 lines   (364 changed)
156 netlens-owned entries added, concentrated in ui.home + Compose lazy.grid
```

That is the **startup** path, which is the highest-value thing to have profiled, so it is worth
landing on its own.

## The journey extension was attempted and ABANDONED — read before retrying

Six CI runs. The work is preserved on **`spike/baseline-journey-extension`** (`0e81d1a`), not
deleted. It is NOT in #144.

**The blocker was never navigation.** Deep links work; `lanscan` and `devices` both resolve;
`generateStartup` passed every run. The blocker is the **POST_NOTIFICATIONS dialog on a fresh
emulator** — Devices requests it on entry, a clean emulator has never been asked, and the dialog
covers the screen so the arrival marker never appears. Dismissal was attempted with resource ids
for **both** `com.android.permissioncontroller` and `com.google.android.permissioncontroller`,
plus "Don't allow" / "Deny" text fallbacks. **All missed. Cause unknown.** That is where it was
stopped.

Two traps that cost runs, both worth keeping:

- **`pm revoke` does NOT simulate a fresh install.** Revoking after a prior grant sets
  `USER_SET`, which Android reads as "user already declined" and suppresses the re-prompt. A
  hardware test done that way shows no dialog and will **wrongly clear** this exact hypothesis —
  which is what happened here, sending two runs down a timeout dead end.
- **The dialog's timing is non-deterministic.** It blocked `devices` on two runs and `lanscan`
  on the next. Anything that dismisses once, at a fixed point, races it. The spike's final form
  polls (clear-dialog, check-marker, repeat) rather than guessing the moment.

If you retry: the highest-value next step is making the dialog impossible rather than dismissing
it — pre-grant or pre-deny `POST_NOTIFICATIONS` after install but before the benchmark, so no
dialog is ever raised.

**Also learned, and independently useful:** `DeepLinkRouter.PATH_TO_ROUTE` is a whitelist, and an
unlisted path resolves to `null` so the app **silently stays on Home**. `wifi` is NOT in that map
(`wifiaudit` is a different screen), which is why deep-linking the Wi-Fi analyser appears to do
nothing. `ipcalc` is in `ROUTES_WITH_QUERY` but not in `PATH_TO_ROUTE`, so that entry is dead.

## Reading CI status: `cancelled` renders as `fail`

`gh pr checks` prints **`fail` for a cancelled job**. Three times this session that was misread
as a real failure — twice by me in the same hour. The cancellations were self-inflicted: this
repo's workflows use `concurrency: cancel-in-progress`, so every push supersedes the previous
commit's in-flight checks, and **closing/reopening a PR also spawns a run that cancels a
re-run you just started.**

**Check the conclusion, not the rendering:**

```
gh api repos/<owner>/<repo>/commits/<sha>/check-runs -q '.check_runs[]|"\(.name) \(.conclusion)"'
```

Related and still true: a commit pushed by a workflow using `GITHUB_TOKEN` does **not** trigger
workflows, so a bot-committed profile has no CI of its own until something else pushes.
