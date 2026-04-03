package ephyra.core.download.util

import ephyra.domain.chapter.model.Chapter
import ephyra.domain.download.service.DownloadManager
import ephyra.domain.manga.model.Manga
import ephyra.source.local.isLocal

/**
 * Returns a copy of the list with not-downloaded chapters removed.
 */
fun List<Chapter>.filterDownloaded(manga: Manga, downloadManager: DownloadManager): List<Chapter> {
    if (manga.isLocal()) return this

    return filter {
        downloadManager.isChapterDownloaded(it.name, it.scanlator, it.url, manga.title, manga.source)
    }
}

/**
 * Returns a copy of the list with duplicate chapters removed.
 * Preference order: current chapter → same scanlator → first available.
 */
fun List<Chapter>.removeDuplicates(currentChapter: Chapter): List<Chapter> {
    return groupBy { it.chapterNumber }
        .map { (_, chapters) ->
            chapters.find { it.id == currentChapter.id }
                ?: chapters.find { it.scanlator == currentChapter.scanlator }
                ?: chapters.first()
        }
}
