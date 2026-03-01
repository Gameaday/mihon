package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.util.system.DeviceUtil
import mihon.core.archive.archiveReader
import mihon.core.archive.epubReader
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.io.Format

/**
 * Loader used to retrieve the [PageLoader] for a given chapter.
 */
class ChapterLoader(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val manga: Manga,
    private val source: Source,
) {

    // Computed once and reused for every chapter loaded in this session.
    private val performanceTier by lazy { DeviceUtil.performanceTier(context) }

    /**
     * Assigns the chapter's page loader and loads the its pages. Returns immediately if the chapter
     * is already loaded.
     *
     * @param chapter the chapter to load.
     * @param isPreloadOnly when `true` the chapter is being preloaded speculatively before the
     *   reader actually navigates to it. The underlying [HttpPageLoader] will be created with a
     *   single background worker so it cannot compete with the active chapter's downloads for
     *   network bandwidth.
     */
    suspend fun loadChapter(chapter: ReaderChapter, isPreloadOnly: Boolean = false) {
        if (chapterIsReady(chapter)) {
            // The chapter's page list is already available. If it was previously loaded by a
            // preload-only (single-worker) HttpPageLoader and is now being activated as the
            // current reading chapter, promote it to the full worker count so downloads are no
            // longer throttled by the background-preload bandwidth cap.
            if (!isPreloadOnly) {
                chapter.pageLoader?.promoteToActive()
            }
            return
        }

        chapter.state = ReaderChapter.State.Loading
        withIOContext {
            logcat { "Loading pages for ${chapter.chapter.name}" }
            try {
                val loader = getPageLoader(chapter, isPreloadOnly)
                chapter.pageLoader = loader

                val pages = loader.getPages()
                    .onEach { it.chapter = chapter }

                if (pages.isEmpty()) {
                    throw Exception(context.stringResource(MR.strings.page_list_empty_error))
                }

                // If the chapter is partially read, set the starting page to the last the user read
                // otherwise use the requested page.
                if (!chapter.chapter.read) {
                    chapter.requestedPage = chapter.chapter.last_page_read
                }

                chapter.state = ReaderChapter.State.Loaded(pages)
            } catch (e: Throwable) {
                chapter.state = ReaderChapter.State.Error(e)
                throw e
            }
        }
    }

    /**
     * Checks [chapter] to be loaded based on present pages and loader in addition to state.
     */
    private fun chapterIsReady(chapter: ReaderChapter): Boolean {
        return chapter.state is ReaderChapter.State.Loaded && chapter.pageLoader != null
    }

    /**
     * Returns the page loader to use for this [chapter].
     */
    private suspend fun getPageLoader(chapter: ReaderChapter, isPreloadOnly: Boolean = false): PageLoader {
        // Ensure the DownloadCache has finished reading its on-disk snapshot before we query
        // it. On the fast path (every call after the first disk-cache load) this is a single
        // deferred check with no suspension. On a cold start where the reader is opened before
        // the disk-cache read completes, we suspend here briefly rather than querying an empty
        // map and incorrectly routing to HttpPageLoader for a downloaded chapter.
        downloadManager.awaitCacheReady()
        val dbChapter = chapter.chapter
        val isDownloaded = downloadManager.isChapterDownloaded(
            dbChapter.name,
            dbChapter.scanlator,
            dbChapter.url,
            manga.title,
            manga.source,
        )
        return when {
            isDownloaded -> DownloadPageLoader(
                chapter,
                manga,
                source,
                downloadManager,
                downloadProvider,
            )
            source is LocalSource -> source.getFormat(chapter.chapter).let { format ->
                when (format) {
                    is Format.Directory -> DirectoryPageLoader(format.file)
                    is Format.Archive -> ArchivePageLoader(format.file.archiveReader(context))
                    is Format.Epub -> EpubPageLoader(format.file.epubReader(context))
                }
            }
            source is HttpSource -> HttpPageLoader(
                chapter,
                source,
                performanceTier = performanceTier,
                isPreloadOnly = isPreloadOnly,
            )
            source is StubSource -> error(context.stringResource(MR.strings.source_not_installed, source.toString()))
            else -> error(context.stringResource(MR.strings.loader_not_implemented_error))
        }
    }
}
