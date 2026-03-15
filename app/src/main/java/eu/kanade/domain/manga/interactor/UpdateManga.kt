package eu.kanade.domain.manga.interactor

import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.model.LockedField
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZonedDateTime

class UpdateManga(
    private val mangaRepository: MangaRepository,
    private val fetchInterval: FetchInterval,
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
        coverCache: CoverCache = Injekt.get(),
        libraryPreferences: LibraryPreferences = Injekt.get(),
        downloadManager: DownloadManager = Injekt.get(),
    ): Boolean {
        val remoteTitle = try {
            remoteManga.title
        } catch (_: UninitializedPropertyAccessException) {
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
            Injekt.get<TrackPreferences>().contentSourcePriorityFields().get()
        } else {
            0L
        }

        // Helper: preserve fields that are locked or where the authority has priority
        // and a non-blank value already exists. When authority metadata is absent or the
        // field prefers the content source, the remote value is used.
        fun preserveIfAuthority(field: Long, existing: String?, remote: String?): String? {
            if (LockedField.isLocked(locked, field)) return null
            val authorityHasPriority = hasAuthorityMetadata &&
                !LockedField.isLocked(contentSourcePriorityFields, field)
            if (authorityHasPriority && !existing.isNullOrBlank()) return null
            return remote
        }

        val title = run {
            if (remoteTitle.isEmpty()) return@run null
            if (LockedField.isLocked(locked, LockedField.TITLE)) return@run null
            val authorityHasPriority = hasAuthorityMetadata &&
                !LockedField.isLocked(contentSourcePriorityFields, LockedField.TITLE)
            if (authorityHasPriority && localManga.title.isNotBlank()) return@run null
            if (localManga.favorite && !libraryPreferences.updateMangaTitles().get()) return@run null
            remoteTitle
        }

        val coverLocked = LockedField.isLocked(locked, LockedField.COVER)
        val coverAuthorityPriority = hasAuthorityMetadata &&
            !LockedField.isLocked(contentSourcePriorityFields, LockedField.COVER) &&
            !localManga.thumbnailUrl.isNullOrBlank()

        val coverLastModified =
            when {
                coverLocked || coverAuthorityPriority -> null
                // Never refresh covers if the url is empty to avoid "losing" existing covers
                remoteManga.thumbnail_url.isNullOrEmpty() -> null
                !manualFetch && localManga.thumbnailUrl == remoteManga.thumbnail_url -> null
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
            coverLocked || coverAuthorityPriority -> null
            else -> remoteManga.thumbnail_url?.takeIf { it.isNotEmpty() }
        }

        val genre = run {
            if (LockedField.isLocked(locked, LockedField.GENRE)) return@run null
            val authorityHasPriority = hasAuthorityMetadata &&
                !LockedField.isLocked(contentSourcePriorityFields, LockedField.GENRE)
            if (authorityHasPriority && !localManga.genre.isNullOrEmpty()) return@run null
            remoteManga.getGenres()
        }

        val status = run {
            if (LockedField.isLocked(locked, LockedField.STATUS)) return@run null
            val authorityHasPriority = hasAuthorityMetadata &&
                !LockedField.isLocked(contentSourcePriorityFields, LockedField.STATUS)
            if (authorityHasPriority && localManga.status != 0L) return@run null
            remoteManga.status.toLong()
        }

        val success = mangaRepository.update(
            MangaUpdate(
                id = localManga.id,
                title = title,
                coverLastModified = coverLastModified,
                author = preserveIfAuthority(LockedField.AUTHOR, localManga.author, remoteManga.author),
                artist = preserveIfAuthority(LockedField.ARTIST, localManga.artist, remoteManga.artist),
                description = preserveIfAuthority(
                    LockedField.DESCRIPTION,
                    localManga.description,
                    remoteManga.description,
                ),
                genre = genre,
                thumbnailUrl = thumbnailUrl,
                status = status,
                updateStrategy = remoteManga.update_strategy,
                initialized = true,
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
