package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.mergedAlternativeTitles
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Refreshes authoritative metadata for a manga from its canonical tracker source.
 *
 * Parses the manga's [Manga.canonicalId] (e.g. "mu:12345", "al:21") to identify the
 * tracker, searches by title to retrieve the full [TrackSearch] result with matching
 * remote ID, and updates metadata fields that have changed.
 *
 * Unlike [MatchUnlinkedManga.enrichFromSearchResult] which only fills empty fields,
 * this refresher **overwrites** existing metadata to capture updates from the
 * authoritative source (e.g. series status changing from "Publishing" to "Completed",
 * updated descriptions, new cover art).
 */
class RefreshCanonicalMetadata(
    private val mangaRepository: MangaRepository,
    private val trackerManager: TrackerManager,
) {

    /**
     * Refreshes metadata for a single manga from its canonical tracker source.
     *
     * @param manga The manga to refresh. Must have a non-null [Manga.canonicalId].
     * @return true if metadata was updated, false if no update was needed or possible.
     */
    suspend fun await(manga: Manga): Boolean = withIOContext {
        val canonicalId = manga.canonicalId ?: return@withIOContext false

        val (prefix, remoteId) = parseCanonicalId(canonicalId) ?: return@withIOContext false

        val trackerId = CANONICAL_PREFIX_TO_TRACKER[prefix] ?: run {
            logcat(LogPriority.DEBUG) { "Unknown canonical prefix: $prefix" }
            return@withIOContext false
        }

        val tracker = trackerManager.get(trackerId) ?: return@withIOContext false
        if (!tracker.isLoggedIn && trackerId !in AddTracks.TRACKERS_WITH_PUBLIC_SEARCH) {
            return@withIOContext false
        }

        try {
            val result = findByRemoteId(tracker, manga.title, remoteId) ?: return@withIOContext false
            applyMetadataUpdate(manga, result)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to refresh canonical metadata for '${manga.title}'" }
            false
        }
    }

    /**
     * Searches the tracker by title and returns the result matching the given remote ID.
     * This is more efficient than fetching all results — we only need the one matching our ID.
     */
    private suspend fun findByRemoteId(
        tracker: eu.kanade.tachiyomi.data.track.Tracker,
        title: String,
        remoteId: Long,
    ): TrackSearch? {
        val results = tracker.search(title)
        return results.firstOrNull { it.remote_id == remoteId }
    }

    /**
     * Applies metadata from the tracker result to the manga.
     * Overwrites existing fields with authoritative data when the tracker provides
     * non-blank values — this captures updates like status changes, new descriptions,
     * and updated cover art.
     */
    private suspend fun applyMetadataUpdate(manga: Manga, result: TrackSearch): Boolean {
        val description = result.summary.takeIf { it.isNotBlank() }
        val author = result.authors.joinToString(", ").takeIf { it.isNotBlank() }
        val artist = result.artists.joinToString(", ").takeIf { it.isNotBlank() }
        val thumbnailUrl = result.cover_url.takeIf { it.isNotBlank() }
        val status = mapTrackerStatus(result.publishing_status)

        // Check if there's actually something to update
        val hasChanges = (description != null && description != manga.description) ||
            (author != null && author != manga.author) ||
            (artist != null && artist != manga.artist) ||
            (thumbnailUrl != null && thumbnailUrl != manga.thumbnailUrl) ||
            (status != null && status != manga.status)

        if (!hasChanges) {
            // Still merge alt titles even if main metadata didn't change
            mergeAlternativeTitles(manga, result)
            return false
        }

        mangaRepository.update(
            MangaUpdate(
                id = manga.id,
                description = description,
                author = author,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                status = status,
            ),
        )

        // Also merge alternative titles
        mergeAlternativeTitles(manga, result)

        logcat(LogPriority.INFO) {
            "Refreshed canonical metadata for '${manga.title}'"
        }
        return true
    }

    /**
     * Merges alternative titles from the tracker result into the manga.
     */
    private suspend fun mergeAlternativeTitles(manga: Manga, result: TrackSearch) {
        if (result.alternative_titles.isEmpty() && result.title.isBlank()) return

        val newTitles = buildList {
            if (result.title.isNotBlank()) add(result.title)
            addAll(result.alternative_titles)
        }
        val merged = manga.mergedAlternativeTitles(newTitles) ?: return
        mangaRepository.update(
            MangaUpdate(id = manga.id, alternativeTitles = merged),
        )
    }

    /**
     * Maps common tracker publishing status strings to SManga status constants.
     */
    private fun mapTrackerStatus(publishingStatus: String): Long? {
        return when (publishingStatus.lowercase().trim()) {
            "ongoing", "publishing", "releasing" -> 1L // SManga.ONGOING
            "completed", "finished" -> 2L // SManga.COMPLETED
            "licensed" -> 4L // SManga.LICENSED
            "cancelled", "canceled", "discontinued" -> 5L // SManga.CANCELLED
            "hiatus", "on hiatus" -> 6L // SManga.ON_HIATUS
            else -> null
        }
    }

    companion object {
        /**
         * Reverse mapping from canonical ID prefix to tracker ID.
         */
        private val CANONICAL_PREFIX_TO_TRACKER = AddTracks.TRACKER_CANONICAL_PREFIXES
            .entries.associate { (trackerId, prefix) -> prefix to trackerId }

        /**
         * Parses a canonical ID string (e.g. "mu:12345") into (prefix, remoteId).
         */
        fun parseCanonicalId(canonicalId: String): Pair<String, Long>? {
            val parts = canonicalId.split(":", limit = 2)
            if (parts.size != 2) return null
            val prefix = parts[0].takeIf { it.isNotEmpty() } ?: return null
            val remoteId = parts[1].toLongOrNull() ?: return null
            return prefix to remoteId
        }
    }
}
