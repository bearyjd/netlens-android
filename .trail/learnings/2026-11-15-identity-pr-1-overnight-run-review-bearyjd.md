---
title: "Identity PR 1 overnight run: review-loop pitfalls and CI-only checks"
author: Bearyjd
date: 2026-11-15
branch: chore/trail-learning-codex-review
cost: { minutes: 162, tokens: 831915 }
touches:
  - ".omc/plans/overnight-2026-11-14.md"
  - ".omc/STATE.md"
  - ".github/workflows/ci.yml"
---

## Decided
- Each queue item runs as executor (sonnet) -> codex author/reviewer/rebuttal chains + opus security/code review -> ONE consolidated fix pass sent back to the same executor via SendMessage -> I re-verify with a forced fresh test run (`--rerun-tasks`, check result-file mtimes are newer than the run) -> push + draft PR. The independent re-run caught a flake that looked like a failure and proved stale XMLs can mislead.
- Stacked PRs do not get CI: ci.yml only triggers for PRs targeting master, and retargeting a PR base does not fire the workflow (close+reopen does). Base stacked drafts on master instead.

## Tried and ruled out
- Trusting the executor's "gate green": CI has a shell step ("Reject literal control characters in source") that Gradle never runs; a test with literal bidi chars failed #209 in 31 s. Run the grep from .github/workflows/ci.yml locally and write such chars as \u escapes.
- A "no behaviour change" refactor (#211) silently moved an ARP-cache invalidation; only a line-by-line opus comparison against master caught it. Pin load-bearing call order with a recording fake.
- `pgrep -f` / `rm -f $VAR/*` are blocked by hooks; poll with gh/files instead.

## Watch out
- Default-ON auto-merge (#210) is low risk only because production discovery supplies no non-MAC identifiers and nothing sets `proven`; before identifier probes (PR 3) ship, a merge audit log, an edit-vs-merge guard and the suggestion UI (1.8) are required (merges delete the loser row, no undo).
- The 'Gradle Test Executor' exit-1 flake on a many-core box shows up on forced reruns; rerun once with --max-workers=2 before debugging.
