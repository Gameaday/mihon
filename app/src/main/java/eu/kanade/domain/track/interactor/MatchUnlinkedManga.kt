package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.yield
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
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
 * 2. **Public search** (slower, 1 API call per manga): Search a public tracker API by title.
 *    Uses alternative titles and normalized comparison for better matching.
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
    ): MatchResult = withIOContext {
        val favorites = mangaRepository.getFavorites()
        val unlinked = favorites.filter { it.canonicalId == null }
        if (unlinked.isEmpty()) return@withIOContext MatchResult(0, 0, 0)

        val total = unlinked.size
        var linked = 0
        var matched = 0

        // Resolve the public-search tracker once (may be null if none available)
        val tracker = AddTracks.TRACKERS_WITH_PUBLIC_SEARCH
            .firstNotNullOfOrNull { id -> trackerManager.get(id) }
        val prefix = tracker?.let { AddTracks.TRACKER_CANONICAL_PREFIXES[it.id] }

        for ((index, manga) in unlinked.withIndex()) {
            yield() // cooperative cancellation

            // Phase 1: Try existing tracker bindings (fast, authoritative)
            var canonicalId = resolveFromTrackerBindings(manga)
            var fromBinding = canonicalId != null

            // Phase 2: Fall back to public API search with improved matching
            var matchedResult: TrackSearch? = null
            if (canonicalId == null && tracker != null && prefix != null) {
                val searchResult = searchForMatch(manga, tracker, prefix)
                canonicalId = searchResult?.first
                matchedResult = searchResult?.second
                fromBinding = false
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

            onProgress?.invoke(index + 1, total)
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

        // Phase 2: Fall back to public API search
        if (canonicalId == null) {
            val tracker = AddTracks.TRACKERS_WITH_PUBLIC_SEARCH
                .firstNotNullOfOrNull { id -> trackerManager.get(id) }
            val prefix = tracker?.let { AddTracks.TRACKER_CANONICAL_PREFIXES[it.id] }
            if (tracker != null && prefix != null) {
                val searchResult = searchForMatch(manga, tracker, prefix)
                canonicalId = searchResult?.first
                matchedResult = searchResult?.second
            }
        }

        if (canonicalId != null) {
            mangaRepository.update(tachiyomi.domain.manga.model.MangaUpdate(id = manga.id, canonicalId = canonicalId))
            if (matchedResult != null) {
                enrichFromSearchResult(manga, matchedResult)
            }
        }
        canonicalId
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
     * Searches for a title match using the tracker's public search API.
     * Uses a tiered matching strategy:
     * 1. Exact (case-insensitive) match against primary title
     * 2. Exact (case-insensitive) match against alternative titles
     * 3. Normalized match (stripped punctuation/whitespace) against all titles
     *
     * Returns a pair of (canonical ID, matched TrackSearch result) or null.
     */
    private suspend fun searchForMatch(
        manga: Manga,
        tracker: eu.kanade.tachiyomi.data.track.Tracker,
        prefix: String,
    ): Pair<String, TrackSearch>? {
        val allTitles = buildList {
            add(manga.title)
            addAll(manga.alternativeTitles)
        }
        return try {
            val results = tracker.search(manga.title)

            // Tier 1: Exact case-insensitive match against any known title
            val exactMatch = results.firstOrNull { result ->
                allTitles.any { title -> result.title.equals(title, ignoreCase = true) }
            }
            if (exactMatch != null && exactMatch.remote_id > 0) {
                return "$prefix:${exactMatch.remote_id}" to exactMatch
            }

            // Tier 2: Normalized match (strip punctuation, collapse whitespace)
            val normalizedTitles = allTitles.map { normalizeTitle(it) }.toSet()
            val normalizedMatch = results.firstOrNull { result ->
                normalizeTitle(result.title) in normalizedTitles
            }
            if (normalizedMatch != null && normalizedMatch.remote_id > 0) {
                "$prefix:${normalizedMatch.remote_id}" to normalizedMatch
            } else {
                null
            }
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Search failed for '${manga.title}'" }
            null
        }
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

            // Only update if there's something new to add
            if (description != null || author != null || artist != null || thumbnailUrl != null) {
                mangaRepository.update(
                    MangaUpdate(
                        id = manga.id,
                        description = description,
                        author = author,
                        artist = artist,
                        thumbnailUrl = thumbnailUrl,
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
