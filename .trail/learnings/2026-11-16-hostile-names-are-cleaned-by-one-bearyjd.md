---
title: Hostile names are cleaned by one shared rule that strips bidi and invisible characters
author: Bearyjd
date: 2026-11-16
branch: fix/displaytext-bidi-stripping
cost: { minutes: 27, tokens: 73838 }
id: l-a6e2b2be
scope: repo
status: active
src: agent
---

## Decided
Display and export text from the network goes through DisplayText.flatten, which strips bidi controls and invisible characters (zero-width space, word joiner, BOM, Hangul fillers, braille blank, tag block, variation-selector payloads), turns line and paragraph separators into spaces, caps combining marks, and returns null when nothing visible is left. Joiners (ZWJ/ZWNJ) survive only between two non-ASCII letters or emoji, so emoji families and Persian/Indic words stay intact. The devices screen's own sanitiser now calls it. Identifier key material is rejected, not cleaned, when flatten would change it.

## Tried and ruled out
Stripping every format character: it breaks emoji sequences and Persian/Indic words. Cleaning identifiers instead of rejecting them: two different hostile identifiers could collapse to one network key. Clamping a key before checking it: the cut could split a surrogate pair.

## Watch out
Stripping a zero-width space joins words in Thai and Khmer. Word-final joiners are dropped. Run Gradle test modules one at a time: several together hit the "Gradle Test Executor finished with non-zero exit value" flake, and a "could not connect to the daemon" failure leaves stale test results that look green.

In the code: DisplayText.flatten, safeDisplay, NetworkKey.clean.
