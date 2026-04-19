package ephyra.domain.manga.interactor

import ephyra.core.common.util.system.logcat
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.library.service.LibraryPreferences
import ephyra.domain.manga.model.LockedField
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.MangaUpdate
import ephyra.domain.manga.model.hasCustomCover
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.manga.service.CoverCache
import ephyra.domain.track.service.TrackPreferences
import ephyra.source.local.isLocal
import eu.kanade.tachiyomi.source.model.SManga
import logcat.LogPriority
import java.time.Instant
import java.time.ZonedDateTime

class UpdateManga(
    private val mangaRepository: MangaRepository,
    private val fetchInterval: FetchInterval,
    private val coverCache: CoverCache,
    private val libraryPreferences: LibraryPreferences,
    private val downloadManager: DownloadManager,
    private val trackPreferences: TrackPreferences,
) {

    suspend fun await(mangaUpdate: MangaUpdate): Boolean {
        return mangaRepository.update(mangaUpdate)
    }

    suspend fun awaitAll(mangaUpdates: List<MangaUpdate>): Boolean {
        return mangaRepository.updateAll(mangaUpdates)
    }

    suspend fun awaitUpdateFromSource(
        localManga: Manga,
        remoteManga: SManga,
        manualFetch: Boolean,
    ): Boolean {
        val remoteTitle = try {
            remoteManga.title
        } catch (e: UninitializedPropertyAccessException) {
            logcat(LogPriority.DEBUG, e) { "Source returned SManga with uninitialized title; using empty string" }
            ""
        }

        // When a manga has a canonical ID (linked to an authoritative tracker), its metadata
        // was enriched from the tracker's verified data. Preserve that authoritative metadata
        // during content source updates to prevent flickering between content-source and
        // authority values on pull-to-refresh. The authority refresh runs separately and
        // handles updating authority-owned fields.
        val hasAuthorityMetadata = localManga.canonicalId != null
        val locked = localManga.lockedFields
        // Per-field content source priority: when a field's bit is set, the content source
        // value takes precedence over the authority source.
        val contentSourcePriorityFields = if (hasAuthorityMetadata) {
            trackPreferences.contentSourcePriorityFields().get()
        } else {
            0L
        }

        // Helper: should a field be skipped? True when the field is locked, or when the
        // authority has priority and the local value is already populated.
        fun shouldPreserve(field: Long, existingIsPopulated: Boolean): Boolean {
            if (LockedField.isLocked(locked, field)) return true
            if (hasAuthorityMetadata &&
                !LockedField.isLocked(contentSourcePriorityFields, field) &&
                existingIsPopulated
            ) {
                return true
            }
            return false
        }

        val title = run {
            if (remoteTitle.isEmpty()) return@run null
            if (shouldPreserve(LockedField.TITLE, localManga.title.isNotBlank())) return@run null
            if (localManga.favorite && !libraryPreferences.updateMangaTitles().get()) return@run null
            if (remoteTitle == localManga.title) return@run null
            remoteTitle
        }

        val coverPreserved = shouldPreserve(LockedField.COVER, !localManga.thumbnailUrl.isNullOrBlank())

        val coverLastModified = when {
            coverPreserved -> null
            remoteManga.thumbnail_url.isNullOrEmpty() -> null
            localManga.thumbnailUrl == remoteManga.thumbnail_url -> null
            localManga.isLocal() -> Instant.now().toEpochMilli()
            localManga.hasCustomCover(coverCache) -> {
                coverCache.deleteFromCache(localManga, false)
                null
            }

            else -> {
                coverCache.deleteFromCache(localManga, false)
                Instant.now().toEpochMilli()
            }
        }

        val thumbnailUrl = when {
            coverPreserved -> null
            remoteManga.thumbnail_url == localManga.thumbnailUrl -> null
            else -> remoteManga.thumbnail_url?.takeIf { it.isNotEmpty() }
        }

        val author = when {
            shouldPreserve(LockedField.AUTHOR, !localManga.author.isNullOrBlank()) -> null
            remoteManga.author == localManga.author -> null
            else -> remoteManga.author
        }
        val artist = when {
            shouldPreserve(LockedField.ARTIST, !localManga.artist.isNullOrBlank()) -> null
            remoteManga.artist == localManga.artist -> null
            else -> remoteManga.artist
        }
        val description = when {
            shouldPreserve(LockedField.DESCRIPTION, !localManga.description.isNullOrBlank()) -> null
            remoteManga.description == localManga.description -> null
            else -> remoteManga.description
        }
        val remoteGenres = remoteManga.getGenres()
        val genre = when {
            shouldPreserve(LockedField.GENRE, !localManga.genre.isNullOrEmpty()) -> null
            remoteGenres == localManga.genre -> null
            else -> remoteGenres
        }
        val remoteStatus = remoteManga.status.toLong()
        val status = when {
            shouldPreserve(LockedField.STATUS, localManga.status != 0L) -> null
            remoteStatus == localManga.status -> null
            else -> remoteStatus
        }
        val updateStrategy = remoteManga.update_strategy.takeIf { it != localManga.updateStrategy }
        val initialized = true.takeIf { !localManga.initialized }

        val hasChanges = title != null || coverLastModified != null || thumbnailUrl != null ||
            author != null || artist != null || description != null || genre != null ||
            status != null || updateStrategy != null || initialized != null

        if (!hasChanges) return true

        val success = mangaRepository.update(
            MangaUpdate(
                id = localManga.id,
                title = title,
                coverLastModified = coverLastModified,
                author = author,
                artist = artist,
                description = description,
                genre = genre,
                thumbnailUrl = thumbnailUrl,
                status = status,
                updateStrategy = updateStrategy,
                initialized = initialized,
            ),
        )
        if (success && title != null) {
            downloadManager.renameManga(localManga, title)
        }
        return success
    }

    suspend fun awaitUpdateFetchInterval(
        manga: Manga,
        dateTime: ZonedDateTime = ZonedDateTime.now(),
        window: Pair<Long, Long> = fetchInterval.getWindow(dateTime),
    ): Boolean {
        return mangaRepository.update(
            fetchInterval.toMangaUpdate(manga, dateTime, window),
        )
    }

    suspend fun awaitUpdateLastUpdate(mangaId: Long): Boolean {
        return mangaRepository.update(MangaUpdate(id = mangaId, lastUpdate = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateCoverLastModified(mangaId: Long): Boolean {
        return mangaRepository.update(MangaUpdate(id = mangaId, coverLastModified = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateFavorite(mangaId: Long, favorite: Boolean): Boolean {
        val dateAdded = when (favorite) {
            true -> Instant.now().toEpochMilli()
            false -> 0
        }
        return mangaRepository.update(
            MangaUpdate(id = mangaId, favorite = favorite, dateAdded = dateAdded),
        )
    }
}
