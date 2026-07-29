package com.ventouxlabs.netlens.navigation

import com.ventouxlabs.netlens.core.network.HostName

/**
 * Vets the `query` argument one tool hands to another before it becomes a navigation route.
 *
 * The strings crossing this seam are hostnames and IPs the *network* supplied — an mDNS or
 * NetBIOS name a LAN device chose for itself, a DNS answer, a traceroute hop. They land in the
 * destination's input field and, from there, in URIs the destination builds. `ServiceLauncher`
 * already refuses to launch such a host; this closes the sibling path that merely prefills it.
 *
 * Route-shaped queries are left alone: LAN Scan takes a CIDR and Devices takes a free-text
 * search, neither of which is a host. A query that fails vetting yields an empty one, so the tap
 * still opens the tool — with a blank field rather than a hostile string in it.
 */
object ToolQuery {

    /** Tools whose query argument is a bare host or IP. */
    private val HOST_ROUTES = setOf("ping", "traceroute", "whois", "dns", "tls", "portscan")

    /** Tools whose query argument is a full URL built around a host. */
    private val URL_ROUTES = setOf("httptester")

    fun sanitize(route: String, query: String): String = when (route) {
        in HOST_ROUTES -> HostName.sanitize(query).orEmpty()
        in URL_ROUTES -> sanitizeUrl(query)
        else -> query
    }

    /**
     * Accepts `scheme://host[:port][/rest]` only when the host survives [HostName.sanitize].
     *
     * Parsed by hand rather than with `Uri`, so the rule stays a plain unit test — there is no
     * Robolectric in this repo, and an Android type here would make it untestable.
     */
    private fun sanitizeUrl(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return ""
        val scheme = url.substring(0, schemeEnd)
        if (scheme != "http" && scheme != "https") return ""

        val afterScheme = url.substring(schemeEnd + 3)
        val authority = afterScheme.substringBefore('/')
        val rest = afterScheme.removePrefix(authority)
        // Anything before an '@' is userinfo, which is exactly the trick this guards against:
        // "192.168.1.1@evil.example" would otherwise read as a LAN device and load evil.example.
        if (authority.contains('@')) return ""

        // An IPv6 authority is bracketed, so split the port off the last ':' outside the brackets.
        val hostPart = if (authority.startsWith("[")) {
            authority.substringBefore(']').removePrefix("[")
        } else {
            authority.substringBefore(':')
        }
        val port = authority.removePrefix(if (authority.startsWith("[")) "[$hostPart]" else hostPart)
        if (port.isNotEmpty() && (!port.startsWith(":") || port.drop(1).any { !it.isDigit() })) return ""

        val safeAuthority = HostName.toAuthority(hostPart) ?: return ""
        return "$scheme://$safeAuthority$port$rest"
    }
}
