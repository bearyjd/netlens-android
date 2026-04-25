# Plan: WHOIS + Reverse DNS

## Summary

Implement WHOIS lookup via raw TCP to WHOIS servers with referral following, and reverse DNS lookup via PTR records. Supports deep linking from IP Info and LAN Scan results.

## User Story

As a user, I want to look up WHOIS information and reverse DNS for any IP or domain, so that I can identify ownership and verify PTR records.

## Metadata

- **Complexity**: Medium
- **Branch**: feat/whois-rdns
- **PR**: PR-08
- **Depends On**: PR-01 (IP info models for deep link)
- **Estimated Files**: 11
- **New Modules**: feature/whois (new)

## Patterns to Mirror

### FEATURE_MODULE
// SOURCE: feature/ipinfo/build.gradle.kts — with serialization if needed

### DEEP_LINK_NAV
// Navigation with arguments: composable("whois/{query}") { backStackEntry -> ... }

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `feature/whois/build.gradle.kts` | CREATE | netlens.android.feature, namespace com.ventoux.netlens.feature.whois, dep :core:network |
| `settings.gradle.kts` | UPDATE | Add `include(":feature:whois")` |
| `app/build.gradle.kts` | UPDATE | Add implementation project dep |
| `feature/whois/src/main/kotlin/com.ventoux.netlens/feature/whois/model/WhoisResult.kt` | CREATE | data class: query, registrar, creationDate, expirationDate, nameServers, rawText |
| `feature/whois/src/main/kotlin/com.ventoux.netlens/feature/whois/model/ReverseDnsResult.kt` | CREATE | data class: ip, hostname |
| `feature/whois/src/main/kotlin/com.ventoux.netlens/feature/whois/model/WhoisUiState.kt` | CREATE | data class: query, whoisResult, reverseDns, isLoading, activeTab (WHOIS/RDNS), error |
| `feature/whois/src/main/kotlin/com.ventoux.netlens/feature/whois/engine/WhoisClient.kt` | CREATE | Interface: suspend fun query(domain: String): Result<WhoisResult> |
| `feature/whois/src/main/kotlin/com.ventoux.netlens/feature/whois/engine/WhoisClientImpl.kt` | CREATE | Raw TCP Socket to whois.iana.org:43. Send "domain\r\n". Read response. Parse "refer:" field for referral server. Follow up to 3 referrals. Parse key fields from final response. Run on Dispatchers.IO. |
| `feature/whois/src/main/kotlin/com.ventoux.netlens/feature/whois/engine/ReverseDnsLookup.kt` | CREATE | Interface + impl: InetAddress.getByName(ip).canonicalHostName (returns hostname or IP if no PTR). Alternative: DnsResolver PTR query. |
| `feature/whois/src/main/kotlin/com.ventoux.netlens/feature/whois/di/WhoisModule.kt` | CREATE | @Module binding clients |
| `feature/whois/src/main/kotlin/com.ventoux.netlens/feature/whois/WhoisViewModel.kt` | CREATE | @HiltViewModel, lookup(query), toggle WHOIS/RDNS tab |
| `feature/whois/src/main/kotlin/com.ventoux.netlens/feature/whois/WhoisScreen.kt` | CREATE | Query TextField, tab row (WHOIS | Reverse DNS), WHOIS tab shows parsed fields + raw text (expandable), RDNS tab shows hostname |
| `app/src/main/kotlin/com.ventoux.netlens/navigation/NetLensNavHost.kt` | UPDATE | Add composable route "whois?query={query}" with optional argument |

## Step-by-Step Tasks

### Task 1: Create feature module
- **ACTION**: Create build.gradle.kts, update settings.gradle.kts, update app/build.gradle.kts
- **VALIDATE**: `./gradlew :feature:whois:compileDebugKotlin`

### Task 2: Create models
- **ACTION**: WhoisResult, ReverseDnsResult, WhoisUiState as specified
- **VALIDATE**: Compiles

### Task 3: Create WhoisClient
- **ACTION**: Interface + impl. On Dispatchers.IO: `Socket().use { socket -> socket.connect(InetSocketAddress(server, 43), 10_000); OutputStreamWriter(socket.outputStream).use { it.write("$query\r\n"); it.flush() }; BufferedReader(InputStreamReader(socket.inputStream)).readText() }`. Parse "refer:" for referral. Follow up to 3 hops. Parse registrar, dates, nameservers from final text using regex.
- **VALIDATE**: Unit test with mock server or test parsing logic

### Task 4: Create ReverseDnsLookup
- **ACTION**: Interface + impl. `InetAddress.getByName(ip).canonicalHostName`. If result equals input IP, no PTR found.
- **VALIDATE**: Unit test

### Task 5: Create DI module
- **ACTION**: `WhoisModule` binding both clients
- **VALIDATE**: Compiles

### Task 6: Create WhoisViewModel
- **ACTION**: `@HiltViewModel`, `fun lookup(query: String)`. Runs both WHOIS and reverse DNS in parallel via `async`. Tab state.
- **VALIDATE**: Unit test with Turbine

### Task 7: Create WhoisScreen
- **ACTION**: OutlinedTextField for IP or domain. Lookup button. TabRow with WHOIS and Reverse DNS tabs. WHOIS tab: parsed fields (registrar, dates, nameservers) in card, expandable "Raw WHOIS" section. RDNS tab: IP → hostname mapping.
- **VALIDATE**: Preview renders

### Task 8: Wire navigation with deep link support
- **ACTION**: In NetLensNavHost add `composable("whois?query={query}", arguments = listOf(navArgument("query") { defaultValue = "" }))`. From IpInfoScreen, add "WHOIS" button that navigates to `whois?query=$ip`.
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - WHOIS response parsing (registrar, dates, referral detection)
  - `WhoisViewModel` — Turbine: parallel lookup, tab switching
- **Integration tests for**:
  - WHOIS query for known domain (network-dependent, optional)

## Validation
