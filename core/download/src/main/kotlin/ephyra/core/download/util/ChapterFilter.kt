package ephyra.core.download.util

import ephyra.domain.base.BasePreferences
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.chapter.service.getChapterSort
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.model.Manga
import ephyra.domain.manga.model.applyFilter
import ephyra.domain.manga.model.downloadedFilter
import ephyra.source.local.isLocal

/**
 * Applies the view filters to the list of chapters obtained from the database.
 * @return an observable of the list of chapters filtered and sorted.
 */
fun List<Chapter>.applyFilters(
    manga: Manga,
    downloadManager: DownloadManager,
    basePreferences: BasePreferences,
): List<Chapter> {
    val isLocalManga = manga.isLocal()
    val unreadFilter = manga.unreadFilter
    val downloadedFilter = manga.downloadedFilter(basePreferences)
    val bookmarkedFilter = manga.bookmarkedFilter

    return filter { chapter -> applyFilter(unreadFilter) { !chapter.read } }
        .filter { chapter -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
        .filter { chapter ->
            applyFilter(downloadedFilter) {
                val downloaded = downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    chapter.url,
                    manga.title,
                    manga.source,
                )
                downloaded || isLocalManga
            }
        }
        .sortedWith(getChapterSort(manga))
}
