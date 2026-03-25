package ephyra.app.util.chapter

import ephyra.app.data.download.DownloadCache
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga
import ephyra.source.local.isLocal

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Chapter>.filterDownloaded(manga: Manga, downloadCache: DownloadCache): List<Chapter> {
    if (manga.isLocal()) return this

    return filter { downloadCache.isChapterDownloaded(it.name, it.scanlator, it.url, manga.title, manga.source) }
}
