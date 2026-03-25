package ephyra.app.util.chapter

import ephyra.app.data.download.DownloadCache
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga
import ephyra.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Chapter>.filterDownloaded(manga: Manga): List<Chapter> {
    if (manga.isLocal()) return this

    val downloadCache: DownloadCache = Injekt.get()

    return filter { downloadCache.isChapterDownloaded(it.name, it.scanlator, it.url, manga.title, manga.source) }
}
