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
}
