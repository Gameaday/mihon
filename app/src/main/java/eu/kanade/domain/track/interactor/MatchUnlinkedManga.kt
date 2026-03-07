package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.ContentType
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.mergedAlternativeTitles
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.track.interactor.GetTracks

/**
 * Comprehensive resolver for canonical IDs on all unlinked library manga.
 *
 * Combines two resolution strategies, applied in priority order:
 * 1. **Tracker bindings** (fast, zero API calls): If the manga already has a tracker binding
 *    (MAL, AniList, MangaUpdates) but no canonical ID, derive the ID from the binding.
 * 2. **Search** (slower, 1 API call per manga): Search a queryable tracker API by title.
 *    Uses alternative titles and normalized comparison for better matching.
 *    A tracker is queryable if it has a canonical prefix AND is either logged in or
 *    supports public (unauthenticated) search.
 *
 * When a match is found via search, also enriches the manga with authoritative metadata:
 * alternative titles, description, author/artist, and cover URL (only fills missing fields).
 *
 * Reports progress via an optional callback so the UI can show "Resolving… 5/20".
 */
class MatchUnlinkedManga(
    private val mangaRepository: MangaRepository,
    private val trackerManager: TrackerManager,
    private val getTracks: GetTracks,
) {

    /**
     * Result of a resolve operation.
     * @param linked Number resolved from existing tracker bindings (zero API calls).
     * @param matched Number resolved via public API search.
     * @param total Total unlinked manga that were processed.
     */
    data class MatchResult(val linked: Int, val matched: Int, val total: Int)

    /**
     * Resolves canonical IDs for all unlinked library manga.
     *
     * @param onProgress Optional callback invoked after each manga is processed.
     *                   Parameters are (current, total) where current is 1-based.
     * @return [MatchResult] with counts of linked, matched, and total processed.
     */
    suspend fun await(
        onProgress: (suspend (current: Int, total: Int) -> Unit)? = null,
    ): MatchResult = awaitInternal { current, total, _ ->
        onProgress?.invoke(current, total)
    }

    /**
     * Resolves canonical IDs for all unlinked library manga.
     * Extended version that reports the manga title along with progress,
     * allowing callers (e.g. notifications) to show which manga is being processed.
     *
     * @param onProgress Callback invoked after each manga is processed.
     *                   Parameters are (current, total, mangaTitle).
     * @return [MatchResult] with counts of linked, matched, and total processed.
     */
    suspend fun await(
        onProgress: suspend (current: Int, total: Int, title: String) -> Unit,
    ): MatchResult = awaitInternal(onProgress)

    private suspend fun awaitInternal(
        onProgress: (suspend (current: Int, total: Int, title: String) -> Unit)? = null,
    ): MatchResult = withIOContext {
        val favorites = mangaRepository.getFavorites()
        val unlinked = favorites.filter { it.canonicalId == null }
        if (unlinked.isEmpty()) return@withIOContext MatchResult(0, 0, 0)

        val total = unlinked.size
        var linked = 0
        var matched = 0

        // Default tracker for manga with unknown content type.
        // Content-type-specific trackers are resolved per-manga below.
        val defaultTracker = findQueryableTracker()
        val defaultPrefix = defaultTracker?.let { AddTracks.TRACKER_CANONICAL_PREFIXES[it.id] }

        for ((index, manga) in unlinked.withIndex()) {
            yield() // cooperative cancellation

            // Phase 1: Try existing tracker bindings (fast, authoritative)
            var canonicalId = resolveFromTrackerBindings(manga)
            var fromBinding = canonicalId != null

            // Phase 2: Fall back to tracker API search with improved matching.
            // Select tracker based on manga's content type — only queries authorities
            // for that type, saving API calls when content type is known.
            var matchedResult: TrackSearch? = null
            if (canonicalId == null) {
                val tracker = if (manga.contentType != ContentType.UNKNOWN) {
                    findQueryableTracker(manga.contentType)
                } else {
                    defaultTracker
                }
                val prefix = tracker?.let { AddTracks.TRACKER_CANONICAL_PREFIXES[it.id] }
                if (tracker != null && prefix != null) {
                    val searchResult = searchForMatch(manga, tracker, prefix)
                    canonicalId = searchResult?.first
                    matchedResult = searchResult?.second
                    fromBinding = false
                    // Rate limit between API searches to avoid tracker throttling.
                    // Only delay after search calls, not after tracker-binding lookups.
                    delay(API_RATE_LIMIT_MS)
                }
            }

            if (canonicalId != null) {
                try {
                    mangaRepository.update(MangaUpdate(id = manga.id, canonicalId = canonicalId))
                    if (fromBinding) {
                        logcat(LogPriority.INFO) {
                            "Linked '${manga.title}' → canonical_id=$canonicalId (from tracker)"
                        }
                        linked++
                    } else {
                        logcat(LogPriority.INFO) {
                            "Matched '${manga.title}' → canonical_id=$canonicalId (from search)"
                        }
                        matched++
                    }

                    // Enrich manga with authoritative metadata from the search result.
                    // Only fills fields that are currently missing — never overwrites user data.
                    if (matchedResult != null) {
                        enrichFromSearchResult(manga, matchedResult)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to update manga ${manga.id}" }
                }
            }

            onProgress?.invoke(index + 1, total, manga.title)
        }
        MatchResult(linked, matched, total)
    }

    /**
     * Resolves the canonical ID for a single manga.
     * Uses the same two-phase strategy as [await] but for a single entry.
     *
     * @return The resolved canonical ID, or null if no match was found.
     */
    suspend fun awaitSingle(manga: Manga): String? = withIOContext {
        if (manga.canonicalId != null) return@withIOContext manga.canonicalId

        // Phase 1: Try existing tracker bindings
        var canonicalId = resolveFromTrackerBindings(manga)
        var matchedResult: TrackSearch? = null

        // Phase 2: Fall back to API search
        if (canonicalId == null) {
            val tracker = findQueryableTracker(manga.contentType)
            val prefix = tracker?.let { AddTracks.TRACKER_CANONICAL_PREFIXES[it.id] }
            if (tracker != null && prefix != null) {
                val searchResult = searchForMatch(manga, tracker, prefix)
                canonicalId = searchResult?.first
                matchedResult = searchResult?.second
            }
        }

        if (canonicalId != null) {
            mangaRepository.update(MangaUpdate(id = manga.id, canonicalId = canonicalId))
            if (matchedResult != null) {
                enrichFromSearchResult(manga, matchedResult)
            }
        }
        canonicalId
    }

    /**
     * Checks whether at least one tracker is available for canonical ID resolution.
     * A tracker is queryable if it has a canonical prefix AND is either logged in
     * or supports unauthenticated public search.
     * Use this to determine whether to show the "Link to authority" action.
     */
    fun hasQueryableTracker(): Boolean = findQueryableTracker() != null

    /**
     * Finds the best queryable tracker for search operations.
     * Prefers public-search trackers (no login needed), then falls back to
     * logged-in trackers that have canonical prefixes.
     *
     * When a [contentType] is specified, only considers trackers that are
     * authorities for that type — saving API calls by not querying services
     * that don't cover the requested content type.
     */
    private fun findQueryableTracker(contentType: ContentType = ContentType.UNKNOWN): Tracker? {
        val validTrackerIds = AddTracks.trackersForContentType(contentType)

        // First try public-search trackers that support this content type
        val publicTracker = AddTracks.TRACKERS_WITH_PUBLIC_SEARCH
            .filter { it in validTrackerIds }
            .firstNotNullOfOrNull { id -> trackerManager.get(id) }
        if (publicTracker != null) return publicTracker

        // Then try any logged-in tracker with a canonical prefix that supports this type
        return validTrackerIds
            .filter { it in AddTracks.TRACKER_CANONICAL_PREFIXES }
            .firstNotNullOfOrNull { id ->
                trackerManager.get(id)?.takeIf { it.isLoggedIn }
            }
    }

    /**
     * Resolves the canonical ID from existing tracker bindings (zero API calls).
     * Returns the first authoritative canonical ID found, or null.
     */
    private suspend fun resolveFromTrackerBindings(manga: Manga): String? {
        val tracks = getTracks.await(manga.id)
        for (track in tracks) {
            if (track.remoteId <= 0) continue
            val trackPrefix = AddTracks.TRACKER_CANONICAL_PREFIXES[track.trackerId] ?: continue
            return "$trackPrefix:${track.remoteId}"
        }
        return null
    }

    /**
     * Searches for a title match using a tracker's search API.
     * Uses a tiered matching strategy:
     * 1. Search by primary title, then check for exact/normalized match against all known titles
     * 2. If no match, search by each alternative title as a separate query
     * 3. Prefer results matching the manga's content type when known
     *
     * Returns a pair of (canonical ID, matched TrackSearch result) or null.
     */
    private suspend fun searchForMatch(
        manga: Manga,
        tracker: Tracker,
        prefix: String,
    ): Pair<String, TrackSearch>? {
        val allTitles = buildList {
            add(manga.title)
            addAll(manga.alternativeTitles)
        }

        // Phase 1: Search by primary title (skip if blank)
        if (manga.title.isNotBlank()) {
            val primaryMatch = searchAndMatch(manga.title, allTitles, manga.contentType, tracker, prefix)
            if (primaryMatch != null) return primaryMatch
        }

        // Phase 2: Try each alternative title as a separate search query
        for (altTitle in manga.alternativeTitles) {
            if (altTitle.isBlank()) continue
            yield() // cooperative cancellation between alt-title searches
            val altMatch = searchAndMatch(altTitle, allTitles, manga.contentType, tracker, prefix)
            if (altMatch != null) return altMatch
        }

        return null
    }

    /**
     * Performs a single tracker search and attempts to match results against known titles.
     * Returns a pair of (canonical ID, matched TrackSearch result) or null.
     */
    private suspend fun searchAndMatch(
        query: String,
        allTitles: List<String>,
        contentType: ContentType,
        tracker: Tracker,
        prefix: String,
    ): Pair<String, TrackSearch>? {
        return try {
            val results = tracker.search(query)

            // Tier 1: Exact case-insensitive match against any known title
            // Prefer results matching the manga's content type when known
            val exactMatches = results.filter { result ->
                result.remote_id > 0 &&
                    allTitles.any { title -> result.title.equals(title, ignoreCase = true) }
            }
            val exactMatch = pickBestByContentType(exactMatches, contentType)
            if (exactMatch != null) {
                return "$prefix:${exactMatch.remote_id}" to exactMatch
            }

            // Tier 2: Normalized match (strip punctuation, collapse whitespace)
            // Filter out blank normalized titles to prevent false positives on e.g. "!!!" → ""
            val normalizedTitles = allTitles.map { normalizeTitle(it) }
                .filter { it.isNotBlank() }
                .toSet()
            if (normalizedTitles.isNotEmpty()) {
                val normalizedMatches = results.filter { result ->
                    result.remote_id > 0 && normalizeTitle(result.title).let {
                        it.isNotBlank() && it in normalizedTitles
                    }
                }
                val normalizedMatch = pickBestByContentType(normalizedMatches, contentType)
                if (normalizedMatch != null) {
                    return "$prefix:${normalizedMatch.remote_id}" to normalizedMatch
                }
            }
            null
        } catch (e: CancellationException) {
            throw e // Don't swallow cancellation — let WorkManager handle it promptly
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Search failed for '$query'" }
            null
        }
    }

    /**
     * Picks the best result from a list of matches, preferring one whose publishing_type
     * matches the manga's content type when known and multiple matches exist.
     */
    private fun pickBestByContentType(
        matches: List<TrackSearch>,
        contentType: ContentType,
    ): TrackSearch? {
        if (matches.isEmpty()) return null
        if (contentType != ContentType.UNKNOWN && matches.size > 1) {
            return matches.firstOrNull { result ->
                ContentType.fromPublishingType(result.publishing_type) == contentType
            } ?: matches.firstOrNull()
        }
        return matches.firstOrNull()
    }

    /**
     * Enriches a manga with authoritative metadata from a matched search result.
     * Only fills fields that are currently missing — never overwrites existing data.
     * This ensures authoritative info (description, author, artist, cover, alt titles)
     * from the canonical source is available throughout the app.
     */
    private suspend fun enrichFromSearchResult(manga: Manga, result: TrackSearch) {
        try {
            val description = result.summary.takeIf {
                it.isNotBlank() && manga.description.isNullOrBlank()
            }
            val author = result.authors.joinToString(", ").takeIf {
                it.isNotBlank() && manga.author.isNullOrBlank()
            }
            val artist = result.artists.joinToString(", ").takeIf {
                it.isNotBlank() && manga.artist.isNullOrBlank()
            }
            val thumbnailUrl = result.cover_url.takeIf {
                it.isNotBlank() && manga.thumbnailUrl.isNullOrBlank()
            }
            // Infer content type from tracker's publishing_type if not already set
            val contentType = ContentType.fromPublishingType(result.publishing_type).takeIf {
                it != ContentType.UNKNOWN && manga.contentType == ContentType.UNKNOWN
            }

            // Only update if there's something new to add
            if (description != null || author != null || artist != null ||
                thumbnailUrl != null || contentType != null
            ) {
                mangaRepository.update(
                    MangaUpdate(
                        id = manga.id,
                        description = description,
                        author = author,
                        artist = artist,
                        thumbnailUrl = thumbnailUrl,
                        contentType = contentType,
                    ),
                )
                logcat(LogPriority.INFO) {
                    "Enriched '${manga.title}' with authoritative metadata"
                }
            }

            // Merge alternative titles from the search result
            if (result.alternative_titles.isNotEmpty()) {
                mergeAlternativeTitles(manga, result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Failed to enrich metadata for '${manga.title}'" }
        }
    }

    /**
     * Merges alternative titles from a search result into the manga's existing list.
     * Also adds the tracker's title as an alternative if it differs from the primary title.
     */
    private suspend fun mergeAlternativeTitles(manga: Manga, result: TrackSearch) {
        val newTitles = buildList {
            if (result.title.isNotBlank()) add(result.title)
            addAll(result.alternative_titles)
        }
        val merged = manga.mergedAlternativeTitles(newTitles) ?: return
        mangaRepository.update(
            MangaUpdate(id = manga.id, alternativeTitles = merged),
        )
        logcat(LogPriority.INFO) {
            "Added ${merged.size - manga.alternativeTitles.size} alt titles to '${manga.title}'"
        }
    }

    companion object {
        private val PUNCT_REGEX = Regex("[^\\p{L}\\p{N}\\s]")
        private val MULTI_SPACE_REGEX = Regex("\\s+")

        /**
         * Delay between API search calls during bulk matching to avoid tracker throttling.
         * Most tracker APIs have rate limits (e.g. AniList: 90/min, MU: unspecified).
         * A 500ms delay keeps us well within limits while still being fast enough
         * for large libraries (~120 manga/min).
         */
        private const val API_RATE_LIMIT_MS = 500L

        /**
         * Normalize a title for fuzzy comparison:
         * - lowercase
         * - replace punctuation with spaces (keeps letters, digits, whitespace)
         * - collapse multiple spaces into one
         */
        fun normalizeTitle(title: String): String {
            return title.lowercase()
                .replace(PUNCT_REGEX, " ")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
        }
    }
}
