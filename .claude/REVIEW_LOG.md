# Review Log

## Plan 1: fix/lanscan-arp — PR #7

**Branch:** `fix/lanscan-arp`
**PR:** https://github.com/bearyjd/netatlas-android/pull/7
**Status:** Merged-ready (all tests pass, no blocking issues)
**Review rounds:** 2 (round 1 found 1 HIGH + 1 MEDIUM + 1 LOW; round 2 found 3 HIGH + 5 MEDIUM + 3 LOW)

### Round 1 Findings

| Severity | Finding | File:Line | Status |
|----------|---------|-----------|--------|
| HIGH | deviceCount local var mutated from two concurrent coroutines | LanScanViewModel.kt:92,108 | FIXED — derive from updatedDevices.size |
| MEDIUM | Hardcoded "Subnet:" and "devices" strings | LanScanScreen.kt:172,181 | FIXED — extracted to strings.xml |
| LOW | Operator precedence ambiguity in calculateIpRange | SubnetScannerImpl.kt:73 | COLLECTED |

### Round 2 Findings

| Severity | Finding | File:Line | Status |
|----------|---------|-----------|--------|
| HIGH | Shared MutableList in ConcurrentHashMap | LanMdnsScannerImpl.kt:32-55 | FIXED — immutable list accumulation |
| HIGH | resolveServiceSuspending has no timeout | LanMdnsScannerImpl.kt:97-128 | FIXED — withTimeoutOrNull(3000) |
| HIGH | Progress denominator hardcoded to 254 | LanScanViewModel.kt:117,149 | FIXED — compute from prefixLength |
| MEDIUM | startScan TOCTOU on isScanning guard | LanScanViewModel.kt:51 | FIXED — scanJob?.isActive |
| MEDIUM | Dead mac/vendor fields in LanDevice | LanDevice.kt:5-6 | FIXED — removed, rewrote fingerprinter |
| MEDIUM | Hardcoded "Back" contentDescription | LanScanScreen.kt:97 | FIXED — stringResource |
| MEDIUM | Hardcoded discovery method labels | LanScanScreen.kt:297-299 | FIXED — string resources |
| MEDIUM | Service type stripping broken (NSD trailing dots) | LanScanScreen.kt:327-329 | FIXED — trim('.') |
| LOW | maxOf for latency merge favors mDNS | LanScanViewModel.kt:103 | COLLECTED |
| LOW | SortOrder enum in ViewModel file | LanScanViewModel.kt:25 | COLLECTED |
| LOW | Operator precedence parentheses | SubnetScannerImpl.kt:73 | COLLECTED (from round 1) |

## Plan 2: fix/dns-lookup

**Branch:** `fix/dns-lookup`
**Status:** All tests pass, no blocking issues
**Review rounds:** 2

### Round 1 Findings

| Severity | Finding | File:Line | Status |
|----------|---------|-----------|--------|
| HIGH | SimpleResolver created per record type (perf + blocking constructor) | DnsResolverImpl.kt:36-38 | FIXED — per resolveType call (thread-safe) |
| HIGH | Raw exception message displayed in UI, leaks internal DNS details | DnsLookupScreen.kt:187-191 | FIXED — takeIf { isNotBlank() } with fallback string resource |
| HIGH | lookupJob not thread-safe for cancel-and-relaunch | DnsLookupViewModel.kt:26 | FIXED — main-thread-confined, no @Volatile needed |
| HIGH | No domain validation / SsrfGuard bypass | DnsResolverImpl.kt:35-41 | SKIPPED — DNS resolver talks to DNS servers, not user domain |
| MEDIUM | Lookup.run() null silently swallows SERVFAIL/UNRECOVERABLE | DnsResolverImpl.kt:40 | FIXED — check lookup.result, throw IOException |
| MEDIUM | Non-hermetic DnsResolverImplTest makes real network calls | DnsResolverImplTest.kt | COLLECTED — integration tests, not in CI |
| MEDIUM | LazyColumn key collision for duplicate DNS records | DnsLookupScreen.kt:209 | FIXED — indexed keys |
| MEDIUM | FakeDnsResolver ignores call arguments, trim test weak | FakeDnsResolver.kt:9 | FIXED — captures lastDomain/lastTypes |
| LOW | LookupFailed(null) vs empty string edge case | DnsLookupScreen.kt:190 | FIXED — takeIf { isNotBlank() } |
| LOW | navigate_back string in feature module instead of shared | strings.xml:4 | COLLECTED |

### Round 2 Findings

| Severity | Finding | File:Line | Status |
|----------|---------|-----------|--------|
| HIGH | SimpleResolver shared across async blocks (thread safety) | DnsResolverImpl.kt:29-34 | FIXED — per-resolveType construction |
| HIGH | @Volatile does not make check-then-act atomic | DnsLookupViewModel.kt:26 | FIXED — removed, access is main-thread-confined |
| MEDIUM | LazyColumn key still not collision-free for identical records | DnsLookupScreen.kt:209 | FIXED — indexed keys with stable counter |
| MEDIUM | isLoading set inside coroutine allows one-frame double-tap | DnsLookupViewModel.kt:55-57 | FIXED — moved state update before launch |
| MEDIUM | Untrimmed domain stays in UI state | DnsLookupViewModel.kt:44-58 | COLLECTED — cosmetic |
| LOW | Disabled button spinner barely visible | DnsLookupScreen.kt:166-182 | COLLECTED — UX |
| LOW | TTL format %d vs Long edge case | strings.xml:16 | COLLECTED — dnsjava caps at signed 32-bit |
