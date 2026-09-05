package com.ventouxlabs.netlens.core.data.model

/**
 * Encoding for [KnownDeviceEntity.fingerprintEvidence].
 *
 * Mirrors [DeviceTags]: evidence strings come from free text this app does not control (SSDP
 * friendly names, mDNS service labels) and can legitimately contain a comma, so every entry has
 * its separator character stripped before it is ever joined — a delimiter round-trip only works
 * if the delimiter genuinely cannot appear inside an entry, and no character is safe to assume
 * that about in vendor-supplied text.
 */
object FingerprintEvidence {

    /** Splits a stored column into a display list. Returns an empty list for null/blank input. */
    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Normalises a list of evidence strings into the stored form, de-duplicated, or null when
     * nothing survives normalisation — null keeps "no evidence" a single representation in the
     * database instead of competing with the empty string.
     */
    fun format(evidence: List<String>): String? {
        val cleaned = evidence.mapNotNull(::normalize).distinct()
        return if (cleaned.isEmpty()) null else cleaned.joinToString(",")
    }

    /** Merges freshly observed evidence into what's already stored, de-duplicated. */
    fun merge(existingRaw: String?, fresh: List<String>): String? = format(parse(existingRaw) + fresh)

    /** Joins a stored column into a human-readable sentence fragment for display. */
    fun formatForDisplay(raw: String?): String = parse(raw).joinToString(", ")

    /**
     * Drops the separator character so an entry can never split itself in two on round-trip.
     * Returns null when nothing survives (blank after trimming).
     */
    private fun normalize(entry: String): String? {
        val cleaned = entry.replace(',', ' ').trim()
        return cleaned.ifEmpty { null }
    }
}
