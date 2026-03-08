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
        "jf" to "", // Jellyfin: URL is server-relative, built dynamically
    )

    private val TRACKER_LABELS = mapOf(
        "al" to "AniList",
        "mal" to "MyAnimeList",
        "mu" to "MangaUpdates",
        "jf" to "Jellyfin",
    )

    /** All recognized canonical ID prefixes. */
    val ALL_PREFIXES: Set<String> = TRACKER_URLS.keys

    /**
     * Creates a canonical ID string from a prefix and remote ID.
     * @return canonical ID (e.g. "al:21") or null if prefix is unrecognized.
     */
    fun create(prefix: String, remoteId: Long): String? {
        if (prefix !in TRACKER_URLS || remoteId <= 0) return null
        return "$prefix:$remoteId"
    }

    /**
     * Creates a canonical ID string from a prefix and a string remote ID.
     * Used for trackers with non-numeric IDs (e.g. Jellyfin UUIDs).
     * @return canonical ID (e.g. "jf:abc123") or null if prefix is unrecognized.
     */
    fun create(prefix: String, remoteId: String): String? {
        if (prefix !in TRACKER_URLS || remoteId.isBlank()) return null
        return "$prefix:$remoteId"
    }

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
     * Parses a canonical ID string into (prefix, stringRemoteId).
     * Supports both numeric and non-numeric remote IDs (e.g. Jellyfin UUIDs).
     * @return pair of (prefix, remoteId) or null if the format is invalid.
     */
    fun parseString(canonicalId: String): Pair<String, String>? {
        val parts = canonicalId.split(":", limit = 2)
        if (parts.size != 2) return null
        val prefix = parts[0].takeIf { it.isNotEmpty() } ?: return null
        val remoteId = parts[1].takeIf { it.isNotEmpty() } ?: return null
        return prefix to remoteId
    }

    /**
     * Builds a web URL for the given canonical ID.
     * @return URL string or null if the prefix is unrecognized or has no base URL.
     */
    fun toUrl(canonicalId: String): String? {
        val (prefix, remoteId) = parse(canonicalId)
            ?: parseString(canonicalId)?.let { it.first to it.second }
            ?: return null
        val baseUrl = TRACKER_URLS[prefix]?.takeIf { it.isNotEmpty() } ?: return null
        return "$baseUrl$remoteId"
    }

    /**
     * Returns a human-readable label for the authority tracker.
     * @return label (e.g. "AniList") or null if the prefix is unrecognized.
     */
    fun toLabel(canonicalId: String): String? {
        val (prefix, _) = parse(canonicalId)
            ?: parseString(canonicalId)
            ?: return null
        return TRACKER_LABELS[prefix]
    }

    /**
     * Attempts to extract a canonical ID from a tracker URL.
     * Supports AniList, MyAnimeList, and MangaUpdates URL formats.
     *
     * @return canonical ID (e.g. "al:21") or null if the URL is unrecognized.
     */
    fun fromUrl(url: String): String? {
        val trimmed = url.trim().removeSuffix("/")
        return when {
            // https://anilist.co/manga/21 or https://anilist.co/manga/21/title
            trimmed.contains("anilist.co/manga/") -> {
                val id = trimmed.substringAfter("anilist.co/manga/")
                    .substringBefore("/")
                    .toLongOrNull()
                id?.let { create("al", it) }
            }
            // https://myanimelist.net/manga/13 or https://myanimelist.net/manga/13/title
            trimmed.contains("myanimelist.net/manga/") -> {
                val id = trimmed.substringAfter("myanimelist.net/manga/")
                    .substringBefore("/")
                    .toLongOrNull()
                id?.let { create("mal", it) }
            }
            // https://www.mangaupdates.com/series.html?id=54321
            trimmed.contains("mangaupdates.com/series") -> {
                val id = trimmed.substringAfter("id=")
                    .substringBefore("&")
                    .toLongOrNull()
                id?.let { create("mu", it) }
            }
            else -> null
        }
    }
}
