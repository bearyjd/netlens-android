---
title: "Reddit launch prep: claim audit and competitor pricing"
author: Bearyjd
date: 2026-10-22
branch: master
cost: { minutes: 1, tokens: 66185 }
touches:
  - PRIVACY.md
  - ".omc/STATE.md"
---

## Decided
- Before posting, fix README/PRIVACY/store claims; privacy mismatch is the highest risk.
- Audit found: F-Droid badge points to an unlisted package; PRIVACY.md names ip-api.com and ipapi.co but code calls ipinfo.io (IpInfoRepositoryImpl.kt, WidgetRefreshWorker.kt) and speed.cloudflare.com (SpeedTestEngineImpl.kt); "no personal data transmitted" is contradicted by public-IP lookups; store full_description is stale and omits Pro.
- Competitor pricing: Fing Starter $2.99/mo or $24.99/yr (fing.com/pricing); Fing Premium Android price unconfirmed (iOS tracker showed $7.99/mo). Cite sources and date, never memory.

## Tried and ruled out
- Web search for Reddit subreddit rules returned only Lemmy r/selfhosted rules (AI-project tags, 10% self-promo cap); not valid for Reddit. Fetch the real rules pages.
- Grepping "ipinfo" in Kotlin matches module/class names; grep URLs instead.

## Watch out
- Nothing has been edited or pushed; README/PRIVACY/store fixes and the post draft are still open. Never push or merge without the user saying so.
