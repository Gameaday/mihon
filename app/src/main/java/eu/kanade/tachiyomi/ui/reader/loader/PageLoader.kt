package eu.kanade.tachiyomi.ui.reader.loader

import androidx.annotation.CallSuper
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/**
 * A loader used to load pages into the reader. Any open resources must be cleaned up when the
 * method [recycle] is called.
 */
abstract class PageLoader {

    /**
     * Whether this loader has been already recycled.
     */
    @Volatile
    var isRecycled = false
        private set

    abstract var isLocal: Boolean

    /**
     * Returns the list of pages of a chapter.
     */
    abstract suspend fun getPages(): List<ReaderPage>

    /**
     * Loads the page. May also preload other pages.
     * Progress of the page loading should be followed via [page.statusFlow].
     * [loadPage] is not currently guaranteed to complete, so it should be launched asynchronously.
     */
    open suspend fun loadPage(page: ReaderPage) {}

    /**
     * Retries the given [page] in case it failed to load. This method only makes sense when an
     * online source is used.
     */
    open fun retryPage(page: ReaderPage) {}

    /**
     * Proactively starts loading the first [amount] pages of this chapter so that their images
     * are cached before the user scrolls to them. Called after the chapter's page list has been
     * fetched but before the user reaches the chapter boundary. Implementations should enqueue
     * pages at background (lowest) priority; the default no-op is appropriate for local loaders
     * whose pages are already ready on disk.
     *
     * @param amount the number of pages from the beginning of the chapter to preload.
     */
    open fun preloadFirstPages(amount: Int) {}

    /**
     * Proactively queues every page in this chapter for download at the lowest background
     * priority so that the smart-combine pre-scan can process the entire chapter without
     * waiting for the user to scroll to each page. Pages already downloading or cached are
     * skipped. The default no-op is appropriate for local loaders whose pages are already
     * ready on disk.
     */
    open fun preloadAllPages() {}

    /**
     * Promotes this loader to full worker concurrency when it was initially created in a
     * bandwidth-throttled preload-only mode.
     *
     * When a chapter transitions from speculative preload to the active reading chapter the
     * underlying [HttpPageLoader] needs additional download workers so it can keep pace with
     * the reader. The base implementation is a no-op; [HttpPageLoader] overrides it to spawn
     * the remaining workers up to the device-tier maximum.
     *
     * Implementations must be idempotent — calling this method more than once must not spawn
     * duplicate workers.
     */
    open fun promoteToActive() {}

    /**
     * Recycles this loader. Implementations must override this method to clean up any active
     * resources.
     */
    @CallSuper
    open fun recycle() {
        isRecycled = true
    }
}
