package us.beary.netlens.feature.dns.model

data class DnsLookupUiState(
    val domain: String = "",
    val selectedTypes: Set<DnsRecordType> = setOf(DnsRecordType.A, DnsRecordType.AAAA),
    val results: List<DnsResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
