package com.ventouxlabs.netlens.core.network

/**
 * Flattening for network-supplied strings that get rendered into a delimited format.
 *
 * Sibling to [HostName], and the split between them is deliberate:
 *
 *  - [HostName] **validates**, returning null. Right for a URI authority or a nav route, where a
 *    stray `/ ? # @` changes which host is contacted and no legitimate host needs those.
 *  - This **flattens**, always returning something renderable. Right for display and export,
 *    where the string is only ever shown. Rejecting outright would drop legitimate names:
 *    an mDNS instance name is a human label and routinely holds spaces and punctuation
 *    (`Brian's MacBook Pro`), which [HostName.sanitize] correctly refuses.
 *
 * What makes this necessary: `LanScanViewModel`'s export writes one line per device as
 * `ip (hostname)  MAC=..  Vendor=..`. The hostname is picked by the device — an mDNS
 * `serviceName`, a NetBIOS name, or an SSDP `friendlyName` — and none of those parsers strip
 * inner control characters, because `.trim()` only touches the ends. A hostname of
 * `nas)\n192.168.1.1 (Router)` therefore forges a second device row in text the user shares.
 */
object DisplayText {

    /**
     * Replaces every C0/C1 control character with a space and collapses whitespace runs.
     *
     * Control characters are removed rather than escaped because the output is read by a person,
     * not re-parsed: a literal `\n` in the middle of a device row is noise, whereas an actual
     * newline is a forged row. Collapsing runs keeps a name that was padded out with tabs or
     * newlines from bloating a column.
     *
     * Returns null for null input, and null if nothing but whitespace survives — a name that
     * flattens to nothing is not a name, and callers already render null as "no hostname".
     */
    fun flatten(value: String?): String? {
        if (value == null) return null
        val flattened = buildString(value.length) {
            for (ch in value) {
                append(if (ch.isControlCharacter()) ' ' else ch)
            }
        }
        return flattened.replace(WHITESPACE_RUN, " ").trim().ifEmpty { null }
    }

    /** C0 (0x00-0x1F), DEL (0x7F) and C1 (0x80-0x9F). Covers CR, LF, NUL and tab. */
    private fun Char.isControlCharacter(): Boolean =
        this.code < 0x20 || this.code == 0x7F || (this.code in 0x80..0x9F)

    private val WHITESPACE_RUN = Regex("""\s{2,}""")
}
