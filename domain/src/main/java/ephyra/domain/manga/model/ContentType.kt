package ephyra.domain.manga.model

/**
 * Categorizes the type of media content.
 *
 * Used to:
 * - Filter tracker suggestions to only those supporting this content type.
 * - Filter content source searches to sources that host this type.
 * - Display appropriate UI labels ("Manga", "Light Novel", etc.).
 * - Group library entries by content type.
 *
 * Stored as an integer in the database via [value]. New types can be added
 * without breaking existing data — unknown values map to [UNKNOWN].
 */
enum class ContentType(val value: Int) {
    /** Default: content type has not been determined yet. */
    UNKNOWN(0),

    /** Standard manga (comics, manhwa, manhua, webtoon, doujinshi, one-shot). */
    MANGA(1),

    /** Light novel or web novel — text-primary content with illustrations. */
    NOVEL(2),

    /** General book (artbook, reference, etc.) — non-serial. */
    BOOK(3),
    ;

    companion object {
        private val BY_VALUE = entries.associateBy { it.value }

        /** Resolves an integer DB value to a [ContentType], defaulting to [UNKNOWN]. */
        fun fromValue(value: Int): ContentType = BY_VALUE[value] ?: UNKNOWN

        /**
         * Infers [ContentType] from a tracker's `publishing_type` string.
         *
         * Tracker APIs return strings like "Manga", "Light Novel", "Manhwa",
         * "Oneshot", "Novel", "Manhua", "Doujinshi", "OEL", etc.
         * This maps those to our canonical content types.
         */
        fun fromPublishingType(publishingType: String): ContentType {
            return when (publishingType.lowercase().trim()) {
                // Standard manga variants (including regional terms)
                "manga", "manhwa", "manhua", "webtoon", "comic",
                "oneshot", "one_shot", "one-shot", "one shot",
                "doujinshi", "doujin",
                "oel", // Original English Language manga
                -> MANGA

                // Novel variants
                "novel", "light novel", "light_novel",
                "web novel", "web_novel",
                -> NOVEL

                // Book variants
                "artbook", "art book", "art_book",
                -> BOOK

                else -> UNKNOWN
            }
        }

        /**
         * Genre keywords that indicate webtoon-style long-strip content,
         * suggesting a continuous vertical reader mode.
         */
        val WEBTOON_GENRE_KEYWORDS = setOf(
            "webtoon",
            "long strip",
            "long-strip",
            "longstrip",
            "manhwa",
            "manhua",
        )

        /**
         * Checks whether [genres] contain any keyword that suggests
         * webtoon-style content (continuous vertical scrolling).
         */
        fun isLikelyWebtoon(genres: List<String>?): Boolean {
            if (genres.isNullOrEmpty()) return false
            return genres.any { genre ->
                WEBTOON_GENRE_KEYWORDS.any { keyword ->
                    genre.lowercase().contains(keyword)
                }
            }
        }

        /**
         * Checks whether a [publishingType] string indicates webtoon format.
         */
        fun isWebtoonPublishingType(publishingType: String): Boolean {
            return when (publishingType.lowercase().trim()) {
                "webtoon", "manhwa", "manhua" -> true
                else -> false
            }
        }
    }
}
