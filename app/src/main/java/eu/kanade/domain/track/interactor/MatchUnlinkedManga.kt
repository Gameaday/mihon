package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.yield
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
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
            if (canonicalId == null && tracker != null && prefix != null) {
                canonicalId = searchForCanonicalId(manga, tracker, prefix)
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
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to update manga ${manga.id}" }
                }
            }

            onProgress?.invoke(index + 1, total)
        }
        MatchResult(linked, matched, total)
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
     * Returns the canonical ID (e.g. "mu:12345") if a confident match is found.
     */
    private suspend fun searchForCanonicalId(
        manga: Manga,
        tracker: eu.kanade.tachiyomi.data.track.Tracker,
        prefix: String,
    ): String? {
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
                return "$prefix:${exactMatch.remote_id}"
            }

            // Tier 2: Normalized match (strip punctuation, collapse whitespace)
            val normalizedTitles = allTitles.map { normalizeTitle(it) }.toSet()
            val normalizedMatch = results.firstOrNull { result ->
                normalizeTitle(result.title) in normalizedTitles
            }
            if (normalizedMatch != null && normalizedMatch.remote_id > 0) {
                "$prefix:${normalizedMatch.remote_id}"
            } else {
                null
            }
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Search failed for '${manga.title}'" }
            null
        }
    }

    companion object {
        /**
         * Normalize a title for fuzzy comparison:
         * - lowercase
         * - replace punctuation with spaces (keeps letters, digits, whitespace)
         * - collapse multiple spaces into one
         */
        fun normalizeTitle(title: String): String {
            return title.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
