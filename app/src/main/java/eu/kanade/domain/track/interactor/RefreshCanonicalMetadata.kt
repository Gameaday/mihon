package eu.kanade.domain.track.interactor

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.ContentType
import tachiyomi.domain.manga.model.LockedField
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.mergedAlternativeTitles
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * Refreshes authoritative metadata for a manga from its canonical tracker source.
 *
 * Parses the manga's [Manga.canonicalId] (e.g. "mu:12345", "al:21") to identify the
 * tracker, searches by title to retrieve the full [TrackSearch] result with matching
 * remote ID, and updates metadata fields.
 *
 * The canonical source is the designated authority for this manga, so it **overwrites**
 * existing metadata with fresh values by default.  This keeps data (status, description,
 * cover art) up to date as the authoritative source changes over time.
 *
 * **Per-field locking** (Jellyfin-style): Fields whose bit is set in [Manga.lockedFields]
 * are never overwritten, even in overwrite mode.  This lets users protect manual edits
 * (e.g. a custom description) while keeping all other fields current from the authority.
 *
 * Fill-only mode is available via [fillOnly] for the initial matching phase, where
 * multiple authority sources are tried in sequence and earlier sources' data should
 * not be overwritten by later, potentially lower-quality sources.
 */
class RefreshCanonicalMetadata(
    private val mangaRepository: MangaRepository,
    private val trackerManager: TrackerManager,
) {

    /**
     * Refreshes metadata for a single manga from its canonical tracker source.
     *
     * @param manga The manga to refresh. Must have a non-null [Manga.canonicalId].
     * @param fillOnly When true, only fills empty/blank fields (used during initial matching).
     *                 When false (default), overwrites all fields with fresh authority data.
     * @return true if metadata was updated, false if no update was needed or possible.
     */
    suspend fun await(manga: Manga, fillOnly: Boolean = false): Boolean = withIOContext {
        val canonicalId = manga.canonicalId ?: return@withIOContext false

        // Try numeric ID first, then fall back to string ID for trackers like Jellyfin
        val numericParsed = parseCanonicalId(canonicalId)
        val stringParsed = if (numericParsed == null) parseCanonicalIdString(canonicalId) else null
        val prefix = numericParsed?.first ?: stringParsed?.first ?: return@withIOContext false

        val trackerId = CANONICAL_PREFIX_TO_TRACKER[prefix] ?: run {
            logcat(LogPriority.DEBUG) { "Unknown canonical prefix: $prefix" }
            return@withIOContext false
        }

        val tracker = trackerManager.get(trackerId) ?: return@withIOContext false
        if (!tracker.isLoggedIn && trackerId !in AddTracks.TRACKERS_WITH_PUBLIC_SEARCH) {
            return@withIOContext false
        }

        try {
            val result = if (numericParsed != null) {
                findByRemoteId(tracker, manga.title, numericParsed.second)
            } else {
                findByStringRemoteId(tracker, manga.title, stringParsed!!.second)
            } ?: return@withIOContext false
            applyMetadataUpdate(manga, result, fillOnly)
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
     * Looks up the tracker result for a string-based remote ID (e.g. Jellyfin UUID).
     * Uses the tracker's search("id:...") protocol, then falls back to title search.
     */
    private suspend fun findByStringRemoteId(
        tracker: Tracker,
        title: String,
        remoteId: String,
    ): TrackSearch? {
        // Try direct ID lookup
        try {
            val directResults = tracker.search("id:$remoteId")
            val directMatch = directResults.firstOrNull {
                it.tracking_url.contains(remoteId)
            }
            if (directMatch != null) return directMatch
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) {
                "Direct string ID lookup failed for $remoteId, falling back to title search"
            }
        }

        // Fall back to title-based search + find by tracking URL
        val results = tracker.search(title)
        return results.firstOrNull { it.tracking_url.contains(remoteId) }
    }

    /**
     * Applies metadata from the tracker result to the manga.
     *
     * Respects per-field locking (Jellyfin-style): fields whose bit is set in
     * [Manga.lockedFields] are never overwritten, regardless of mode.
     *
     * When [fillOnly] is false (default), overwrites all *unlocked* fields with
     * non-blank authority values — the canonical source is the designated truth.
     * When [fillOnly] is true, only fills empty/blank *unlocked* fields (used
     * during initial matching to avoid overwrite chains).
     */
    private suspend fun applyMetadataUpdate(manga: Manga, result: TrackSearch, fillOnly: Boolean): Boolean {
        val locked = manga.lockedFields

        // Only set title if it would actually change the value
        val title = result.title.takeIf {
            it.isNotBlank() &&
                !LockedField.isLocked(locked, LockedField.TITLE) &&
                (!fillOnly || manga.title.isBlank()) &&
                it != manga.title
        }
        val description = result.summary.takeIf {
            it.isNotBlank() &&
                !LockedField.isLocked(locked, LockedField.DESCRIPTION) &&
                (!fillOnly || manga.description.isNullOrBlank()) &&
                it != manga.description
        }
        val author = result.authors.joinToString(", ").takeIf {
            it.isNotBlank() &&
                !LockedField.isLocked(locked, LockedField.AUTHOR) &&
                (!fillOnly || manga.author.isNullOrBlank()) &&
                it != manga.author
        }
        val artist = result.artists.joinToString(", ").takeIf {
            it.isNotBlank() &&
                !LockedField.isLocked(locked, LockedField.ARTIST) &&
                (!fillOnly || manga.artist.isNullOrBlank()) &&
                it != manga.artist
        }
        val thumbnailUrl = result.cover_url.takeIf {
            it.isNotBlank() &&
                !LockedField.isLocked(locked, LockedField.COVER) &&
                (!fillOnly || manga.thumbnailUrl.isNullOrBlank()) &&
                it != manga.thumbnailUrl
        }
        val status = mapTrackerStatus(result.publishing_status).takeIf {
            it != null &&
                !LockedField.isLocked(locked, LockedField.STATUS) &&
                (!fillOnly || manga.status == 0L) &&
                it != manga.status
        }
        // Infer content type from tracker's publishing_type
        val contentType = ContentType.fromPublishingType(result.publishing_type).takeIf {
            it != ContentType.UNKNOWN &&
                !LockedField.isLocked(locked, LockedField.CONTENT_TYPE) &&
                (!fillOnly || manga.contentType == ContentType.UNKNOWN) &&
                it != manga.contentType
        }
        // Merge genres from authority (if available and not locked)
        val genre = result.genres.takeIf {
            it.isNotEmpty() &&
                !LockedField.isLocked(locked, LockedField.GENRE) &&
                (!fillOnly || manga.genre.isNullOrEmpty())
        }?.let { authorityGenres ->
            // Merge: keep existing genres, add new ones from authority
            val existing = manga.genre.orEmpty().toSet()
            val merged = (existing + authorityGenres).distinct()
            merged.takeIf { it.toSet() != existing }
        }

        val hasChanges = title != null || description != null || author != null || artist != null ||
            thumbnailUrl != null || status != null || contentType != null || genre != null

        if (!hasChanges) {
            // Still merge alt titles even if main metadata didn't change
            mergeAlternativeTitles(manga, result)
            return false
        }

        mangaRepository.update(
            MangaUpdate(
                id = manga.id,
                title = title,
                description = description,
                author = author,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                status = status,
                contentType = contentType,
                genre = genre,
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

        /**
         * Parses a canonical ID string with a non-numeric ID (e.g. "jf:abc123").
         */
        fun parseCanonicalIdString(canonicalId: String): Pair<String, String>? {
            val parts = canonicalId.split(":", limit = 2)
            if (parts.size != 2) return null
            val prefix = parts[0].takeIf { it.isNotEmpty() } ?: return null
            val remoteId = parts[1].takeIf { it.isNotEmpty() } ?: return null
            // Only use this for known non-numeric prefixes
            if (prefix !in CANONICAL_PREFIX_TO_TRACKER) return null
            return prefix to remoteId
        }
    }
}
