package ephyra.data.backup.restore.restorers

import androidx.room.withTransaction
import ephyra.data.backup.models.BackupCategory
import ephyra.data.backup.models.BackupChapter
import ephyra.data.backup.models.BackupHistory
import ephyra.data.backup.models.BackupManga
import ephyra.data.backup.models.BackupTracking
import ephyra.data.room.EphyraDatabase
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.model.toChapterUpdate
import ephyra.domain.chapter.repository.ChapterRepository
import ephyra.domain.history.interactor.UpsertHistory
import ephyra.domain.history.model.HistoryUpdate
import ephyra.domain.history.repository.HistoryRepository
import ephyra.domain.manga.interactor.FetchInterval
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.interactor.GetMangaByUrlAndSourceId
import ephyra.domain.manga.interactor.SetExcludedScanlators
import ephyra.domain.manga.interactor.UpdateManga
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.toMangaUpdate
import ephyra.domain.manga.repository.MangaRepository
import ephyra.domain.track.interactor.GetTracks
import ephyra.domain.track.interactor.InsertTrack
import ephyra.domain.track.model.Track
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.max

class MangaRestorer(
    private val database: EphyraDatabase,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val historyRepository: HistoryRepository,
    private val upsertHistory: UpsertHistory,
    private val getCategories: GetCategories,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val updateManga: UpdateManga,
    private val getTracks: GetTracks,
    private val insertTrack: InsertTrack,
    private val getExcludedScanlators: GetExcludedScanlators,
    private val setExcludedScanlators: SetExcludedScanlators,
    private val fetchInterval: FetchInterval,
) {

    private val now = ZonedDateTime.now()
    private val currentFetchWindow = fetchInterval.getWindow(now)

    suspend fun sortByNew(backupMangas: List<BackupManga>): List<BackupManga> {
        val urlsBySource = mangaRepository.getAllMangaSourceAndUrl()
            .groupBy({ it.first }, { it.second })

        return backupMangas
            .sortedWith(
                compareBy<BackupManga> { it.url in urlsBySource[it.source].orEmpty() }
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    suspend fun restore(
        backupManga: BackupManga,
        backupCategories: List<BackupCategory>,
    ) {
        database.withTransaction {
            val dbManga = findExistingManga(backupManga)
            val manga = backupManga.getMangaImpl()
            val restoredManga = if (dbManga == null) {
                restoreNewManga(manga)
            } else {
                restoreExistingManga(manga, dbManga)
            }

            restoreMangaDetails(
                manga = restoredManga,
                chapters = backupManga.chapters,
                categories = backupManga.categories,
                backupCategories = backupCategories,
                history = backupManga.history,
                tracks = backupManga.tracking,
                excludedScanlators = backupManga.excludedScanlators,
            )
        }
    }

    private suspend fun findExistingManga(backupManga: BackupManga): Manga? {
        return getMangaByUrlAndSourceId.await(backupManga.url, backupManga.source)
    }

    private suspend fun restoreExistingManga(manga: Manga, dbManga: Manga): Manga {
        return if (manga.version > dbManga.version) {
            updateMangaInDb(dbManga.copyFrom(manga).copy(id = dbManga.id))
        } else {
            updateMangaInDb(manga.copyFrom(dbManga).copy(id = dbManga.id))
        }
    }

    private fun Manga.copyFrom(newer: Manga): Manga {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            author = newer.author,
            artist = newer.artist,
            description = newer.description,
            genre = newer.genre,
            thumbnailUrl = newer.thumbnailUrl,
            status = newer.status,
            initialized = this.initialized || newer.initialized,
            version = newer.version,
            metadataSource = newer.metadataSource ?: this.metadataSource,
            metadataUrl = newer.metadataUrl ?: this.metadataUrl,
            alternativeTitles = newer.alternativeTitles.ifEmpty { this.alternativeTitles },
        )
    }

    private suspend fun updateMangaInDb(manga: Manga): Manga {
        updateManga.await(manga.toMangaUpdate())
        return manga
    }

    private suspend fun restoreNewManga(manga: Manga): Manga {
        val inserted = mangaRepository.insertNetworkManga(listOf(manga))
        return inserted.firstOrNull() ?: manga
    }

    private suspend fun restoreChapters(manga: Manga, backupChapters: List<BackupChapter>) {
        val dbChaptersByUrl = getChaptersByMangaId.await(manga.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = backupChapters
            .mapNotNull {
                val chapter = it.toChapterImpl().copy(mangaId = manga.id)

                val dbChapter = dbChaptersByUrl[chapter.url]
                    ?: return@mapNotNull chapter // New chapter

                if (chapter.forComparison() == dbChapter.forComparison()) {
                    return@mapNotNull null // Same state; skip
                }

                var updatedChapter = chapter
                    .copyFrom(dbChapter)
                    .copy(
                        id = dbChapter.id,
                        bookmark = chapter.bookmark || dbChapter.bookmark,
                    )
                if (dbChapter.read && !updatedChapter.read) {
                    updatedChapter = updatedChapter.copy(
                        read = true,
                        lastPageRead = dbChapter.lastPageRead,
                    )
                } else if (updatedChapter.lastPageRead == 0L && dbChapter.lastPageRead != 0L) {
                    updatedChapter = updatedChapter.copy(
                        lastPageRead = dbChapter.lastPageRead,
                    )
                }
                updatedChapter
            }
            .partition { it.id > 0 }

        if (newChapters.isNotEmpty()) {
            chapterRepository.addAll(newChapters)
        }
        if (existingChapters.isNotEmpty()) {
            chapterRepository.updateAll(existingChapters.map { it.toChapterUpdate() })
        }
    }

    private fun Chapter.forComparison() =
        this.copy(id = 0L, mangaId = 0L, dateFetch = 0L, dateUpload = 0L, lastModifiedAt = 0L, version = 0L)

    private suspend fun restoreMangaDetails(
        manga: Manga,
        chapters: List<BackupChapter>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
        excludedScanlators: List<String>,
    ): Manga {
        restoreCategories(manga, categories, backupCategories)
        restoreChapters(manga, chapters)
        restoreTracking(manga, tracks)
        restoreHistory(history)
        restoreExcludedScanlators(manga, excludedScanlators)
        updateManga.awaitUpdateFetchInterval(manga, now, currentFetchWindow)
        return manga
    }

    private suspend fun restoreCategories(
        manga: Manga,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = getCategories.await()
        val dbCategoriesByName = dbCategories.associateBy { it.name }
        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val categoryIds = categories.mapNotNull { backupCategoryOrder ->
            backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                dbCategoriesByName[backupCategory.name]?.id
            }
        }

        if (categoryIds.isNotEmpty()) {
            mangaRepository.setMangaCategories(manga.id, categoryIds)
        }
    }

    private suspend fun restoreHistory(backupHistory: List<BackupHistory>) {
        val updates = backupHistory.mapNotNull { history ->
            val chapter = chapterRepository.getChapterByUrl(history.url)
                ?: return@mapNotNull null // Chapter doesn't exist; skip

            val item = history.getHistoryImpl().copy(chapterId = chapter.id)
            val existing = historyRepository.getHistoryByChapterId(chapter.id)

            if (existing == null) {
                item
            } else {
                item.copy(
                    id = existing.id,
                    readAt = max(item.readAt?.time ?: 0L, existing.readAt?.time ?: 0L)
                        .takeIf { it > 0L }
                        ?.let { Date(it) },
                    readDuration = max(item.readDuration, existing.readDuration) - existing.readDuration,
                )
            }
        }

        updates.forEach { entry ->
            upsertHistory.await(
                HistoryUpdate(
                    chapterId = entry.chapterId,
                    readAt = entry.readAt ?: Date(0),
                    sessionReadDuration = entry.readDuration,
                ),
            )
        }
    }

    private suspend fun restoreTracking(manga: Manga, backupTracks: List<BackupTracking>) {
        val dbTrackByTrackerId = getTracks.await(manga.id).associateBy { it.trackerId }

        val tracksToUpsert = backupTracks
            .mapNotNull {
                val track = it.getTrackImpl()
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: return@mapNotNull track.copy(id = 0, mangaId = manga.id)

                if (track.forComparison() == dbTrack.forComparison()) {
                    return@mapNotNull null // Same state; skip
                }

                dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    lastChapterRead = max(dbTrack.lastChapterRead, track.lastChapterRead),
                )
            }

        if (tracksToUpsert.isNotEmpty()) {
            insertTrack.awaitAll(tracksToUpsert)
        }
    }

    private fun Track.forComparison() = this.copy(id = 0L, mangaId = 0L)

    private suspend fun restoreExcludedScanlators(manga: Manga, excludedScanlators: List<String>) {
        if (excludedScanlators.isEmpty()) return
        val existing = getExcludedScanlators.await(manga.id)
        val combined = existing + excludedScanlators.toSet()
        if (combined != existing) {
            setExcludedScanlators.await(manga.id, combined)
        }
    }
}

