---
name: ip-api-http-only
description: ip-api.com free tier returns empty JSON over HTTPS — must use HTTP with cleartext exception
triggers:
  - ip-api.com
  - IpApiResponse
  - "fields are required"
  - "empty JSON"
  - ipinfo serialization
---

# ip-api.com Free Tier is HTTP-Only

## The Insight
ip-api.com's free tier silently degrades over HTTPS. Instead of returning an error or redirect, it returns `{}` (empty JSON object). This causes kotlinx.serialization to throw a "fields are required" error because all required fields are missing.

## Why This Matters
The failure mode is deceptive. You get a 200 OK with valid JSON, so the HTTP layer looks fine. The error surfaces as a serialization problem, sending you down the wrong debugging path (checking field names, `@SerialName` mappings, `ignoreUnknownKeys`, etc.) when the real issue is the transport protocol.

## Recognition Pattern
- `IpApiResponse` serialization fails with "fields are required"
- API call returns 200 but response body is `{}`
- Only happens on HTTPS; curl to HTTP endpoint returns full data

## The Approach
1. Use `http://ip-api.com/...` not `https://`
2. Add a domain-specific cleartext exception in `network_security_config.xml`:
   ```xml
   <domain-config cleartextTrafficPermitted="true">
       <domain includeSubdomains="true">ip-api.com</domain>
   </domain-config>
   ```
3. Keep the global `cleartextTrafficPermitted="false"` base config intact — only punch the hole for this one domain.
4. If upgrading to ip-api pro tier later, switch back to HTTPS and remove the exception.
