package com.ventouxlabs.netlens.core.network

/**
 * Validation for host strings that did not come from the user.
 *
 * A host is frequently *not* ours: a hostile device on the LAN picks its own mDNS/NetBIOS name, a
 * DNS lookup result can be routed into another tool, and a traceroute hop is whatever the network
 * said it was. Any of those can end up in a URI authority or a navigation route, where `/ ? # @`
 * escape their position — `192.168.1.1@evil.example` reads as a LAN device and loads
 * `evil.example`.
 *
 * Validating beats escaping here: no legitimate host needs those characters, so anything carrying
 * them is rejected outright rather than rendered inert and shown anyway.
 */
object HostName {

    /**
     * The bare host — surrounding brackets and any IPv6 scope id stripped — or null if it is
     * neither a plain DNS name nor an IP literal.
     */
    fun sanitize(host: String): String? {
        val trimmed = host.trim().removeSurrounding("[", "]")
        // A link-local address carries a scope id ("fe80::1%wlan0"). '%' starts a percent-escape
        // in an authority, and the scope is meaningless to whichever app handles the intent — so
        // it is dropped, but only for an IPv6 literal. Stripping it from a DNS name would rewrite
        // "nas%00.local" into a different host ("nas") and hand it over as if it were what was
        // asked for; a name carrying '%' is refused instead.
        //
        // The colon test is load-bearing, not decoration. IPV6_LITERAL is deliberately loose and
        // has no colon of its own, so it matches any run of hex letters and dots — "dead",
        // "cafe", "1234". Without this, "cafe%evil.example" passed as a scoped literal and was
        // handed back as "cafe", which is the exact rewrite the paragraph above forbids. It only
        // looked fixed because the case that was tested ("nas") happens not to spell hex.
        val beforeScope = trimmed.substringBefore('%')
        val isScopedIpv6 = beforeScope != trimmed &&
            beforeScope.contains(':') &&
            IPV6_LITERAL.matches(beforeScope)
        val bare = if (isScopedIpv6) beforeScope else trimmed
        return when {
            bare.isEmpty() -> null
            bare.contains(':') -> if (IPV6_LITERAL.matches(bare)) bare else null
            DNS_NAME.matches(bare) -> bare
            else -> null
        }
    }

    /**
     * [sanitize] plus IPv6 bracketing, ready to drop into a URI authority.
     */
    fun toAuthority(host: String): String? {
        val bare = sanitize(host) ?: return null
        return if (bare.contains(':')) "[$bare]" else bare
    }

    /**
     * Letters, digits and inner hyphens or underscores per label — which also covers IPv4 dotted
     * quads. Underscores are not legal in a public DNS hostname, but they are everywhere on a LAN
     * (`my_nas`), and mDNS service labels use them by design; rejecting them hid working hosts.
     * They carry no meaning in a URI authority, so admitting them costs nothing.
     */
    private val DNS_NAME =
        Regex("^[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?(\\.[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?)*\\.?$")

    /** Deliberately loose — Uri handles the exact shape; this only bars URI metacharacters. */
    private val IPV6_LITERAL = Regex("^[0-9A-Fa-f:.]+$")
}
