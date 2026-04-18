package ephyra.domain.chapter.service

/**
 * Sanitizes raw chapter names fetched from sources by stripping leading manga titles and
 * extraneous separator characters.
 *
 * Pure business logic — no Android or data-layer dependencies.  Previously lived in `:data`
 * (`ephyra.data.chapter.ChapterSanitizer`) but belongs in `:domain` because it encodes a
 * business rule about how chapter names should be normalized, not a data-persistence concern.
 */
object ChapterSanitizer {

    fun String.sanitize(title: String): String {
        return trim()
            .removePrefix(title)
            .trim(*CHAPTER_TRIM_CHARS)
    }

    private val CHAPTER_TRIM_CHARS = arrayOf(
        // Whitespace
        ' ',
        '\u0009',
        '\u000A',
        '\u000B',
        '\u000C',
        '\u000D',
        '\u0020',
        '\u0085',
        '\u00A0',
        '\u1680',
        '\u2000',
        '\u2001',
        '\u2002',
        '\u2003',
        '\u2004',
        '\u2005',
        '\u2006',
        '\u2007',
        '\u2008',
        '\u2009',
        '\u200A',
        '\u2028',
        '\u2029',
        '\u202F',
        '\u205F',
        '\u3000',

        // Separators
        '-',
        '_',
        ',',
        ':',
    ).toCharArray()
}
