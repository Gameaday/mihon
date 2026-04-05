package ephyra.feature.reader.loader

import ephyra.core.common.util.system.ImageUtil
import ephyra.core.common.util.system.logcat
import ephyra.domain.download.service.DownloadPreferences
import ephyra.feature.reader.model.ReaderPage
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import java.io.InputStream
import kotlin.math.abs

/**
 * Unified pre-processing pipeline for reader pages.
 *
 * Centralises all page-level filtering decisions (blocked-page detection, future
 * auto-detection, etc.) so that **every** page source — online, downloaded, local —
 * feeds through the same logic. The pipeline runs in two modes:
 *
 * * **Immediate** ([processLoadedPages]) — for pages whose image streams are already
 *   available (downloaded / local chapters). Called once in [ChapterLoader.loadChapter]
 *   after [PageLoader.getPages] returns. Uses an aspect-ratio pre-filter (matching the
 *   [Downloader][ephyra.app.data.download.Downloader] logic) to skip pages
 *   whose dimensions match the chapter's dominant aspect ratio, avoiding unnecessary
 *   dHash computation.
 *
 * * **Incremental** ([checkPageOnLoad]) — for pages whose images arrive asynchronously
 *   (online chapters via [HttpPageLoader]). Called each time a page image is fetched.
 *   If the page is blocked, [HttpPageLoader] skips setting its status to
 *   [Page.State.Ready][eu.kanade.tachiyomi.source.model.Page.State.Ready] so the holder
 *   never renders it.
 *
 * Both modes set [ReaderPage.isBlockedByFilter] on matching pages; the viewer adapters
 * exclude pages where [ReaderPage.isHidden] is `true`.
 */
class ReaderPagePreProcessor(
    private val downloadPreferences: DownloadPreferences,
) {

    /**
     * Lazily resolved and cached blocked dHash values. Parsed once from the preference
     * set and reused for every page check during this pre-processor's lifetime (which
     * matches the reader session). `null` when the blocklist is empty.
     */
    private val cachedBlockedDHashes: List<Long>? by lazy { resolveBlockedDHashes() }

    // ── Immediate processing (downloaded / local) ───────────────────────

    /**
     * Scans boundary pages that already have image streams and marks any that match
     * the blocklist. Called once after a chapter's page list is obtained.
     *
     * Uses an aspect-ratio pre-filter before the expensive dHash comparison: the
     * dominant (median) aspect ratio is computed from header-only dimension reads on
     * all pages, and candidate pages whose ratio closely matches the dominant are
     * assumed to be real content and skipped. Credit pages typically have a visually
     * distinct aspect ratio (e.g. a landscape banner in a portrait manga).
     */
    suspend fun processLoadedPages(pages: List<ReaderPage>) {
        val blockedDHashes = cachedBlockedDHashes ?: return

        val candidates = getBoundaryCandidates(pages)
        // Only consider candidates that already have image streams (downloaded / local).
        val streamCandidates = candidates.filter { it.stream != null }
        if (streamCandidates.isEmpty()) return

        // ── Aspect-ratio pre-filter (mirrors Downloader.filterBlockedPagesImpl) ──
        // Compute per-page aspect ratios via cheap header-only dimension reads and
        // determine the chapter's dominant (median) ratio.
        val pageRatios = HashMap<Int, Float>(streamCandidates.size)
        val allRatios = mutableListOf<Float>()
        for (page in pages) {
            val streamFn = page.stream ?: continue
            try {
                val dims = streamFn().use { ImageUtil.getImageDimensions(it) } ?: continue
                if (dims.second > 0) {
                    val ar = dims.first.toFloat() / dims.second
                    pageRatios[page.index] = ar
                    allRatios.add(ar)
                }
            } catch (_: Exception) {
                /* skip */
            }
        }
        val dominantAR = if (allRatios.isNotEmpty()) {
            allRatios.sort()
            allRatios[allRatios.size / 2]
        } else {
            null
        }

        var blockedCount = 0
        for (page in streamCandidates) {
            val streamFn = page.stream ?: continue
            // Skip pages matching the dominant aspect ratio within tolerance —
            // they are almost certainly real content, not credit pages.
            if (dominantAR != null) {
                val pageAR = pageRatios[page.index]
                if (pageAR != null && abs(pageAR - dominantAR) / dominantAR <= ASPECT_RATIO_TOLERANCE) {
                    continue
                }
            }

            if (checkAndFilter(streamFn, blockedDHashes)) {
                page.isBlockedByFilter = true
                blockedCount++
                logcat(LogPriority.DEBUG) { "Pre-processor: blocked page ${page.index} (immediate)" }
            }
        }
        if (blockedCount > 0) {
            logcat(LogPriority.DEBUG) {
                "Pre-processor: finished immediate scan — $blockedCount of ${streamCandidates.size} candidate pages blocked"
            }
        }
    }

    // ── Incremental processing (online) ─────────────────────────────────

    /**
     * Checks a single page after its image becomes available during online loading.
     *
     * @return `true` if the page was blocked (caller should skip setting status to Ready
     *         and notify the viewer to refresh).
     */
    fun checkPageOnLoad(page: ReaderPage, totalPages: Int): Boolean {
        if (!isBoundaryPage(page.index, totalPages)) return false

        val blockedDHashes = cachedBlockedDHashes ?: return false
        val streamFn = page.stream ?: return false

        if (checkAndFilter(streamFn, blockedDHashes)) {
            page.isBlockedByFilter = true
            logcat(LogPriority.DEBUG) { "Pre-processor: blocked page ${page.index} (on-load)" }
            return true
        }
        return false
    }

    // ── Internals ───────────────────────────────────────────────────────

    /**
     * Parses the preference set into dHash [Long] values. Returns `null` if the
     * blocklist is empty or contains no valid entries (common fast-path).
     */
    private fun resolveBlockedDHashes(): List<Long>? {
        val hexSet = runBlocking { downloadPreferences.blockedPageHashes().get() }
        if (hexSet.isEmpty()) return null
        val hashes = hexSet.mapNotNull { hex ->
            try {
                ImageUtil.hexToDHash(hex)
            } catch (_: Exception) {
                null
            }
        }
        return hashes.ifEmpty { null }
    }

    private fun isBoundaryPage(index: Int, totalPages: Int): Boolean {
        return index < BOUNDARY_PAGES || index >= totalPages - BOUNDARY_PAGES
    }

    private fun getBoundaryCandidates(pages: List<ReaderPage>): List<ReaderPage> {
        if (pages.size <= BOUNDARY_PAGES * 2) return pages
        return pages.take(BOUNDARY_PAGES) + pages.takeLast(BOUNDARY_PAGES)
    }

    /**
     * Computes the dHash of the image produced by [streamFn] and checks it against
     * [blockedDHashes]. Returns `true` if the page matches a blocked entry.
     */
    private fun checkAndFilter(
        streamFn: () -> InputStream,
        blockedDHashes: List<Long>,
    ): Boolean {
        val threshold = DownloadPreferences.BLOCKED_PAGE_DHASH_THRESHOLD
        val hash = try {
            streamFn().use { ImageUtil.computeDHash(it) }
        } catch (_: Exception) {
            return false
        } ?: return false

        return blockedDHashes.any { blocked ->
            ImageUtil.dHashDistance(hash, blocked) <= threshold
        }
    }

    companion object {
        /** Number of pages at each chapter boundary to evaluate. */
        private const val BOUNDARY_PAGES = 3

        /**
         * Aspect-ratio tolerance for the pre-filter. Pages whose aspect ratio is
         * within this fraction of the dominant ratio are assumed to be real content.
         * Matches the value used by [Downloader][ephyra.app.data.download.Downloader].
         */
        private const val ASPECT_RATIO_TOLERANCE = 0.05f
    }
}
