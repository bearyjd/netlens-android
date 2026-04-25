# Plan: DNS Lookup

## Summary

Implement a DNS lookup tool supporting multiple record types (A, AAAA, MX, TXT, CNAME, NS, SOA) using Android's native DnsResolver (API 29+) for A/AAAA records and the dnsjava library for all other record types.

## User Story

As a user, I want to query DNS records for any domain by type, so that I can troubleshoot DNS configuration and verify record propagation.

## Metadata

- **Complexity**: Medium
- **Branch**: feat/dns-lookup
- **PR**: PR-04
- **Depends On**: scaffold
- **Estimated Files**: 12
- **New Modules**: none (feature/dns already exists with dnsjava dep)

## Patterns to Mirror

### FEATURE_MODULE
// SOURCE: feature/dns/build.gradle.kts — depends on :core:network, dnsjava

### SEALED_INTERFACE
// Kotlin rules: sealed interface for closed type hierarchies, exhaustive when

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/model/DnsRecordType.kt` | CREATE | enum class: A, AAAA, MX, TXT, CNAME, NS, SOA |
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/model/DnsRecord.kt` | CREATE | Sealed interface with data class variants per type |
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/model/DnsLookupUiState.kt` | CREATE | data class: domain, selectedType, records list, isLoading, error |
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/engine/DnsLookupEngine.kt` | CREATE | Interface: suspend fun resolve(domain: String, type: DnsRecordType): Result<List<DnsRecord>> |
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/engine/AndroidDnsResolver.kt` | CREATE | API 29+ DnsResolver for A/AAAA, wraps callback in suspendCancellableCoroutine |
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/engine/DnsJavaResolver.kt` | CREATE | Uses dnsjava SimpleResolver for MX, TXT, CNAME, NS, SOA. Maps org.xbill.DNS types to DnsRecord |
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/engine/CompositeDnsResolver.kt` | CREATE | Delegates A/AAAA to AndroidDnsResolver, others to DnsJavaResolver |
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/data/DnsLookupRepository.kt` | CREATE | Interface: suspend fun lookup(domain, type): Result<List<DnsRecord>> |
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/data/DnsLookupRepositoryImpl.kt` | CREATE | Wraps CompositeDnsResolver, adds Dispatchers.IO |
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/di/DnsModule.kt` | CREATE | @Module binding repository + providing resolvers |
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/DnsLookupViewModel.kt` | CREATE | @HiltViewModel, lookup(domain, type), state with records |
| `feature/dns/src/main/kotlin/com.ventoux.netlens/feature/dns/DnsLookupScreen.kt` | CREATE | Domain TextField, record type chips (FilterChip row), results list, each record type has distinct display |
| `app/src/main/kotlin/com.ventoux.netlens/navigation/NetLensNavHost.kt` | UPDATE | Replace PlaceholderScreen for Dns route |

## Step-by-Step Tasks

### Task 1: Create DnsRecordType enum
- **ACTION**: `enum class DnsRecordType { A, AAAA, MX, TXT, CNAME, NS, SOA }`
- **VALIDATE**: Compiles

### Task 2: Create DnsRecord sealed interface
- **ACTION**: Sealed interface with: `data class A(val ip: String)`, `data class AAAA(val ip: String)`, `data class MX(val priority: Int, val exchange: String)`, `data class TXT(val text: String)`, `data class CNAME(val target: String)`, `data class NS(val nameserver: String)`, `data class SOA(val mname: String, val rname: String, val serial: Long, val refresh: Int, val retry: Int, val expire: Int, val minimum: Int)`
- **VALIDATE**: Compiles

### Task 3: Create AndroidDnsResolver
- **ACTION**: Uses `android.net.DnsResolver.getInstance()`. For A records: `query(network, domain, DnsResolver.TYPE_A, ...)`. Wrap `DnsResolver.Callback` in `suspendCancellableCoroutine`. Parse raw byte answers into IP strings. Similarly for AAAA.
- **VALIDATE**: Unit test with mock (or manual device test)

### Task 4: Create DnsJavaResolver
- **ACTION**: Uses `org.xbill.DNS.SimpleResolver()` and `org.xbill.DNS.Lookup(domain, type)`. Map result `Record[]` to `DnsRecord` variants. Run on `Dispatchers.IO`.
- **VALIDATE**: Unit test

### Task 5: Create CompositeDnsResolver
- **ACTION**: `@Inject constructor(androidResolver: AndroidDnsResolver, dnsJavaResolver: DnsJavaResolver)`. Routes A/AAAA to android, everything else to dnsjava.
- **VALIDATE**: Unit test

### Task 6: Create repository + DI module
- **ACTION**: Standard interface/impl pair. DI module binds engine and repository.
- **VALIDATE**: Compiles

### Task 7: Create DnsLookupViewModel
- **ACTION**: `@HiltViewModel`, `fun lookup(domain: String, type: DnsRecordType)`. Validates domain format. Emits loading → results/error.
- **VALIDATE**: Unit test with Turbine

### Task 8: Create DnsLookupScreen
- **ACTION**: OutlinedTextField for domain. Horizontal scrollable FilterChip row for record types (A selected by default). "Lookup" button. Results list with type-specific rendering (MX shows priority, SOA shows all fields, etc). TopAppBar "DNS Lookup".
- **VALIDATE**: Preview renders

### Task 9: Wire navigation
- **ACTION**: Update NetLensNavHost for Dns route
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - `DnsJavaResolver` — mock Lookup results for each record type
  - `CompositeDnsResolver` — routing logic
  - `DnsLookupViewModel` — Turbine: lookup lifecycle, input validation
- **Integration tests for**:
  - Real DNS query for google.com A record (network-dependent, optional)

## Validation
