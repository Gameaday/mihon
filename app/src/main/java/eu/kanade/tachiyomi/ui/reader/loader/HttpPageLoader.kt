package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.system.DeviceUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.PriorityBlockingQueue
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.min

/**
 * Loader used to load chapters from an online source.
 */
internal class HttpPageLoader(
    private val chapter: ReaderChapter,
    private val source: HttpSource,
    private val chapterCache: ChapterCache = Injekt.get(),
    /**
     * Device performance tier used to scale preload window sizes and worker concurrency.
     * Defaults to [DeviceUtil.PerformanceTier.MEDIUM] so that the loader is safe to instantiate
     * in tests or other contexts where a [Context] is unavailable. Production callers (i.e.
     * [eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader]) always supply the real tier.
     */
    performanceTier: DeviceUtil.PerformanceTier = DeviceUtil.PerformanceTier.MEDIUM,
    /**
     * When `true` the loader is being used to preload a chapter that is not yet the active
     * reading chapter. In this mode only a single background worker is spawned, regardless of
     * the device performance tier, so the preload never competes with the active chapter's
     * downloads for network bandwidth. The worker count is raised to the full tier value the
     * moment the chapter becomes active (i.e. a new [HttpPageLoader] is created for it via the
     * active-chapter path in [ChapterLoader]).
     */
    isPreloadOnly: Boolean = false,
) : PageLoader() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A queue used to manage requests one by one while allowing priorities.
     */
    private val queue = PriorityBlockingQueue<PriorityPage>()

    /**
     * Number of pages ahead of the current page to preload — scaled by device capability.
     */
    private val preloadSize = when (performanceTier) {
        DeviceUtil.PerformanceTier.LOW -> 2
        DeviceUtil.PerformanceTier.MEDIUM -> 4
        DeviceUtil.PerformanceTier.HIGH -> 6
    }

    /**
     * Number of pages behind the current page to preload — scaled by device capability.
     */
    private val preloadBackwardSize = when (performanceTier) {
        DeviceUtil.PerformanceTier.LOW -> 1
        DeviceUtil.PerformanceTier.MEDIUM -> 2
        DeviceUtil.PerformanceTier.HIGH -> 3
    }

    /**
     * Number of concurrent page-download workers.
     *
     * For preload-only loaders a single worker is always used so that background chapter
     * prefetch never steals bandwidth from the active chapter. The full worker count (scaled
     * by device tier) is only used once the chapter becomes the active reading chapter.
     */
    private val fullWorkerCount = when (performanceTier) {
        DeviceUtil.PerformanceTier.LOW -> 1
        DeviceUtil.PerformanceTier.MEDIUM -> 2
        DeviceUtil.PerformanceTier.HIGH -> 3
    }

    /** Guards [promoteToActive] so the promotion is applied at most once. */
    @OptIn(ExperimentalAtomicApi::class)
    private val promoted = AtomicBoolean(!isPreloadOnly)

    init {
        val initialWorkers = if (isPreloadOnly) 1 else fullWorkerCount
        repeat(initialWorkers) { launchWorker() }
    }

    /**
     * Promotes this loader from preload-only (1 worker) to the full [fullWorkerCount] for the
     * active reading chapter. Idempotent — subsequent calls after the first are no-ops.
     *
     * The formula `fullWorkerCount - 1` is correct because preload-only loaders always start
     * with exactly 1 worker ([init] uses `initialWorkers = 1` when [isPreloadOnly] is true),
     * and [promoted] starts as `!isPreloadOnly`, so this branch is only reached when
     * [isPreloadOnly] was true and exactly 1 worker is already running.
     */
    @OptIn(ExperimentalAtomicApi::class)
    override fun promoteToActive() {
        if (isRecycled) return
        if (!promoted.compareAndSet(expectedValue = false, newValue = true)) return
        // Preload-only loaders always start with 1 worker; launch the remaining workers up to
        // the tier-scaled maximum.
        val remaining = fullWorkerCount - 1
        repeat(remaining) { launchWorker() }
    }

    private fun launchWorker() {
        scope.launchIO {
            flow {
                while (true) {
                    emit(runInterruptible { queue.take() }.page)
                }
            }
                .filter { it.status == Page.State.Queue }
                .collect(::internalLoadPage)
        }
    }

    override var isLocal: Boolean = false

    /**
     * Returns the page list for a chapter. It tries to return the page list from the local cache,
     * otherwise fallbacks to network.
     */
    override suspend fun getPages(): List<ReaderPage> {
        check(!isRecycled)
        val pages = try {
            chapterCache.getPageListFromCache(
                requireNotNull(chapter.chapter.toDomainChapter()) {
                    "Chapter has no database ID"
                },
            )
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            source.getPageList(chapter.chapter)
        }
        return pages.mapIndexed { index, page ->
            // Don't trust sources and use our own indexing
            ReaderPage(index, page.url, page.imageUrl)
        }
    }

    /**
     * Loads a page through the queue. Handles re-enqueueing pages if they were evicted from the cache.
     */
    override suspend fun loadPage(page: ReaderPage) = withIOContext {
        check(!isRecycled)
        val imageUrl = page.imageUrl

        // Check if the image has been deleted
        if (page.status == Page.State.Ready && imageUrl != null && !chapterCache.isImageInCache(imageUrl)) {
            page.status = Page.State.Queue
        }

        // Automatically retry failed pages when subscribed to this page
        if (page.status is Page.State.Error) {
            page.status = Page.State.Queue
        }

        val queuedPages = mutableListOf<PriorityPage>()
        if (page.status == Page.State.Queue) {
            queuedPages += PriorityPage(page, 1).also { queue.offer(it) }
        }
        queuedPages += preloadNextPages(page, preloadSize)
        queuedPages += preloadPrevPages(page, preloadBackwardSize)

        suspendCancellableCoroutine<Nothing> { continuation ->
            continuation.invokeOnCancellation {
                queuedPages.forEach {
                    if (it.page.status == Page.State.Queue) {
                        queue.remove(it)
                    }
                }
            }
        }
    }

    /**
     * Retries a page. This method is only called from user interaction on the viewer.
     */
    override fun retryPage(page: ReaderPage) {
        check(!isRecycled)
        if (page.status is Page.State.Error) {
            page.status = Page.State.Queue
        }
        queue.offer(PriorityPage(page, 2))
    }

    override fun recycle() {
        super.recycle()
        // Cancel all in-flight download coroutines. Any page whose download is interrupted here
        // will have its disk-cache editor aborted (see ChapterCache.fetchAndCacheImage), so no
        // partial data is committed. The page status may be left in a transient state
        // (LoadPage/DownloadImage); we reset it below so external observers never see a ghost
        // "downloading" indicator for a cancelled operation.
        scope.cancel()
        queue.clear()

        // Reset pages stuck in transient states so that:
        //  • any viewer holding a reference to the old ReaderPage sees a retryable state, and
        //  • if this chapter is re-entered later, new ReaderPage objects start with Queue status
        //    (they are always created fresh in getPages(), but defensive reset costs nothing).
        val pages = chapter.pages
        if (pages != null) {
            var cancelledCount = 0
            for (page in pages) {
                val status = page.status
                if (status == Page.State.LoadPage || status == Page.State.DownloadImage) {
                    page.status = Page.State.Queue
                    cancelledCount++
                }
            }
            if (cancelledCount > 0) {
                logcat(LogPriority.DEBUG) {
                    "Recycled ${chapter.chapter.name}: cancelled $cancelledCount in-flight download(s)"
                }
            }

            // Release stream lambdas so the captured imageUrl strings and file references can be GC'd
            pages.forEach { it.stream = null }

            // Cache current page list progress for online chapters to allow a faster reopen
            launchIO {
                try {
                    // Convert to pages without reader information
                    val pagesToSave = pages.map { Page(it.index, it.url, it.imageUrl) }
                    chapterCache.putPageListToCache(
                        requireNotNull(chapter.chapter.toDomainChapter()) {
                            "Chapter has no database ID"
                        },
                        pagesToSave,
                    )
                } catch (e: Throwable) {
                    if (e is CancellationException) {
                        throw e
                    }
                }
            }
        }
    }

    /**
     * Preloads the given [amount] of pages after the [currentPage] with a lower priority.
     *
     * @return a list of [PriorityPage] that were added to the [queue]
     */
    private fun preloadNextPages(currentPage: ReaderPage, amount: Int): List<PriorityPage> {
        val pageIndex = currentPage.index
        val pages = currentPage.chapter.pages ?: return emptyList()
        if (pageIndex == pages.lastIndex) return emptyList()

        return pages
            .subList(pageIndex + 1, min(pageIndex + 1 + amount, pages.size))
            .mapNotNull {
                if (it.status == Page.State.Queue) {
                    PriorityPage(it, 0).apply { queue.offer(this) }
                } else {
                    null
                }
            }
    }

    /**
     * Preloads the given [amount] of pages before the [currentPage] with a lower priority.
     * This avoids stutter when the user navigates backward through a chapter.
     *
     * @param currentPage the page the user is currently viewing.
     * @param amount the number of pages before [currentPage] to preload.
     * @return a list of [PriorityPage] that were added to the [queue]
     */
    private fun preloadPrevPages(currentPage: ReaderPage, amount: Int): List<PriorityPage> {
        val pageIndex = currentPage.index
        if (pageIndex == 0) return emptyList()
        val pages = currentPage.chapter.pages ?: return emptyList()

        return pages
            .subList(maxOf(0, pageIndex - amount), pageIndex)
            .mapNotNull {
                if (it.status == Page.State.Queue) {
                    PriorityPage(it, 0).apply { queue.offer(this) }
                } else {
                    null
                }
            }
    }

    /**
     * Proactively starts loading the first [amount] pages of this chapter at background priority.
     * Called after the page list has been fetched so that images begin downloading before the user
     * actually scrolls to this chapter, reducing wait time at chapter boundaries.
     */
    override fun preloadFirstPages(amount: Int) {
        if (isRecycled) return
        val pages = chapter.pages?.take(amount) ?: return
        pages.forEach { page ->
            if (page.status == Page.State.Queue) {
                queue.offer(PriorityPage(page, 0))
            }
        }
    }

    /**
     * Queues every page in this chapter at the lowest background priority so that the
     * smart-combine pre-scan can process the entire chapter without waiting for the user to
     * navigate to each page. Pages are enqueued at priority [BACKGROUND_PRELOAD_PRIORITY]
     * (below the nearby-page preload priority of 0), so they never compete for bandwidth with
     * the page the user is actively reading or about to read.
     *
     * Only pages in [Page.State.Queue] are enqueued. Pages already downloading, ready, or in
     * an error state are intentionally skipped: pages in progress or already cached need no
     * action, and errored pages are retried through the user-facing [retryPage] path rather
     * than being silently re-queued here.
     */
    override fun preloadAllPages() {
        if (isRecycled) return
        val pages = chapter.pages ?: return
        pages.forEach { page ->
            if (page.status == Page.State.Queue) {
                queue.offer(PriorityPage(page, BACKGROUND_PRELOAD_PRIORITY))
            }
        }
    }

    /**
     * Loads the page, retrieving the image URL and downloading the image if necessary.
     * Automatically retries on transient network errors (IO errors, HTTP 429 and 5xx) up to
     * [MAX_PAGE_LOAD_RETRIES] times with exponential backoff before marking the page as failed.
     * Downloaded images are stored in the chapter cache.
     *
     * @param page the page whose source image has to be downloaded.
     */
    private suspend fun internalLoadPage(page: ReaderPage) {
        var retries = 0
        while (true) {
            try {
                if (page.imageUrl.isNullOrEmpty()) {
                    page.status = Page.State.LoadPage
                    page.imageUrl = source.getImageUrl(page)
                }
                val imageUrl = requireNotNull(page.imageUrl) { "Image URL is null after being fetched from source" }

                if (!chapterCache.isImageInCache(imageUrl)) {
                    page.status = Page.State.DownloadImage
                    chapterCache.fetchAndCacheImage(imageUrl) { source.getImage(page) }
                }

                page.stream = { chapterCache.getImageFile(imageUrl).inputStream() }
                page.status = Page.State.Ready
                return
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                val isTransient = when (e) {
                    is IOException -> true
                    is HttpException -> e.code == 429 || e.code >= 500
                    else -> false
                }
                if (isTransient && retries < MAX_PAGE_LOAD_RETRIES) {
                    retries++
                    delay(
                        (PAGE_LOAD_RETRY_DELAY_MS * (1L shl (retries - 1))).coerceAtMost(MAX_PAGE_LOAD_RETRY_DELAY_MS),
                    )
                } else {
                    page.status = Page.State.Error(e)
                    return
                }
            }
        }
    }

    companion object {
        /** Maximum number of automatic retry attempts for transient page-load failures. */
        private const val MAX_PAGE_LOAD_RETRIES = 3

        /** Initial delay in milliseconds before the first retry; doubles with each subsequent attempt. */
        private const val PAGE_LOAD_RETRY_DELAY_MS = 1_000L

        /** Maximum delay cap in milliseconds between retry attempts. */
        private const val MAX_PAGE_LOAD_RETRY_DELAY_MS = 8_000L

        /**
         * Priority assigned to pages queued by [preloadAllPages]. Set below the nearby-page
         * preload priority (0) so that background full-chapter downloads never steal bandwidth
         * from pages the user is actively reading or about to reach.
         */
        private const val BACKGROUND_PRELOAD_PRIORITY = -1
    }
}

/**
 * Data class used to keep ordering of pages in order to maintain priority.
 */
@OptIn(ExperimentalAtomicApi::class)
private class PriorityPage(
    val page: ReaderPage,
    val priority: Int,
) : Comparable<PriorityPage> {
    companion object {
        private val idGenerator = AtomicInt(0)
    }

    private val identifier = idGenerator.incrementAndFetch()

    override fun compareTo(other: PriorityPage): Int {
        val p = other.priority.compareTo(priority)
        return if (p != 0) p else identifier.compareTo(other.identifier)
    }
}
