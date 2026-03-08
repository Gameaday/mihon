package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.ContentType
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.mergedAlternativeTitles
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Refreshes authoritative metadata for a manga from its canonical tracker source.
 *
 * Parses the manga's [Manga.canonicalId] (e.g. "mu:12345", "al:21") to identify the
 * tracker, searches by title to retrieve the full [TrackSearch] result with matching
 * remote ID, and fills in any **missing** metadata fields.
 *
 * Like [MatchUnlinkedManga.enrichFromSearchResult], this refresher only fills fields
 * that are currently empty/blank — it never overwrites user or source data.  This
 * reduces unnecessary writes, bandwidth, and prevents "overwrite chains" when multiple
 * authorities are checked in sequence.
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to refresh canonical metadata for '${manga.title}'" }
            false
        }
    }

    /**
     * Looks up the tracker result for a given remote ID.
     * First tries direct ID lookup via the tracker's search("id:...") protocol (0 API search calls).
     * Falls back to searching by title and filtering by remote ID if direct lookup fails.
     */
    private suspend fun findByRemoteId(
        tracker: Tracker,
        title: String,
        remoteId: Long,
    ): TrackSearch? {
        // Try direct ID lookup first (supported by MAL, AniList, MangaUpdates)
        try {
            val directResults = tracker.search("id:$remoteId")
            val directMatch = directResults.firstOrNull { it.remote_id == remoteId }
            if (directMatch != null) return directMatch
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Direct ID lookup failed for $remoteId, falling back to title search" }
        }

        // Fall back to title-based search + filter by remote ID
        val results = tracker.search(title)
        return results.firstOrNull { it.remote_id == remoteId }
    }

    /**
     * Applies metadata from the tracker result to the manga.
     * Only fills fields that are currently empty/blank — never overwrites existing
     * user or source data.  This prevents "overwrite chains" when multiple
     * authorities are consulted in sequence and reduces unnecessary writes.
     */
    private suspend fun applyMetadataUpdate(manga: Manga, result: TrackSearch): Boolean {
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
        val status = mapTrackerStatus(result.publishing_status).takeIf {
            it != null && (manga.status == 0L)
        }
        // Infer content type from tracker's publishing_type
        val contentType = ContentType.fromPublishingType(result.publishing_type).takeIf {
            it != ContentType.UNKNOWN && manga.contentType == ContentType.UNKNOWN
        }

        val hasChanges = description != null || author != null || artist != null ||
            thumbnailUrl != null || status != null || contentType != null

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
                contentType = contentType,
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
            val remoteId = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
            return prefix to remoteId
        }
    }
}
