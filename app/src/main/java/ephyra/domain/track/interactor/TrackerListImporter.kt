package ephyra.domain.track.interactor

import ephyra.app.data.track.TrackerManager
import ephyra.app.data.track.model.TrackSearch
import ephyra.app.data.track.myanimelist.MyAnimeList
import ephyra.app.data.track.myanimelist.dto.MALListItemStatus
import kotlinx.coroutines.yield
import logcat.LogPriority
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.logcat
import ephyra.domain.chapter.interactor.GenerateAuthorityChapters
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.track.interactor.InsertTrack

/**
 * Imports a user's reading list from a tracker (currently MAL) into the local library.
 *
 * For each entry on the tracker's list:
 * 1. Creates a manga entry (source = AUTHORITY_SOURCE_ID, no content source)
 * 2. Adds to library (favorite = true)
 * 3. Binds the tracker with canonical ID
 * 4. Generates authority chapters from tracker metadata so users can mark progress
 *
 * This enables "The Existing Reader" user story — users with established tracker accounts
 * can populate their library immediately without browsing sources.
 */
class TrackerListImporter(
    private val mangaRepository: MangaRepository,
    private val insertTrack: InsertTrack,
    private val trackerManager: TrackerManager,
    private val generateAuthorityChapters: GenerateAuthorityChapters,
) {

    /**
     * Imports manga from the user's MAL reading list.
     *
     * @return ImportResult with counts of imported, skipped, and failed entries
     */
    suspend fun importFromMal(): ImportResult = withIOContext {
        val mal = trackerManager.myAnimeList
        if (!mal.isLoggedIn) {
            return@withIOContext ImportResult(error = "Not logged in to MyAnimeList")
        }

        try {
            val listItems = mal.getUserFullList()
            logcat(LogPriority.INFO) { "MAL import: fetched ${listItems.size} items from reading list" }

            var imported = 0
            var skipped = 0
            var failed = 0

            for ((trackSearch, listStatus) in listItems) {
                try {
                    val result = importSingleEntry(mal, trackSearch, listStatus)
                    if (result) imported++ else skipped++
                } catch (e: Exception) {
                    failed++
                    logcat(LogPriority.WARN, e) { "MAL import: failed to import '${trackSearch.title}'" }
                }
                // Yield to allow cancellation and keep the app responsive during large imports
                yield()
            }

            logcat(LogPriority.INFO) {
                "MAL import complete: $imported imported, $skipped skipped, $failed failed"
            }
            ImportResult(imported = imported, skipped = skipped, failed = failed)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "MAL import: failed to fetch reading list" }
            ImportResult(error = e.message ?: "Failed to fetch reading list")
        }
    }

    private suspend fun importSingleEntry(
        mal: MyAnimeList,
        trackSearch: TrackSearch,
        listStatus: MALListItemStatus?,
    ): Boolean {
        val canonicalId = "mal:${trackSearch.remote_id}"

        // Skip if already in library with this canonical ID
        val existingByCanonical = mangaRepository.getFavoritesByCanonicalId(canonicalId, -1L)
        if (existingByCanonical.isNotEmpty()) {
            logcat(LogPriority.DEBUG) { "MAL import: skipping '${trackSearch.title}' — already in library" }
            return false
        }

        // Also check by URL+source to avoid duplicates for unfavorited entries
        val existingByUrl = mangaRepository.getMangaByUrlAndSourceId(canonicalId, AUTHORITY_SOURCE_ID)
        if (existingByUrl?.favorite == true) {
            logcat(LogPriority.DEBUG) { "MAL import: skipping '${trackSearch.title}' — already exists" }
            return false
        }

        // Create or get existing manga entry
        val manga = if (existingByUrl != null) {
            existingByUrl
        } else {
            val newManga = Manga.create().copy(
                url = canonicalId,
                title = trackSearch.title,
                source = AUTHORITY_SOURCE_ID,
                thumbnailUrl = trackSearch.cover_url.ifBlank { null },
                artist = trackSearch.artists.joinToString(", ").ifBlank { null },
                author = trackSearch.authors.joinToString(", ").ifBlank { null },
                description = trackSearch.summary.ifBlank { null },
                initialized = true,
            )
            val inserted = mangaRepository.insertNetworkManga(listOf(newManga))
            inserted.firstOrNull() ?: return false
        }

        // Add to library
        mangaRepository.update(
            MangaUpdate(
                id = manga.id,
                favorite = true,
                dateAdded = System.currentTimeMillis(),
                canonicalId = canonicalId,
            ),
        )

        // Create tracker binding
        val track = ephyra.domain.track.model.Track(
            id = 0,
            mangaId = manga.id,
            trackerId = mal.id,
            remoteId = trackSearch.remote_id,
            libraryId = null,
            title = trackSearch.title,
            lastChapterRead = listStatus?.numChaptersRead ?: 0.0,
            totalChapters = trackSearch.total_chapters,
            status = listStatus?.let { getTrackStatus(it.status) } ?: MyAnimeList.PLAN_TO_READ,
            score = listStatus?.score?.toDouble() ?: 0.0,
            remoteUrl = "https://myanimelist.net/manga/${trackSearch.remote_id}",
            startDate = 0L,
            finishDate = 0L,
            private = false,
        )
        insertTrack.await(track)

        // Generate authority chapters from tracker metadata
        val totalChapters = trackSearch.total_chapters.toInt()
        val lastChapterRead = listStatus?.numChaptersRead?.toInt() ?: 0
        if (totalChapters > 0) {
            val generated = generateAuthorityChapters.await(manga.id, totalChapters, lastChapterRead)
            logcat(LogPriority.DEBUG) {
                "MAL import: generated $generated authority chapters for '${trackSearch.title}'"
            }
        }

        logcat(LogPriority.INFO) { "MAL import: imported '${trackSearch.title}' ($canonicalId)" }
        return true
    }

    private fun getTrackStatus(malStatus: String): Long = when (malStatus) {
        "reading" -> MyAnimeList.READING
        "completed" -> MyAnimeList.COMPLETED
        "on_hold" -> MyAnimeList.ON_HOLD
        "dropped" -> MyAnimeList.DROPPED
        "plan_to_read" -> MyAnimeList.PLAN_TO_READ
        else -> MyAnimeList.PLAN_TO_READ
    }

    data class ImportResult(
        val imported: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val error: String? = null,
    ) {
        val isSuccess get() = error == null
        val total get() = imported + skipped + failed
    }

    companion object {
        /**
         * Source ID for authority-only manga (no content source).
         * These manga exist solely for tracking via external services.
         */
        const val AUTHORITY_SOURCE_ID = -1L
    }
}
