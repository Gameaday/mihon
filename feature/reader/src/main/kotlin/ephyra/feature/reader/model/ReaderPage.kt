package ephyra.feature.reader.model

import android.graphics.Bitmap
import eu.kanade.tachiyomi.source.model.Page
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    var stream: (() -> InputStream)? = null,
) : Page(index, url, imageUrl, null) {

    open lateinit var chapter: ReaderChapter

    /**
     * Cached [Bitmap] of a smart-combine merge with a following stub page.
     * Non-null once the merge has succeeded; subsequent renders pass this bitmap
     * directly to [SubsamplingScaleImageView] without any encoding or decoding.
     *
     * **Lifecycle:** cleared via [recycleMergedBitmap] when the page is retried
     * or when the owning [ReaderChapter] is disposed, to avoid retaining large
     * native allocations beyond their useful lifetime.
     */
    @Volatile
    var mergedBitmap: Bitmap? = null

    /**
     * Recycles the native backing memory of [mergedBitmap] (if any) and sets the
     * reference to `null` so that the next render triggers a fresh merge.
     */
    fun recycleMergedBitmap() {
        mergedBitmap?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
            mergedBitmap = null
        }
    }

    /**
     * True once this page has been absorbed by the previous page as a stub during smart combine.
     * Absorbed pages are removed from the adapter's item list but remain in [chapter.pages].
     * The flag is used by the ViewModel to determine the effective last page of a chapter so
     * that read-marking still fires when the last pages are a merged pair.
     */
    @Volatile
    var isAbsorbed: Boolean = false

    /**
     * True when this page matched a blocked perceptual-hash entry during pre-processing.
     * Set by [ephyra.app.ui.reader.loader.ReaderPagePreProcessor] for both
     * downloaded and online pages.
     */
    @Volatile
    var isBlockedByFilter: Boolean = false

    /**
     * Convenience flag: `true` when the page should be hidden from the viewer, either
     * because it was absorbed by smart-combine or blocked by the page filter.
     * Adapters, page counts, and navigation all use this to determine visible pages.
     */
    val isHidden: Boolean get() = isAbsorbed || isBlockedByFilter
}
