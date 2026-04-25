# NetLens Android — CSO Security Audit Report

**Date:** 2026-04-21
**Scope:** Full codebase — Kotlin + Jetpack Compose network tools suite
**Method:** OWASP Mobile Top 10, STRIDE threat modeling, manual code review

---

## Findings

### FINDING 1 — HIGH: ProcessBuilder Argument Injection (FIXED)

**File:** `feature/ping/src/main/kotlin/com.ventoux.netlens/feature/ping/engine/PingerImpl.kt:16`

`ProcessBuilder("ping", "-c", count.toString(), host)` — user-supplied `host` passed directly.

**Risk:** Not shell injection (ProcessBuilder list form doesn't invoke a shell), but a leading-dash value like `-f` could be interpreted as a flag by the `ping` binary (e.g., flood mode).

**Fix applied:** Added `validateHost()` with hostname/IP regex allowlist and leading-dash rejection.

---

### FINDING 2 — MEDIUM: HTTP Cleartext to ip-api.com

**File:** `feature/ipinfo/src/main/kotlin/com.ventoux.netlens/feature/ipinfo/data/IpInfoRepositoryImpl.kt`
**File:** `app/src/main/res/xml/network_security_config.xml`

Public IP, ISP, geolocation, and proxy status sent over plaintext HTTP. The `network_security_config.xml` correctly blocks cleartext as base config but carves out an explicit exception for `ip-api.com`.

**Risk:** On-path observers (captive portals, ISP, public WiFi) see the device's IP metadata.

**Recommendation:** Switch to an HTTPS provider (ipinfo.io free tier, or ip-api.com paid HTTPS plan) and remove the `domain-config` cleartext exception.

---

### FINDING 3 — MEDIUM: Unbounded Port Scan Input (FIXED)

**File:** `feature/portscan/src/main/kotlin/com.ventoux.netlens/feature/portscan/engine/PortScannerImpl.kt`

No upper bound on port list size. A caller passing all 65535 ports creates 1311 batches of 50 concurrent sockets.

**Fix applied:** Added `require(ports.size <= 10_000)` guard.

---

### FINDING 4 — LOW: WoL Broadcast IP/Port Unvalidated (FIXED)

**File:** `feature/wol/src/main/kotlin/com.ventoux.netlens/feature/wol/engine/WolSenderImpl.kt`

`broadcastIp` and `port` accepted without format or range validation.

**Fix applied:** Added IPv4 regex validation and port range check (1-65535).

---

### FINDING 5 — INFO: ArpTableReader — No Risk

**File:** `feature/lanscan/src/main/kotlin/com.ventoux.netlens/feature/lanscan/engine/ArpTableReader.kt`

Path is hardcoded constant `/proc/net/arp`. No user-controlled input reaches the file path. Clean.

---

### FINDING 6 — INFO: DnsResolverImpl — No Risk

**File:** `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/engine/DnsResolverImpl.kt`

dnsjava `Lookup` encodes domain as DNS wire-format label octets. No injection surface analogous to SQL or shell. Malformed domains return null, which is handled.

---

## OWASP Mobile Top 10 Assessment

| Category | Status | Notes |
|---|---|---|
| M1: Improper Credential Usage | N/A | No credentials stored |
| M2: Inadequate Supply Chain Security | PASS | All deps from Maven Central/Google |
| M3: Insecure Auth/Authz | N/A | No auth required |
| M4: Insufficient Input Validation | FIXED | Host, port list, broadcast IP validated |
| M5: Insecure Communication | WARN | HTTP to ip-api.com (see Finding 2) |
| M6: Inadequate Privacy Controls | PASS | No analytics, no tracking, no PII stored |
| M7: Insufficient Binary Protection | INFO | ProGuard/R8 not yet configured for release |
| M8: Security Misconfiguration | PASS | network_security_config.xml blocks cleartext by default |
| M9: Insecure Data Storage | PASS | Room DB stores only WoL targets (MAC + label) |
| M10: Insufficient Cryptography | N/A | No crypto operations |

## STRIDE Threat Model

| Threat | Component | Status |
|---|---|---|
| Spoofing | No auth (tool app, expected) | Accepted |
| Tampering | HTTP to ip-api.com | Mitigate: use HTTPS |
| Repudiation | No audit logging | Accepted for tool app |
| Information Disclosure | IP/location over HTTP | Mitigate: use HTTPS |
| Denial of Service | Unbounded port list | Fixed: capped at 10K |
| Elevation of Privilege | ProcessBuilder arg injection | Fixed: host validation |

## Data Classification

| Data | Sensitivity | Handling |
|---|---|---|
| Device public IP + geolocation | Medium | Sent over HTTP — recommend fix |
| LAN topology (IPs, MACs, vendors) | Medium | Local only, not transmitted |
| Scan targets (hosts, ports) | Low | Local only |
| WoL MAC addresses | Low | Stored in Room DB |
| DNS query domains | Low | Sent to system resolver |

## Summary

- **0 CRITICAL** findings
- **1 HIGH** finding (fixed)
- **2 MEDIUM** findings (1 fixed, 1 recommendation pending)
- **1 LOW** finding (fixed)
- **2 INFO** findings (no action needed)
- No hardcoded secrets detected
- No SQL injection (Room parameterized queries throughout)
- No shell injection (ProcessBuilder argument list form)
- No WebView usage

## Remaining Recommendation

Switch ip-api.com to an HTTPS endpoint to close the cleartext information disclosure. This is the only open finding.
