package com.ventouxlabs.netlens.feature.dnsleak.model

data class ResolverInfo(
    val ip: String,
    val name: String,
    val isKnownPublic: Boolean,
)

sealed interface DnsLeakResult {
    data class NoLeak(val resolvers: List<ResolverInfo>) : DnsLeakResult
    data class LeakDetected(
        val leakedResolvers: List<ResolverInfo>,
        val expectedResolvers: List<ResolverInfo>,
    ) : DnsLeakResult
    data class Error(val message: String) : DnsLeakResult
}
