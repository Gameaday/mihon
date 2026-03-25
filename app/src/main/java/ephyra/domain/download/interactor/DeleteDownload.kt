package ephyra.domain.download.interactor

import ephyra.app.data.download.DownloadManager
import ephyra.core.common.util.lang.withNonCancellableContext
import ephyra.domain.chapter.model.Chapter
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.service.SourceManager

class DeleteDownload(
    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
) {

    suspend fun awaitAll(manga: Manga, vararg chapters: Chapter) = withNonCancellableContext {
        sourceManager.get(manga.source)?.let { source ->
            downloadManager.deleteChapters(chapters.toList(), manga, source)
        }
    }
}
