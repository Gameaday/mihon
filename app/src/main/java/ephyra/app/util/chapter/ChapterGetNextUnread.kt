package ephyra.app.util.chapter

import ephyra.domain.chapter.model.applyFilters
import ephyra.app.data.download.DownloadManager
import ephyra.app.ui.manga.ChapterList
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<Chapter>.getNextUnread(manga: Manga, downloadManager: DownloadManager): Chapter? {
    return applyFilters(manga, downloadManager).let { chapters ->
        if (manga.sortDescending()) {
            chapters.findLast { !it.read }
        } else {
            chapters.find { !it.read }
        }
    }
}

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<ChapterList.Item>.getNextUnread(manga: Manga): Chapter? {
    return applyFilters(manga).let { chapters ->
        if (manga.sortDescending()) {
            chapters.findLast { !it.chapter.read }
        } else {
            chapters.find { !it.chapter.read }
        }
    }?.chapter
}
