package com.ventoux.netlens.feature.dns.model

sealed interface DnsError {
    data object EmptyDomain : DnsError
    data object NoTypes : DnsError
    data class LookupFailed(val message: String?) : DnsError
}

data class DnsLookupUiState(
    val domain: String = "",
    val selectedTypes: Set<DnsRecordType> = setOf(DnsRecordType.A, DnsRecordType.AAAA),
    val results: List<DnsResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: DnsError? = null,
)
