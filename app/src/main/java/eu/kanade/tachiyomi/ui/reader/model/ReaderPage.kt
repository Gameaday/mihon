package eu.kanade.tachiyomi.ui.reader.model

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
     * Cached bytes of a smart-combine merge with a following stub page.
     * Non-null once the merge has succeeded; subsequent renders write these bytes
     * directly into a [okio.Buffer] without opening any intermediate stream.
     * Cleared to null when the page is retried so a fresh load starts clean.
     */
    @Volatile
    var mergedBytes: ByteArray? = null

    /**
     * True once this page has been absorbed by the previous page as a stub during smart combine.
     * Absorbed pages are removed from the adapter's item list but remain in [chapter.pages].
     * The flag is used by the ViewModel to determine the effective last page of a chapter so
     * that read-marking still fires when the last pages are a merged pair.
     */
    @Volatile
    var isAbsorbed: Boolean = false
}
