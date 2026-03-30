package ephyra.core.download.util

import ephyra.domain.base.BasePreferences
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.model.Manga

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<Chapter>.getNextUnread(
    manga: Manga,
    downloadManager: DownloadManager,
    basePreferences: BasePreferences,
): Chapter? {
    return applyFilters(manga, downloadManager, basePreferences).let { chapters ->
        if (manga.sortDescending()) {
            chapters.findLast { !it.read }
        } else {
            chapters.find { !it.read }
        }
    }
}
