package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import androidx.core.view.isVisible
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

/**
 * View of the ViewPager that contains a page of a chapter.
 */
@SuppressLint("ViewConstructor")
class PagerPageHolder(
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val page: ReaderPage,
) : ReaderPageImageView(readerThemedContext), ViewPagerAdapter.PositionableView {

    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item
        get() = page

    /**
     * Loading progress bar to indicate the current progress.
     */
    private var progressIndicator: ReaderProgressIndicator? = null // = ReaderProgressIndicator(readerThemedContext)

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorBinding? = null

    private val scope = MainScope()

    /**
     * Job for loading the page and processing changes to the page's status.
     */
    private var loadJob: Job? = null

    /**
     * Job that watches the next page's status and re-triggers [setImage] once it loads,
     * allowing smart combine to fire even when page N+1 wasn't ready at display time.
     */
    private var smartCombineRetryJob: Job? = null

    /**
     * Dimensions of the most recently displayed image, cached so that [setupSmartCombineRetry]
     * can perform a stub check without re-buffering the current page's stream.
     */
    private var cachedPageWidth = 0
    private var cachedPageHeight = 0

    init {
        loadJob = scope.launch { loadPageAndProcessStatus() }
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loadJob?.cancel()
        loadJob = null
        smartCombineRetryJob?.cancel()
        smartCombineRetryJob = null
        scope.cancel()
    }

    private fun initProgressIndicator() {
        if (progressIndicator == null) {
            progressIndicator = ReaderProgressIndicator(context)
            addView(progressIndicator)
        }
    }

    /**
     * Loads the page and processes changes to the page's status.
     *
     * Returns immediately if the page has no PageLoader.
     * Otherwise, this function does not return. It will continue to process status changes until
     * the Job is cancelled.
     */
    private suspend fun loadPageAndProcessStatus() {
        val loader = page.chapter.pageLoader ?: return

        supervisorScope {
            launchIO {
                loader.loadPage(page)
            }
            page.statusFlow.collectLatest { state ->
                when (state) {
                    Page.State.Queue -> setQueued()
                    Page.State.LoadPage -> setLoading()
                    Page.State.DownloadImage -> {
                        setDownloading()
                        page.progressFlow.collectLatest { value ->
                            progressIndicator?.setProgress(value)
                        }
                    }
                    Page.State.Ready -> setImage()
                    is Page.State.Error -> setError(state.error)
                }
            }
        }
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        // mergedBytes is intentionally NOT cleared here. If this page was previously merged
        // with a stub, the merge result is still valid even after a re-queue (e.g. when the
        // chapter-cache evicts the original image). setImage() will take the instant fast path
        // using the cached bytes, so the user never sees a loading indicator or a brief
        // unmerged image while the original re-downloads.
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        initProgressIndicator()
        progressIndicator?.show()
        removeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        progressIndicator?.setProgress(0)

        // Fast path: if this page was already merged with its stub, use the cached bytes
        // directly and skip re-opening the original streams entirely. This makes scrubbing
        // back to a merged page instant with zero extra I/O or network calls.
        // streamFn is only needed when there is no cache; capturing it before the suspend
        // point guards against the stream reference being cleared on another thread.
        val cachedMerge = page.mergedBytes
        val streamFn = if (cachedMerge == null) page.stream ?: return else null

        try {
            val (source, isAnimated, background) = withIOContext {
                val source = if (cachedMerge != null) {
                    // Write the cached byte array directly into a Buffer —
                    // no intermediate InputStream or stream copy needed.
                    Buffer().write(cachedMerge)
                } else {
                    checkNotNull(streamFn)().use { process(item, Buffer().readFrom(it)) }
                }
                // Cache displayed image dimensions so setupSmartCombineRetry can perform
                // a header-only stub check without re-buffering the stream.
                val dimOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(source.peek().inputStream(), null, dimOpts)
                cachedPageWidth = dimOpts.outWidth
                cachedPageHeight = dimOpts.outHeight
                val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                val background = if (!isAnimated && viewer.config.automaticBackground) {
                    ImageUtil.chooseBackground(context, source.peek().inputStream())
                } else {
                    null
                }
                Triple(source, isAnimated, background)
            }
            withUIContext {
                setImage(
                    source,
                    isAnimated,
                    Config(
                        zoomDuration = viewer.config.doubleTapAnimDuration,
                        minimumScaleType = viewer.config.imageScaleType,
                        cropBorders = viewer.config.imageCropBorders,
                        zoomStartPosition = viewer.config.imageZoomType,
                        landscapeZoom = viewer.config.landscapeZoom,
                    ),
                )
                if (!isAnimated) {
                    pageBackground = background
                }
                removeErrorLayout()
            }
            // If smart combine was enabled but the next page wasn't ready yet, watch for it
            // and re-render once it arrives so the merge still fires automatically.
            setupSmartCombineRetry()
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
            withUIContext {
                setError(e)
            }
        }
    }

    /**
     * If [ViewerConfig.smartCombine] is on and the neighbouring stub page has not yet
     * finished loading, launches a coroutine that suspends until it does and then
     * re-triggers [setImage] so the merge fires automatically without requiring a
     * manual page flip.
     */
    private fun setupSmartCombineRetry() {
        smartCombineRetryJob?.cancel()
        if (!viewer.config.smartCombine || page is InsertPage) return
        // Already merged while the page was loading — no retry needed.
        if (page.mergedBytes != null) return
        val nextPage = page.chapter.pages?.getOrNull(page.index + 1) ?: return
        // If the next page was already ready, trySmartCombine already had a chance to handle it.
        if (nextPage.status == Page.State.Ready) return
        smartCombineRetryJob = scope.launch {
            // Wait for the stub candidate to finish loading.
            nextPage.statusFlow.filter { it == Page.State.Ready }.first()
            // isAttachedToWindow() returns true while this view is part of the window's
            // view hierarchy, i.e. the page holder is still on screen.
            if (!isAttachedToWindow()) return@launch
            // The merge may have been completed by another path while we were waiting.
            if (page.mergedBytes != null) return@launch
            // Use the cached dimensions of the current page so no stream re-read is needed.
            val nextStreamFn = nextPage.stream ?: return@launch
            try {
                val isStub = nextStreamFn().use { ImageUtil.isSmallPage(it, cachedPageWidth, cachedPageHeight) }
                if (isStub) setImage()
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Smart combine retry stub check failed" }
            }
        }
    }

    private fun process(page: ReaderPage, imageSource: BufferedSource): BufferedSource {
        if (viewer.config.dualPageRotateToFit) {
            val degrees = if (viewer.config.dualPageRotateToFitInvert) -90f else 90f
            return ImageUtil.rotateDualPageIfWide(imageSource, degrees)
        }

        if (!viewer.config.dualPageSplit) {
            return trySmartCombine(page, imageSource) ?: imageSource
        }

        if (page is InsertPage) {
            return splitInHalf(imageSource)
        }

        val isDoublePage = ImageUtil.isWideImage(imageSource)
        if (!isDoublePage) {
            return trySmartCombine(page, imageSource) ?: imageSource
        }

        onPageSplit(page)

        return splitInHalf(imageSource)
    }

    private fun trySmartCombine(page: ReaderPage, imageSource: BufferedSource): BufferedSource? {
        if (!viewer.config.smartCombine || page is InsertPage) return null
        val nextPage = page.chapter.pages?.getOrNull(page.index + 1) ?: return null
        if (nextPage.status != Page.State.Ready) return null
        val nextStreamFn = nextPage.stream ?: return null

        // Read only the image headers of the next page to decide if it is a stub.
        // This avoids buffering the entire stream into memory for pages that are not stubs.
        if (!nextStreamFn().use { ImageUtil.isSmallPage(it, imageSource) }) return null

        // Stub confirmed. Reject animated current pages here (after the cheap dimension check)
        // so the animated-image decode only happens when a merge would actually proceed.
        if (ImageUtil.isAnimatedAndSupported(imageSource)) return null

        // Confirmed stub and non-animated: load the full next-page stream for bitmap decoding.
        val nextSource = nextStreamFn().use { Buffer().readFrom(it) }
        return try {
            val mergedBuffer = ImageUtil.mergePages(imageSource, nextSource)
            // Persist merged bytes on the page so every subsequent render (scrubbing back,
            // refreshAdapter, etc.) returns the cached result instantly — no re-merge,
            // no extra stream opens, no extra network or disk I/O.
            // readByteArray() reads and removes all bytes from mergedBuffer in one pass.
            val mergedBytes = mergedBuffer.readByteArray()
            page.mergedBytes = mergedBytes
            // Absorb the stub only after a successful merge so that a decode failure
            // does not silently remove a page from the reader.
            onPageAbsorb(nextPage)
            // Write the already-extracted byte array directly into a fresh Buffer —
            // no intermediate InputStream needed.
            Buffer().write(mergedBytes)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Smart combine merge failed; showing pages separately" }
            null
        }
    }

    private fun splitInHalf(imageSource: BufferedSource): BufferedSource {
        var side = when {
            viewer is L2RPagerViewer && page is InsertPage -> ImageUtil.Side.RIGHT
            viewer !is L2RPagerViewer && page is InsertPage -> ImageUtil.Side.LEFT
            viewer is L2RPagerViewer && page !is InsertPage -> ImageUtil.Side.LEFT
            viewer !is L2RPagerViewer && page !is InsertPage -> ImageUtil.Side.RIGHT
            else -> error("We should choose a side!")
        }

        if (viewer.config.dualPageInvert) {
            side = when (side) {
                ImageUtil.Side.RIGHT -> ImageUtil.Side.LEFT
                ImageUtil.Side.LEFT -> ImageUtil.Side.RIGHT
            }
        }

        return ImageUtil.splitInHalf(imageSource, side)
    }

    private fun onPageSplit(page: ReaderPage) {
        val newPage = InsertPage(page)
        viewer.onPageSplit(page, newPage)
    }

    private fun onPageAbsorb(page: ReaderPage) {
        viewer.onPageAbsorb(page)
    }

    /**
     * Called when the page has an error.
     */
    private fun setError(error: Throwable?) {
        progressIndicator?.hide()
        showErrorLayout(error)
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        progressIndicator?.hide()
    }

    /**
     * Called when an image fails to decode.
     */
    override fun onImageLoadError(error: Throwable?) {
        super.onImageLoadError(error)
        setError(error)
    }

    /**
     * Called when an image is zoomed in/out.
     */
    override fun onScaleChanged(newScale: Float) {
        super.onScaleChanged(newScale)
        viewer.activity.hideMenu()
    }

    private fun showErrorLayout(error: Throwable?): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), this, true)
            errorLayout?.actionRetry?.viewer = viewer
            errorLayout?.actionRetry?.setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
            }
        }

        val imageUrl = page.imageUrl
        errorLayout?.actionOpenInWebView?.isVisible = imageUrl != null
        if (imageUrl != null) {
            if (imageUrl.startsWith("http", true)) {
                errorLayout?.actionOpenInWebView?.viewer = viewer
                errorLayout?.actionOpenInWebView?.setOnClickListener {
                    val sourceId = viewer.activity.viewModel.manga?.source

                    val intent = WebViewActivity.newIntent(context, imageUrl, sourceId)
                    context.startActivity(intent)
                }
            }
        }

        errorLayout?.errorMessage?.text = with(context) { error?.formattedMessage }
            ?: context.stringResource(MR.strings.decode_image_error)

        errorLayout?.root?.isVisible = true
        return errorLayout!!
    }

    /**
     * Removes the decode error layout from the holder, if found.
     */
    private fun removeErrorLayout() {
        errorLayout?.root?.isVisible = false
        errorLayout = null
    }
}
