package tachiyomi.domain.manga.model

/**
 * Utilities for working with canonical IDs (e.g. "al:21", "mal:13", "mu:54321").
 *
 * A canonical ID uniquely identifies a manga across sources via an authority
 * tracker (AniList, MyAnimeList, MangaUpdates).
 */
object CanonicalId {

    private val TRACKER_URLS = mapOf(
        "al" to "https://anilist.co/manga/",
        "mal" to "https://myanimelist.net/manga/",
        "mu" to "https://www.mangaupdates.com/series.html?id=",
    )

    private val TRACKER_LABELS = mapOf(
        "al" to "AniList",
        "mal" to "MyAnimeList",
        "mu" to "MangaUpdates",
    )

    /**
     * Parses a canonical ID string into (prefix, remoteId).
     * @return pair of (prefix, remoteId) or null if the format is invalid.
     */
    fun parse(canonicalId: String): Pair<String, Long>? {
        val parts = canonicalId.split(":", limit = 2)
        if (parts.size != 2) return null
        val prefix = parts[0].takeIf { it.isNotEmpty() } ?: return null
        val remoteId = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return prefix to remoteId
    }

    /**
     * Builds a web URL for the given canonical ID.
     * @return URL string or null if the prefix is unrecognized.
     */
    fun toUrl(canonicalId: String): String? {
        val (prefix, remoteId) = parse(canonicalId) ?: return null
        val baseUrl = TRACKER_URLS[prefix] ?: return null
        return "$baseUrl$remoteId"
    }

    /**
     * Returns a human-readable label for the authority tracker.
     * @return label (e.g. "AniList") or null if the prefix is unrecognized.
     */
    fun toLabel(canonicalId: String): String? {
        val (prefix, _) = parse(canonicalId) ?: return null
        return TRACKER_LABELS[prefix]
    }
}
