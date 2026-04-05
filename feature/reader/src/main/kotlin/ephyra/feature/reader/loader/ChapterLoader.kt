package ephyra.feature.reader.loader

import android.app.Application
import android.content.Context
import ephyra.core.archive.archiveReader
import ephyra.core.archive.epubReader
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.lang.withIOContext
import ephyra.core.common.util.system.DeviceUtil
import ephyra.core.common.util.system.logcat
import ephyra.core.download.DownloadManager
import ephyra.core.download.DownloadProvider
import ephyra.data.cache.ChapterCache
import ephyra.domain.download.service.DownloadPreferences
import ephyra.domain.manga.model.Manga
import ephyra.domain.source.model.StubSource
import ephyra.feature.reader.model.ReaderChapter
import ephyra.i18n.MR
import ephyra.source.local.LocalSource
import ephyra.source.local.io.Format
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * Loader used to retrieve the [PageLoader] for a given chapter.
 */
class ChapterLoader(
    private val context: Application,
    private val downloadManager: DownloadManager,
    private val downloadProvider: DownloadProvider,
    private val manga: Manga,
    private val source: Source,
    private val downloadPreferences: DownloadPreferences,
    private val chapterCache: ChapterCache,
) {

    // Computed once and reused for every chapter loaded in this session.
    private val performanceTier by lazy { DeviceUtil.performanceTier(context) }

    /**
     * Unified page pre-processor shared with [HttpPageLoader] so that both immediate
     * (downloaded / local) and incremental (online) filtering use the same pipeline.
     */
    private val preProcessor by lazy { ReaderPagePreProcessor(downloadPreferences) }

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

                // Run the unified pre-processing pipeline on pages that already have
                // image streams (downloaded / local). Online pages are checked
                // incrementally in HttpPageLoader.internalLoadPage() via the same
                // preProcessor instance.
                preProcessor.processLoadedPages(pages)

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
                context,
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
                chapterCache = chapterCache,
                performanceTier = performanceTier,
                isPreloadOnly = isPreloadOnly,
                preProcessor = preProcessor,
            )

            source is StubSource -> error(context.stringResource(MR.strings.source_not_installed, source.toString()))
            else -> error(context.stringResource(MR.strings.loader_not_implemented_error))
        }
    }
}
