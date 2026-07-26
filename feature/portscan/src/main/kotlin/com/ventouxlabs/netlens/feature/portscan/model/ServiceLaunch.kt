package com.ventouxlabs.netlens.feature.portscan.model

/** What kind of app a discovered service hands off to, which drives the button's wording. */
enum class ServiceLaunchKind {
    /** Opens in a browser. */
    WEB,

    /** Hands off to a terminal/remote-desktop client (ssh, telnet, vnc, rdp). */
    REMOTE,

    /** Hands off to a file client (ftp, smb). */
    FILES,
}

/**
 * A tappable action for an open port: the URI to fire as an `ACTION_VIEW` intent plus enough
 * context for the UI to label the button. Deliberately plain data with no Android types so the
 * port→URI mapping stays unit-testable; building and firing the intent is the caller's job.
 */
data class ServiceLaunch(
    val uri: String,
    val kind: ServiceLaunchKind,
    /** Human label for the target, e.g. "HTTP" or "Plex". */
    val serviceLabel: String,
)

/**
 * Maps an open port to something the phone can actually open.
 *
 * Only ports whose protocol has a registered URI scheme are listed — a database or a metrics
 * exporter has nothing useful to hand off to, so those stay non-tappable rather than opening a
 * browser at a port that will never speak HTTP. Non-web schemes (ssh, vnc, rdp, smb, ftp) are
 * offered even though no handler may be installed: the caller catches the failure and says so,
 * which is friendlier than hiding an action that works fine for anyone with a client app.
 */
object ServiceLauncher {

    private val WEB_PORTS: Map<Int, String> = mapOf(
        80 to "HTTP",
        81 to "HTTP",
        591 to "HTTP",
        3000 to "Grafana / dev server",
        5601 to "Kibana",
        8000 to "HTTP",
        8008 to "HTTP",
        8080 to "HTTP alt",
        8081 to "HTTP alt",
        8123 to "Home Assistant",
        8888 to "HTTP proxy / Jupyter",
        9090 to "Prometheus",
        9200 to "Elasticsearch",
        32400 to "Plex",
    )

    private val SECURE_WEB_PORTS: Map<Int, String> = mapOf(
        443 to "HTTPS",
        8006 to "Proxmox",
        8443 to "HTTPS alt",
        9443 to "HTTPS alt",
        6443 to "Kubernetes API",
    )

    // 631 is IPP: the daemon serves its admin UI over plain HTTP on the same port.
    private const val IPP_PORT = 631

    fun forPort(host: String, port: Int): ServiceLaunch? {
        val safeHost = sanitizeHost(host) ?: return null
        val authority = if (port == 80 || port == 443) safeHost else "$safeHost:$port"
        WEB_PORTS[port]?.let { label ->
            return ServiceLaunch("http://$authority", ServiceLaunchKind.WEB, label)
        }
        SECURE_WEB_PORTS[port]?.let { label ->
            return ServiceLaunch("https://$authority", ServiceLaunchKind.WEB, label)
        }
        return when (port) {
            IPP_PORT -> ServiceLaunch("http://$authority", ServiceLaunchKind.WEB, "Printer admin")
            22 -> ServiceLaunch("ssh://$safeHost", ServiceLaunchKind.REMOTE, "SSH")
            23 -> ServiceLaunch("telnet://$safeHost", ServiceLaunchKind.REMOTE, "Telnet")
            3389 -> ServiceLaunch("rdp://$safeHost", ServiceLaunchKind.REMOTE, "RDP")
            5900 -> ServiceLaunch("vnc://$safeHost", ServiceLaunchKind.REMOTE, "VNC")
            21 -> ServiceLaunch("ftp://$safeHost", ServiceLaunchKind.FILES, "FTP")
            139, 445 -> ServiceLaunch("smb://$safeHost", ServiceLaunchKind.FILES, "SMB share")
            else -> null
        }
    }

    /**
     * Accepts only a plain DNS name or an IP literal, returning it ready to drop into a URI
     * authority (IPv6 bracketed). Anything else yields null, and the caller shows no action.
     *
     * The host is not always ours: a hostile device on the LAN picks its own mDNS/NetBIOS name,
     * and a DNS lookup result can be routed into the port scanner. Without this, a host
     * containing `/ ? # @` escapes the authority position — `192.168.1.1@evil.example` reads as
     * a LAN device and loads `evil.example`. Validating beats escaping here because there is no
     * legitimate host that needs those characters.
     */
    private fun sanitizeHost(host: String): String? {
        val trimmed = host.trim().removeSurrounding("[", "]")
        // A link-local address carries a scope id ("fe80::1%wlan0"). '%' starts a percent-escape
        // in an authority, and the scope is meaningless to whichever app handles the intent.
        val bare = trimmed.substringBefore('%')
        return when {
            bare.isEmpty() -> null
            bare.contains(':') -> if (IPV6_LITERAL.matches(bare)) "[$bare]" else null
            DNS_NAME.matches(bare) -> bare
            else -> null
        }
    }

    /** Letters, digits and inner hyphens per label — which also covers IPv4 dotted quads. */
    private val DNS_NAME =
        Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.?$")

    /** Deliberately loose — Uri handles the exact shape; this only bars URI metacharacters. */
    private val IPV6_LITERAL = Regex("^[0-9A-Fa-f:.]+$")
}
