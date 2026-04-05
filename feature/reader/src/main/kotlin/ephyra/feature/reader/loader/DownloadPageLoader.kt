package ephyra.feature.reader.loader

import android.app.Application
import android.net.Uri
import com.hippo.unifile.UniFile
import ephyra.core.archive.archiveReader
import ephyra.core.download.DownloadManager
import ephyra.core.download.DownloadProvider
import ephyra.data.database.models.toDomainChapter
import ephyra.domain.manga.model.Manga
import ephyra.feature.reader.model.ReaderChapter
import ephyra.feature.reader.model.ReaderPage
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page

internal class DownloadPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: Source,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val context: Application,
) : PageLoader() {

    @Volatile
    private var archivePageLoader: ArchivePageLoader? = null

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        check(!isRecycled)
        val dbChapter = chapter.chapter
        val chapterPath = downloadProvider.findChapterDir(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            manga.title,
            source,
        )
        return if (chapterPath?.isFile == true) {
            getPagesFromArchive(chapterPath)
        } else {
            getPagesFromDirectory()
        }
    }

    override fun recycle() {
        super.recycle()
        archivePageLoader?.recycle()
    }

    private suspend fun getPagesFromArchive(file: UniFile): List<ReaderPage> {
        val loader = ArchivePageLoader(file.archiveReader(context)).also { archivePageLoader = it }
        return loader.getPages()
    }

    private fun getPagesFromDirectory(): List<ReaderPage> {
        val pages = downloadManager.buildPageList(
            source,
            manga,
            requireNotNull(chapter.chapter.toDomainChapter()) {
                "Chapter has no database ID"
            },
        )
        return pages.map { page ->
            ReaderPage(page.index, page.url, page.imageUrl) {
                requireNotNull(context.contentResolver.openInputStream(page.uri ?: Uri.EMPTY)) {
                    "Could not open input stream for page URI: ${page.uri}"
                }
            }.apply {
                status = Page.State.Ready
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
        archivePageLoader?.loadPage(page)
    }
}
