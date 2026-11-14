---
title: Cross-model PR review loop with codex CLI
author: Bearyjd
date: 2026-11-14
branch: master
cost: { minutes: 6, tokens: 17941 }
---

## Decided
- Open-PR sweep: dependabot action bumps (SHA-pinned) are verified by comparing the pinned SHA to the tag via `gh api repos/<org>/<repo>/commits/<tag> --jq .sha`, then merged with `gh pr merge --merge` (repo history uses merge commits).
- Adversarial author/reviewer/rebuttal loop runs as `codex exec -s read-only -C <worktree> -o <file>` chained in a script, one chain per topic, run in parallel in the background. The PR is checked out in a git worktree under the session scratchpad via `git fetch origin pull/N/head:branch`.

## Tried and ruled out
- Agent-tool `model: gpt-codex` / `gpt-5` are not selectable here; the codex CLI is the way to reach another model family. Model names are not known, so the default from ~/.codex/config.toml is used.
- `rm -f $VAR/*.done` is blocked by the safety check; use a literal path or `"${VAR:?}"` form, or skip the rm.

## Watch out
- PR #200 is a draft with an unbuilt gesture change (author could not build locally); CI is its first compile. Glance/pointer behavior still needs a device check.
