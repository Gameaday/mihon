package ephyra.data.backup.create.creators

import ephyra.data.backup.create.BackupOptions
import ephyra.data.backup.models.BackupChapter
import ephyra.data.backup.models.BackupHistory
import ephyra.data.backup.models.BackupManga
import ephyra.domain.backup.model.toBackupChapter
import ephyra.domain.backup.model.toBackupTracking
import ephyra.domain.category.interactor.GetCategories
import ephyra.domain.chapter.interactor.GetChaptersByMangaId
import ephyra.domain.history.interactor.GetHistory
import ephyra.domain.manga.interactor.GetExcludedScanlators
import ephyra.domain.manga.model.Manga
import ephyra.domain.reader.model.ReadingMode
import ephyra.domain.track.interactor.GetTracks

class MangaBackupCreator(
    private val getCategories: GetCategories,
    private val getHistory: GetHistory,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getTracks: GetTracks,
    private val getExcludedScanlators: GetExcludedScanlators,
) {

    suspend operator fun invoke(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        return mangas.map {
            backupManga(it, options)
        }
    }

    private suspend fun backupManga(manga: Manga, options: BackupOptions): BackupManga {
        val mangaObject = manga.toBackupManga()

        mangaObject.excludedScanlators = getExcludedScanlators.await(manga.id).toList()

        if (options.chapters) {
            getChaptersByMangaId.await(manga.id, applyScanlatorFilter = false)
                .map { it.toBackupChapter() }
                .takeUnless(List<BackupChapter>::isEmpty)
                ?.let { mangaObject.chapters = it }
        }

        if (options.categories) {
            val categoriesForManga = getCategories.await(manga.id)
            if (categoriesForManga.isNotEmpty()) {
                mangaObject.categories = categoriesForManga.map { it.order }
            }
        }

        if (options.tracking) {
            val tracks = getTracks.await(manga.id).map { it.toBackupTracking() }
            if (tracks.isNotEmpty()) {
                mangaObject.tracking = tracks
            }
        }

        if (options.history) {
            val historyByMangaId = getHistory.await(manga.id)
            if (historyByMangaId.isNotEmpty()) {
                val chaptersById = getChaptersByMangaId.await(manga.id, applyScanlatorFilter = false)
                    .associateBy { it.id }
                val history = historyByMangaId.mapNotNull { history ->
                    chaptersById[history.chapterId]?.let { chapter ->
                        BackupHistory(chapter.url, history.readAt?.time ?: 0L, history.readDuration)
                    }
                }
                if (history.isNotEmpty()) {
                    mangaObject.history = history
                }
            }
        }

        return mangaObject
    }
}

private fun Manga.toBackupManga() =
    BackupManga(
        url = this.url,
        title = this.title,
        artist = this.artist,
        author = this.author,
        description = this.description,
        genre = this.genre.orEmpty(),
        status = this.status.toInt(),
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewer = (this.viewerFlags.toInt() and ReadingMode.MASK),
        viewer_flags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        notes = this.notes,
        initialized = this.initialized,
        metadataSource = this.metadataSource,
        metadataUrl = this.metadataUrl,
        alternativeTitles = this.alternativeTitles,
        canonicalId = this.canonicalId,
        sourceStatus = this.sourceStatus,
        deadSince = this.deadSince,
        contentType = this.contentType.value,
        lockedFields = this.lockedFields,
    )
