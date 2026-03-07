package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.yield
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Searches for canonical IDs for all library manga that don't have one yet.
 *
 * Uses trackers with public search APIs (currently MangaUpdates) to find matches by title,
 * then assigns the canonical ID to matching manga. This enables the authority model for
 * existing library content without requiring users to manually search for each entry.
 *
 * The flow:
 * 1. Find all favorites without a canonical ID
 * 2. For each, search a public-API tracker by title
 * 3. If a high-confidence match is found (exact title match), assign the canonical ID
 * 4. Skip ambiguous matches — the user can resolve those manually
 */
class MatchUnlinkedManga(
    private val mangaRepository: MangaRepository,
    private val trackerManager: TrackerManager,
) {

    /**
     * @return the number of manga that were matched to canonical IDs.
     */
    suspend fun await(): Int = withIOContext {
        val favorites = mangaRepository.getFavorites()
        val unlinked = favorites.filter { it.canonicalId == null }
        if (unlinked.isEmpty()) return@withIOContext 0

        // Use the first available public-search tracker
        val tracker = AddTracks.TRACKERS_WITH_PUBLIC_SEARCH
            .firstNotNullOfOrNull { id -> trackerManager.get(id) }
            ?: return@withIOContext 0

        val prefix = AddTracks.TRACKER_CANONICAL_PREFIXES[tracker.id]
            ?: return@withIOContext 0

        var matched = 0
        for (manga in unlinked) {
            yield() // Allow cooperative cancellation and maintain UI responsiveness
            val canonicalId = searchForCanonicalId(manga, tracker, prefix)
            if (canonicalId != null) {
                try {
                    mangaRepository.update(MangaUpdate(id = manga.id, canonicalId = canonicalId))
                    logcat(LogPriority.INFO) {
                        "Matched '${manga.title}' → canonical_id=$canonicalId"
                    }
                    matched++
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to update manga ${manga.id}" }
                }
            }
        }
        matched
    }

    /**
     * Searches for an exact title match using the tracker's public search API.
     * Returns the canonical ID (e.g. "mu:12345") if a confident match is found.
     */
    private suspend fun searchForCanonicalId(
        manga: Manga,
        tracker: eu.kanade.tachiyomi.data.track.Tracker,
        prefix: String,
    ): String? {
        return try {
            val results = tracker.search(manga.title)
            // Only assign if we find an exact (case-insensitive) title match
            val match = results.firstOrNull { result ->
                result.title.equals(manga.title, ignoreCase = true)
            }
            if (match != null && match.remote_id > 0) {
                "$prefix:${match.remote_id}"
            } else {
                null
            }
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Search failed for '${manga.title}'" }
            null
        }
    }
}
