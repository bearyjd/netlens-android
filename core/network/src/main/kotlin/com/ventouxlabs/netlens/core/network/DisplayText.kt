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
     * Makes a network-supplied string safe to render and to write into a one-row-per-device export.
     * Works per code point, so a surrogate pair is never split. Rules, in order:
     *
     * - **Replaced with a space**: every C0/C1 control character (CR, LF, NUL, tab, U+0085 ...) and
     *   the Unicode line and paragraph separators U+2028 / U+2029. A literal `\n` mid-row is noise,
     *   whereas a real line break is a forged row.
     * - **Stripped (removed, no space left)**: [STRIPPED_CODE_POINTS] (bidi controls, directional
     *   marks, zero-width characters, blank-looking fillers), the Tag block U+E0000-U+E007F, the
     *   variation selectors U+FE00-U+FE0D and U+E0100-U+E01EF, and every other `Cf` format
     *   character. Removing them defeats Trojan Source reordering (`admin` + RLO + `gnp.exe`) and
     *   invisible-character spoofing. Decisions worth knowing: the `Cf` Arabic number/ayah marks
     *   U+0600-U+0605, U+06DD and U+070F are stripped too (they are never needed in a device name);
     *   and stripping the zero width space U+200B concatenates words in scripts that use it as a
     *   word separator (Thai, Khmer), which is accepted because the alternative is an invisible
     *   character that can hide or split a name.
     * - **Joiners U+200C (ZWNJ) / U+200D (ZWJ)** are `Cf` but required by emoji sequences and
     *   Persian/Indic words, so one is kept only when BOTH neighbours (after stripping, and looking
     *   past combining marks, so a virama or VS16 before the joiner is skipped to its base) are
     *   non-ASCII, non-space, non-joiner code points. At an edge, in a run, next to an ASCII
     *   character (`ad` + ZWJ + `min`) or behind ASCII + marks it is dropped: there it is an
     *   invisible spoof/payload channel with no linguistic job. Accepted fidelity loss: a
     *   word-final joiner (Malayalam chillu, Persian ZWNJ before ASCII punctuation or at the end of
     *   a word) is dropped too.
     * - **Variation selectors U+FE0E/U+FE0F** (text/emoji presentation) are kept only directly after
     *   an `OTHER_SYMBOL` code point (or an ASCII keycap base followed by U+20E3), else stripped.
     *   So VS16 after emoji bases that are not `OTHER_SYMBOL` (arrows, the information source,
     *   double exclamation mark) is stripped and the character renders in text style. Likewise the
     *   Tag-block characters of subdivision flags are stripped, so those degrade to a plain flag.
     * - **Combining marks** (`Mn`/`Me`/`Mc`) are capped at [MAX_COMBINING_MARKS] consecutive per base
     *   (zalgo text); Arabic harakat, Thai tone marks and stacked Vietnamese diacritics stay well
     *   under it. Joiners and variation selectors do not count towards, or reset, the run. The run
     *   resets at every non-mark character, so a total of [MAX_TOTAL_COMBINING_MARKS] marks per
     *   string is also enforced; names are short, so a legitimate name stays far below it.
     * - **Unpaired surrogates** are replaced with U+FFFD (they cannot be encoded and must not crash
     *   or corrupt an export); a valid pair is kept intact.
     *
     * Whitespace runs are then collapsed and the result trimmed. Returns null for null input, and
     * null unless at least one letter, digit, punctuation or symbol code point survives: a name made
     * only of invisible characters, joiners or marks is not a name, and callers already render null
     * as "no hostname". Idempotent: `flatten(flatten(x)) == flatten(x)`.
     *
     * This removes direction controls rather than isolating them. A caller that renders the result
     * inside a translated sentence and needs directional isolation wraps it with `bidiIsolate`
     * AFTER flatten, never before.
     */
    fun flatten(value: String?): String? {
        if (value == null) return null
        val kept = ArrayList<Int>(value.length)
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            i += Character.charCount(cp)
            when {
                isReplacedWithSpace(cp) -> kept.add(SPACE)
                cp in LONE_SURROGATES -> kept.add(REPLACEMENT)
                !isStripped(cp) -> kept.add(cp)
            }
        }
        val cleaned = resolveJoiners(capMarks(resolveVariationSelectors(kept)))
        val text = buildString(cleaned.size) { cleaned.forEach { appendCodePoint(it) } }
            .replace(WHITESPACE_RUN, " ")
            .trim()
        return text.takeIf { t -> t.codePoints().anyMatch { isVisibleContent(it) } }
    }

    /** Longest run of consecutive combining marks kept after a base character. */
    const val MAX_COMBINING_MARKS = 4

    /** Most combining marks kept in a whole string, however they are spread over bases. */
    const val MAX_TOTAL_COMBINING_MARKS = 32

    /**
     * Invisible, direction-changing or blank-looking code points that are removed outright. Listed
     * explicitly so the rule is auditable; the general `Cf` rule in [isStripped] covers many of them
     * too, and the non-`Cf` ones (fillers, braille blank, object replacement) only appear here.
     */
    private val STRIPPED_CODE_POINTS: Set<Int> = setOf(
        0x061C, // Arabic letter mark
        0x200E, 0x200F, // LRM, RLM
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E, // LRE, RLE, PDF, LRO, RLO
        0x2066, 0x2067, 0x2068, 0x2069, // LRI, RLI, FSI, PDI
        0x200B, // zero width space
        0x2060, // word joiner
        0xFEFF, // BOM / zero width no-break space
        0x180E, // Mongolian vowel separator
        0x00AD, // soft hyphen
        0x115F, 0x1160, 0x3164, 0xFFA0, // Hangul fillers
        0x2800, // braille pattern blank
        0x180B, 0x180C, 0x180D, 0x180F, // Mongolian free variation selectors
        0x17B4, 0x17B5, // Khmer inherent vowels (invisible)
        0x034F, // combining grapheme joiner
        0xFFFC, // object replacement character
    )

    private const val ZWNJ = 0x200C
    private const val ZWJ = 0x200D
    private const val VS15 = 0xFE0E
    private const val VS16 = 0xFE0F
    private const val KEYCAP = 0x20E3
    private const val SPACE = 0x20
    private const val REPLACEMENT = 0xFFFD

    private val TAG_BLOCK = 0xE0000..0xE007F
    private val VARIATION_SELECTORS = 0xFE00..0xFE0D
    private val VARIATION_SELECTORS_SUPPLEMENT = 0xE0100..0xE01EF
    private val LONE_SURROGATES = Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code

    private val VISIBLE_TYPES: Set<Int> = setOf(
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
        Character.MODIFIER_LETTER, Character.OTHER_LETTER, Character.DECIMAL_DIGIT_NUMBER,
        Character.LETTER_NUMBER, Character.OTHER_NUMBER, Character.CONNECTOR_PUNCTUATION,
        Character.DASH_PUNCTUATION, Character.START_PUNCTUATION, Character.END_PUNCTUATION,
        Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION,
        Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL,
    ).map { it.toInt() }.toSet()

    private val MARK_TYPES: Set<Int> = setOf(
        Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK,
    ).map { it.toInt() }.toSet()

    private fun isReplacedWithSpace(cp: Int): Boolean =
        cp < 0x20 || cp == 0x7F || cp in 0x80..0x9F || cp == 0x2028 || cp == 0x2029

    private fun isJoiner(cp: Int): Boolean = cp == ZWNJ || cp == ZWJ

    private fun isVariationSelector16Or15(cp: Int): Boolean = cp == VS15 || cp == VS16

    /** Decided here without looking at neighbours; joiners and VS15/VS16 are resolved later. */
    private fun isStripped(cp: Int): Boolean = when {
        isJoiner(cp) -> false
        cp in STRIPPED_CODE_POINTS || cp in TAG_BLOCK -> true
        cp in VARIATION_SELECTORS || cp in VARIATION_SELECTORS_SUPPLEMENT -> true
        else -> Character.getType(cp) == Character.FORMAT.toInt()
    }

    private fun isVisibleContent(cp: Int): Boolean = Character.getType(cp) in VISIBLE_TYPES

    private fun isMark(cp: Int): Boolean = Character.getType(cp) in MARK_TYPES

    private fun isKeycapBase(cp: Int): Boolean = cp in '0'.code..'9'.code || cp == '#'.code || cp == '*'.code

    private fun resolveVariationSelectors(cps: List<Int>): List<Int> = cps.filterIndexed { idx, cp ->
        if (!isVariationSelector16Or15(cp)) return@filterIndexed true
        val prev = cps.getOrNull(idx - 1) ?: return@filterIndexed false
        Character.getType(prev) == Character.OTHER_SYMBOL.toInt() ||
            (cp == VS16 && isKeycapBase(prev) && cps.getOrNull(idx + 1) == KEYCAP)
    }

    private fun capMarks(cps: List<Int>): List<Int> {
        val out = ArrayList<Int>(cps.size)
        var run = 0
        var total = 0
        for (cp in cps) {
            when {
                isJoiner(cp) || isVariationSelector16Or15(cp) -> out.add(cp)
                isMark(cp) -> if (++run <= MAX_COMBINING_MARKS && total < MAX_TOTAL_COMBINING_MARKS) { total++; out.add(cp) }
                else -> { run = 0; out.add(cp) }
            }
        }
        return out
    }

    private fun resolveJoiners(cps: List<Int>): List<Int> = cps.filterIndexed { idx, cp ->
        !isJoiner(cp) || (isJoinable(baseBefore(cps, idx)) && isJoinable(baseAfter(cps, idx)))
    }

    /** Nearest non-mark code point before [idx] (marks, including a virama or VS16, are skipped). */
    private fun baseBefore(cps: List<Int>, idx: Int): Int? {
        var j = idx - 1
        while (j >= 0 && isMark(cps[j])) j--
        return cps.getOrNull(j)
    }

    /** Nearest non-mark code point after [idx]. */
    private fun baseAfter(cps: List<Int>, idx: Int): Int? {
        var j = idx + 1
        while (j < cps.size && isMark(cps[j])) j++
        return cps.getOrNull(j)
    }

    private fun isJoinable(cp: Int?): Boolean =
        cp != null && cp > 0x7F && !isJoiner(cp) && !Character.isWhitespace(cp) && !Character.isSpaceChar(cp)

    private val WHITESPACE_RUN = Regex("""\s{2,}""")
}
