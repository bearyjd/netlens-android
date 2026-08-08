# Handoff: Fable Audit → Fix Plan (2026-07-04)

> **Relocation note (2026-08-08).** Moved here from `.claude/PRPs/`, which was deleted; every
> `.claude/PRPs/...` path below is dead. Point 3 and the closing "Files" section are now
> historically wrong in a way worth stating, because they describe the *opposite* of what
> happened: this file and the plan are no longer gitignored — they are tracked, here, in `docs/`.
>
> Current locations:
>
> - Fix plan → `docs/backlog/fable-audit-fixes.plan.md` (resume at **Task 3**)
> - Audit report → **deleted, unrecoverable.** Never git-tracked. The plan restates each finding,
>   so use it instead.
> - This handoff → `docs/backlog/fable-audit-handoff-2026-07-04.md`
>
> The competitor research in the section below is the reason this file was kept: it exists here
> and nowhere else.

**Status at pause:** Phase 1 done, merged, and pushed to `origin/master`. Phases 2-4 not started. Competitor-feature research done but not actioned.

## What happened, in order

1. Ran a Fable-model agent to audit NetLens across three dimensions: feature completeness, UI consistency, and security/functionality. Findings written to `.claude/PRPs/reports/fable-audit-2026-07-04.md`.
2. Wrote a phased implementation plan to fix every finding: `.claude/PRPs/plans/fable-audit-fixes.plan.md` (11 tasks across 4 phases, using the `superpowers:writing-plans` format but placed in this repo's own `.claude/PRPs/` convention rather than the skill's generic default location).
3. **Both `.claude/PRPs/reports/` and `.claude/PRPs/plans/` are gitignored** (`.gitignore:23`). The user chose to leave these two files local/uncommitted rather than force-adding them (some older PRPs artifacts in this repo *were* force-added historically — `cso-security-audit.md` etc. — but these two are not). If a future session needs them tracked, force-add explicitly.
4. Executed Phase 1 (Tasks 1-2, the Critical security fix) via `superpowers:subagent-driven-development` in an isolated worktree, with per-task review + a whole-branch review + a re-review after fixes (details below). User explicitly deferred Phases 2-4 ("Subagent-driven, Phase 1 only").
5. In parallel, ran a background research agent on competitor network-diagnostics apps (Fing, PingTools, WiFiman, NetX, etc.) for feature ideas. Findings are in this conversation's transcript only — **not yet written to any file**. See "Competitor research" section below to recover them if the transcript isn't available.

## Phase 1 — what was actually fixed (merged, pushed)

Commits on `master` (origin, pushed): `037c973` → `1d47c16`, 5 commits:
- `037c973` fix: prevent SSRF via HTTP redirect in HTTP Tester
- `316b732` docs: document SSRF-safety rationale for IpInfo Ktor client
- `c2a51bd` fix: block SSRF-via-redirect in monitor's EndpointCheckerImpl
- `b488c61` test: add happy-path regression test for HttpRequesterImpl
- `1d47c16` docs: document safe hardcoded-host client in HopGeolocatorImpl

**Root cause fixed:** `HttpRequesterImpl` (httptester) and `EndpointCheckerImpl` (monitor) both validated a user-supplied host via `SsrfGuard.isPrivateOrLoopback()` on the *initial* request only, then let Ktor's `HttpClient(CIO)` auto-follow redirects with no re-validation — a compromised/malicious endpoint could 302 into a private/loopback/metadata address. Fixed via a shared `configureSecureDefaults()` function (in each module) that sets `followRedirects = false`, used by both the production `@Inject` constructor and a test-only `internal` engine-accepting constructor — no construction path can skip it.

**Important scope note:** the Monitor bug (`EndpointCheckerImpl`) was **not** in the original plan — the plan's Task 2 only scoped an audit of `feature/ipinfo`. The whole-branch review (a later gate in the subagent-driven-development process) caught it by sweeping the whole codebase for other Ktor clients. The plan and audit report have both been corrected in place to reflect this (see Finding 1b and Finding 12 in the audit report — Finding 12 is a new low-priority backlog item, `feature/lanscan/.../SsdpScanner.kt:74`, same bug class but different threat model — LAN-local device spoofing SSDP, not remote user input — explicitly deferred, not fixed).

**Process used:** 3 rounds of review before calling it done — per-task review (Task 1, Task 2), a whole-branch review (found the Monitor Critical + 2 Minor items), a fix pass, then a re-review of the fix, then one final whole-branch confirmation sweep. All approved. Full review transcripts are not saved to disk — they exist only in this conversation and in the worktree's now-deleted `.superpowers/sdd/` scratch files (worktree was cleaned up after merge, per `superpowers:finishing-a-development-branch`).

**User decisions made during Phase 1 (won't need re-asking):**
- Worktree location: yes, create one (native `EnterWorktree`, not the git-fallback `.worktrees/`).
- HTTP Tester's new behavior — redirects are *shown*, not auto-followed, even for legitimate ones (e.g. http→https upgrades) — confirmed as the intended final behavior, not a bug. Don't revisit this unless the user raises it again.
- Findings report/plan: kept local/uncommitted (see gitignore note above).
- Execution mode chosen: "Subagent-driven, Phase 1 only" (not the whole plan, not inline execution).

## What's NOT done: Phases 2-4

Full detail is in `.claude/PRPs/plans/fable-audit-fixes.plan.md`. Quick map:

- **Phase 2 (High/feature parity)** — Task 3: add result export (`buildExportText()` + Share/Copy) to `monitor`, `posture`, `vpnstatus`, `wifiaudit`, `wol` (currently the only 5 tool modules without it). Task 4: Pro-gate netlog's Share button (currently free-for-all, unlike every peer). Task 5: wire `SpeedTestHistoryDao` into `HistoryRepository`'s combined timeline (currently orphaned).
- **Phase 3 (Medium/consistency)** — Task 6: replace Posture's hardcoded `untrustedNetwork = true` stub — **this one needs a product decision from the user before any code is written** (what should "trusted network" actually mean — VPN active? user allowlist? not-a-public-hotspot heuristic?). Task 7: fix LAN-scan cancellation not closing blocking sockets promptly. Task 8: two hardcoded `contentDescription = "Back"` strings. Task 9: migrate Posture/VpnStatus off hardcoded hex colors onto the shared `LocalStatusColors` token. Task 10: trace whether `HistoryRepository.clearOlderThan()` is ever actually called (unbounded-growth risk if not).
- **Phase 4 (Low/docs)** — Task 11: fix CLAUDE.md's stale "13 modules have export" claim (real count is now 17 after Phase 2, or 15 currently) and log remaining backlog (DNS SRV/PTR/CAA record types, Ping interval/packet-size options, a follow-up typography/accessibility pass on unread screens).

**Note:** the plan's Gradle task names were fixed in place before Phase 1 execution — this repo's product flavors (`foss`/`gplay`) exist only at the `:app` module level, so feature/core module tasks are `testDebugUnitTest`/`assembleDebug`, not `testFossDebugUnitTest`/`assembleFossDebug` as the plan originally (and CLAUDE.md's "Build Commands" section still, as of this writing) states. Task 11 already has a line item to fix CLAUDE.md too.

**To resume:** re-invoke `superpowers:subagent-driven-development` against `.claude/PRPs/plans/fable-audit-fixes.plan.md`, starting at Task 3 (Tasks 1-2 are done; there's no ledger file to check since it lived in the now-deleted worktree — just start fresh at Task 3). Expect to hit the Task 6 product-decision checkpoint mid-way through Phase 3; surface it rather than guessing.

## Competitor research (not yet written to a file)

A background research agent compared NetLens against Fing, PingTools, WiFiman, NetX/NetNX, iNet Network Scanner, VREM WiFiAnalyzer, and traffic-monitoring apps (GlassWire/NetGuard/PCAPdroid). Full findings are in the conversation transcript around the message where the agent reported back (search for "NetLens Competitive Feature Research"). Two buckets were produced:

- **Enhancements to existing tools** (~7 ideas): LAN Scan gateway-MAC-change alert, one-tap service launch from discovered hosts, Port Scan banner grabbing, TLS weak-config grading, Speed Test bufferbloat score + trend history/scheduling, WiFi Analyzer best-channel recommendation, WoL post-send wake confirmation, Posture Score folding in DNS hygiene/gateway integrity.
- **Entirely new tools** (~6 ideas, roughly small→large scope): Rogue AP/evil-twin detector, iPerf3 client, per-app data usage monitor, Wi-Fi heatmap/floor-plan mapper, local traffic inspector (VpnService-based), multi-location ping (flagged as needing a hosted relay, the scope outlier).

**Not yet done:** this hasn't been folded into any backlog document. The user hadn't decided whether to merge it into Task 11's backlog note or keep it separate when the session paused — ask, or just fold it into Task 11 when that task comes up, since it already has a backlog-logging step.

## Key files

- Audit report (findings, corrected in place after Phase 1's whole-branch review): `.claude/PRPs/reports/fable-audit-2026-07-04.md`
- Fix plan (11 tasks, 4 phases): `.claude/PRPs/plans/fable-audit-fixes.plan.md`
- This handoff: `.claude/PRPs/HANDOFF-fable-audit-2026-07-04.md`

All three are gitignored (`.claude/PRPs/`) and currently uncommitted — they exist only on this local checkout unless force-added.
