package com.ventouxlabs.netlens.core.data.model

/**
 * Encoding for [KnownDeviceEntity.tags].
 *
 * Tags are stored as a single comma-separated column rather than a join table: a device carries
 * a handful of short labels, they are only ever read back with the row, and filtering happens
 * in memory over an inventory that is already fully loaded for the list. Everything that writes
 * the column goes through [format] so the stored form stays normalised, and everything that
 * reads it goes through [parse].
 */
object DeviceTags {

    const val MAX_TAGS = 12
    const val MAX_TAG_LENGTH = 24

    private val WHITESPACE = Regex("\\s+")

    /** Splits a stored column into display tags. Returns an empty list for null/blank input. */
    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').mapNotNull(::normalize).distinctByLowercase()
    }

    /**
     * Normalises a list of user-entered tags into the stored form, or null when nothing survives
     * normalisation — null keeps "no tags" a single representation in the database instead of
     * competing with the empty string.
     */
    fun format(tags: List<String>): String? {
        val cleaned = tags.mapNotNull(::normalize).distinctByLowercase().take(MAX_TAGS)
        return if (cleaned.isEmpty()) null else cleaned.joinToString(",")
    }

    /** Convenience for the "type tags into one text field" entry path. */
    fun formatFromInput(input: String): String? = format(input.split(','))

    /**
     * Trims, collapses internal whitespace and drops the separator character, so a tag can never
     * split itself in two when it round-trips through storage. Returns null when empty.
     */
    fun normalize(tag: String): String? {
        val cleaned = tag.replace(',', ' ').replace(WHITESPACE, " ").trim().take(MAX_TAG_LENGTH).trim()
        return cleaned.ifEmpty { null }
    }

    /** True when any tag in [raw] contains [query], case-insensitively. */
    fun matches(raw: String?, query: String): Boolean =
        parse(raw).any { it.contains(query, ignoreCase = true) }

    /** True when [raw] carries [tag] exactly (ignoring case) — used by the tag filter chips. */
    fun hasTag(raw: String?, tag: String): Boolean =
        parse(raw).any { it.equals(tag, ignoreCase = true) }

    private fun List<String>.distinctByLowercase(): List<String> {
        val seen = HashSet<String>(size)
        return filter { seen.add(it.lowercase()) }
    }
}
